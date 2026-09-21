package org.dromara.certmuse.assessment.service.impl;

import cn.hutool.core.util.IdUtil;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.ai.client.AiGenerationHandle;
import org.dromara.certmuse.ai.client.AiModelClient;
import org.dromara.certmuse.ai.client.AiModelStreamListener;
import org.dromara.certmuse.ai.config.CertMuseAiProperties;
import org.dromara.certmuse.assessment.domain.PracticeAiConversationRow;
import org.dromara.certmuse.assessment.domain.PracticeAiIdempotencyRow;
import org.dromara.certmuse.assessment.domain.PracticeAiMessageRow;
import org.dromara.certmuse.assessment.domain.PracticeAiQuestionContextRow;
import org.dromara.certmuse.assessment.domain.bo.SendPracticeAiMessageBo;
import org.dromara.certmuse.assessment.domain.vo.CancelPracticeAiMessageVo;
import org.dromara.certmuse.assessment.support.AssessmentJsonSchema;
import org.dromara.certmuse.assessment.domain.vo.CreatePracticeAiConversationVo;
import org.dromara.certmuse.assessment.domain.vo.PracticeAiConversationVo;
import org.dromara.certmuse.assessment.domain.vo.PracticeAiErrorVo;
import org.dromara.certmuse.assessment.domain.vo.PracticeAiMessagePageVo;
import org.dromara.certmuse.assessment.domain.vo.PracticeAiMessageVo;
import org.dromara.certmuse.assessment.mapper.PracticeAiChatMapper;
import org.dromara.certmuse.assessment.service.PracticeAiChatService;
import org.dromara.certmuse.assessment.support.PracticeAiContentSafetyPolicy;
import org.dromara.certmuse.assessment.support.PracticeAiContext;
import org.dromara.certmuse.assessment.support.PracticeAiContextAssembler;
import org.dromara.certmuse.assessment.support.PracticeAiException;
import org.dromara.certmuse.assessment.support.PracticeAiQuota;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/** Coordinates persistent question AI conversations and provider streams. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PracticeAiChatServiceImpl implements PracticeAiChatService {
    private static final String CREATE_ACTION = "CREATE_PRACTICE_AI_CONVERSATION";
    private static final String CANCEL_ACTION = "CANCEL_PRACTICE_AI_GENERATION";
    private static final int MESSAGE_LIMIT = 200;

    private final PracticeAiChatMapper mapper;
    private final CertMuseAiProperties properties;
    private final PracticeAiContextAssembler contextAssembler;
    private final PracticeAiContentSafetyPolicy contentSafety;
    private final PracticeAiQuota quota;
    private final AiModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService practiceAiScheduler;
    private final Map<Long, ActiveGeneration> active = new ConcurrentHashMap<>();

    @Override
    public CreatePracticeAiConversationVo create(long userId, long sessionId, int questionOrder, String requestId) {
        requireEnabled();
        PracticeAiQuestionContextRow context = mapper.selectQuestionContext(sessionId, userId, questionOrder);
        if (context == null) {
            throw new PracticeAiException(404, "PRACTICE_SESSION_NOT_FOUND", "练习会话不存在或无权访问", false);
        }
        String requestHash = hash(userId + ":" + sessionId + ":" + questionOrder);
        PracticeAiIdempotencyRow existingAction = mapper.selectIdempotency(CREATE_ACTION, requestId);
        if (existingAction != null) return replayCreate(existingAction, requestHash, userId, sessionId, questionOrder);

        return transactionTemplate.execute(status -> {
            long actionId = IdUtil.getSnowflakeNextId();
            if (mapper.insertIdempotency(actionId, CREATE_ACTION, requestId, requestHash) == 0) {
                PracticeAiIdempotencyRow concurrent = mapper.selectIdempotency(CREATE_ACTION, requestId);
                if (concurrent == null) throw conflict();
                return replayCreate(concurrent, requestHash, userId, sessionId, questionOrder);
            }
            long conversationId = IdUtil.getSnowflakeNextId();
            boolean created = mapper.insertConversation(conversationId, userId, sessionId, questionOrder,
                "openai-compatible", properties.getModel()) == 1;
            PracticeAiConversationRow row = mapper.selectConversationByItem(userId, sessionId, questionOrder);
            if (row == null) throw systemFailure("AI会话创建失败", null);
            mapper.succeedIdempotency(actionId, "cm_ai_conversation", row.getId(), json(Map.of(
                "schema_version", AssessmentJsonSchema.PRACTICE_AI_CREATE_RESPONSE.version(),
                "conversationId", String.valueOf(row.getId()))));
            return new CreatePracticeAiConversationVo(created, conversationVo(row, context));
        });
    }

    @Override
    public PracticeAiConversationVo conversation(long userId, long conversationId) {
        PracticeAiConversationRow row = ownedConversation(userId, conversationId);
        return conversationVo(row, questionContext(row, userId));
    }

    @Override
    public PracticeAiMessagePageVo messages(long userId, long conversationId, Integer beforeSequence, int limit) {
        ownedConversation(userId, conversationId);
        List<PracticeAiMessageRow> rows = new ArrayList<>(mapper.selectMessages(conversationId, beforeSequence, limit + 1));
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows.removeFirst();
        List<PracticeAiMessageVo> items = rows.stream().map(this::messageVo).toList();
        Integer next = hasMore && !rows.isEmpty() ? rows.getFirst().getSequenceNo() : null;
        return new PracticeAiMessagePageVo(items, hasMore, next);
    }

    @Override
    public SseEmitter stream(long userId, long conversationId, String requestId, SendPracticeAiMessageBo command) {
        requireEnabled();
        String message = command.message().trim();
        if (message.isEmpty() || message.codePointCount(0, message.length()) > 2000) {
            throw invalidField("message", "OUT_OF_RANGE", "消息长度必须为1至2000个字符");
        }
        if (!contentSafety.acceptsInput(message)) {
            throw new PracticeAiException(403, "AI_CHAT_CONTENT_REJECTED", "消息未通过内容安全检查", false);
        }
        PracticeAiConversationRow conversation = ownedConversation(userId, conversationId);
        PracticeAiQuestionContextRow question = questionContext(conversation, userId);
        if (mapper.countMessages(conversationId) >= MESSAGE_LIMIT) {
            throw new PracticeAiException(409, "AI_CHAT_MESSAGE_LIMIT_REACHED", "当前题目对话已达上限", false);
        }
        PracticeAiMessageRow duplicate = mapper.selectUserMessageByClientId(command.clientMessageId());
        if (duplicate != null) throw duplicateMessage(duplicate, conversationId, message);

        String mode = question.getAttemptId() == null ? "GUIDANCE_ONLY" : "FULL_EXPLANATION";
        List<PracticeAiMessageRow> history = mapper.selectRecentMessages(conversationId, 20);
        PracticeAiContext modelContext = contextAssembler.assemble(question, mode,
            List.copyOf(command.currentSelection()), history, message);
        long userMessageId = IdUtil.getSnowflakeNextId();
        long assistantMessageId = IdUtil.getSnowflakeNextId();
        if (!quota.acquireSlot(userId, assistantMessageId)) {
            throw limited(409, "AI_CHAT_GENERATION_IN_PROGRESS", "CONCURRENT_GENERATION", 1);
        }
        try {
            if (!quota.acquireMinute(userId)) throw limited(429, "AI_CHAT_RATE_LIMITED", "USER_MINUTE", 60);
            if (!quota.acquireDay(userId)) {
                throw limited(429, "AI_CHAT_RATE_LIMITED", "USER_DAY", quota.secondsUntilNextDay());
            }
            transactionTemplate.executeWithoutResult(status -> prepareMessages(
                userId, conversationId, userMessageId, assistantMessageId, message,
                command.clientMessageId(), mode));
        } catch (RuntimeException exception) {
            quota.releaseSlot(userId, assistantMessageId);
            throw exception;
        }
        return startStream(userId, conversationId, userMessageId, assistantMessageId, mode, modelContext);
    }

    @Override
    public CancelPracticeAiMessageVo cancel(long userId, long conversationId, long messageId, String requestId) {
        PracticeAiMessageRow message = mapper.selectOwnedAssistantMessage(conversationId, messageId, userId);
        if (message == null) {
            throw new PracticeAiException(404, "AI_MESSAGE_NOT_FOUND", "消息不存在或无权访问", false);
        }
        String payloadHash = hash(userId + ":" + conversationId + ":" + messageId);
        PracticeAiIdempotencyRow existing = mapper.selectIdempotency(CANCEL_ACTION, requestId);
        if (existing != null && !payloadHash.equals(existing.getPayloadHash())) throw conflict();
        if ("CANCELLED".equals(message.getStatus())) return new CancelPracticeAiMessageVo(String.valueOf(messageId), "CANCELLED");
        if (!"GENERATING".equals(message.getStatus())) {
            throw new PracticeAiException(409, "AI_MESSAGE_NOT_GENERATING", "消息已经结束生成", false);
        }
        transactionTemplate.executeWithoutResult(status -> {
            if (existing == null) {
                long actionId = IdUtil.getSnowflakeNextId();
                if (mapper.insertIdempotency(actionId, CANCEL_ACTION, requestId, payloadHash) == 0) throw conflict();
                mapper.cancelAssistantMessage(messageId);
                mapper.succeedIdempotency(actionId, "cm_ai_message", messageId,
                    json(Map.of(
                        "schema_version", AssessmentJsonSchema.PRACTICE_AI_CANCEL_RESPONSE.version(),
                        "assistantMessageId", String.valueOf(messageId),
                        "status", "CANCELLED")));
            } else {
                mapper.cancelAssistantMessage(messageId);
            }
        });
        cancelActive(userId, messageId, true);
        return new CancelPracticeAiMessageVo(String.valueOf(messageId), "CANCELLED");
    }

    @Scheduled(fixedDelay = 60_000L)
    public void cleanStaleGenerating() {
        mapper.selectStaleGenerating(120).forEach(row -> {
            if (mapper.failStaleGenerating(row.getId(), "AI_GENERATION_INTERRUPTED") == 1) {
                quota.releaseSlot(row.getOwnerUserId(), row.getId());
                cancelActive(row.getOwnerUserId(), row.getId(), false);
            }
        });
    }

    @PreDestroy
    public void stopActiveGenerations() {
        active.forEach((id, generation) -> generation.cancelProvider());
        active.clear();
    }

    private SseEmitter startStream(long userId, long conversationId, long userMessageId,
                                   long assistantMessageId, String mode, PracticeAiContext context) {
        SseEmitter emitter = new SseEmitter(properties.getTotalTimeout().plusSeconds(30).toMillis());
        ActiveGeneration generation = new ActiveGeneration(userId, assistantMessageId, emitter);
        active.put(assistantMessageId, generation);
        emitter.onTimeout(() -> fail(generation, "AI_PROVIDER_TIMEOUT", true, null));
        emitter.onError(error -> fail(generation, "AI_CHAT_SYSTEM_FAILURE", true, error));
        emitter.onCompletion(() -> {
            if (!generation.terminal.get()) cancelGeneration(generation, false);
        });
        generation.heartbeat = practiceAiScheduler.scheduleAtFixedRate(() -> heartbeat(generation),
            15, 15, TimeUnit.SECONDS);
        generation.firstTokenTimeout = practiceAiScheduler.schedule(() -> {
            if (!generation.firstToken.get()) fail(generation, "AI_PROVIDER_TIMEOUT", true, null);
        }, properties.getFirstTokenTimeout().toMillis(), TimeUnit.MILLISECONDS);
        send(generation, "message.start", Map.of(
            "conversationId", String.valueOf(conversationId),
            "userMessageId", String.valueOf(userMessageId),
            "assistantMessageId", String.valueOf(assistantMessageId),
            "answerDisclosureMode", mode));
        AiGenerationHandle handle = modelClient.stream(context.request(), new AiModelStreamListener() {
            @Override
            public void onDelta(String delta) {
                if (generation.terminal.get() || delta == null || delta.isEmpty()) return;
                generation.firstToken.set(true);
                String accepted = limitDelta(generation.content, delta);
                if (accepted.isEmpty()) {
                    complete(generation, "LENGTH");
                    return;
                }
                String candidate = generation.content + accepted;
                if (!contentSafety.acceptsOutput(candidate, mode, context.correctLabels(), context.analysis())) {
                    fail(generation, "AI_OUTPUT_REJECTED", false, null);
                    return;
                }
                generation.content.append(accepted);
                send(generation, "message.delta", Map.of(
                    "assistantMessageId", String.valueOf(assistantMessageId), "delta", accepted));
                if (contentLength(generation.content) >= properties.getMaxOutputCharacters()) {
                    complete(generation, "LENGTH");
                }
            }

            @Override
            public void onCompleted(String finishReason) {
                complete(generation, finishReason);
            }

            @Override
            public void onFailure(String errorCode, Throwable cause) {
                fail(generation, errorCode, true, cause);
            }
        });
        generation.handle = handle;
        if (generation.terminal.get()) handle.cancel();
        return emitter;
    }

    private void complete(ActiveGeneration generation, String finishReason) {
        if (!generation.terminal.compareAndSet(false, true)) return;
        mapper.completeAssistantMessage(generation.messageId, generation.content.toString());
        sendTerminal(generation, "message.completed", Map.of(
            "assistantMessageId", String.valueOf(generation.messageId),
            "status", "COMPLETED",
            "finishReason", finishReason == null ? "STOP" : finishReason,
            "contentLength", contentLength(generation.content),
            "completedAt", java.time.OffsetDateTime.now().toString()));
    }

    private void fail(ActiveGeneration generation, String errorCode, boolean retryable, Throwable cause) {
        if (!generation.terminal.compareAndSet(false, true)) return;
        mapper.failAssistantMessage(generation.messageId, errorCode);
        String traceId = UUID.randomUUID().toString();
        if (cause != null) {
            log.error("AI generation failed, messageId={}, errorCode={}, traceId={}",
                generation.messageId, errorCode, traceId, cause);
        } else {
            log.warn("AI generation failed, messageId={}, errorCode={}, traceId={}",
                generation.messageId, errorCode, traceId);
        }
        sendTerminal(generation, "message.failed", Map.of(
            "assistantMessageId", String.valueOf(generation.messageId),
            "status", "FAILED", "errorCode", errorCode, "retryable", retryable,
            "traceId", traceId, "partialContentDiscarded", true));
    }

    private void cancelGeneration(ActiveGeneration generation, boolean emit) {
        if (!generation.terminal.compareAndSet(false, true)) return;
        mapper.cancelAssistantMessage(generation.messageId);
        generation.cancelProvider();
        if (emit) sendTerminal(generation, "message.cancelled", Map.of(
            "assistantMessageId", String.valueOf(generation.messageId), "status", "CANCELLED",
            "completedAt", java.time.OffsetDateTime.now().toString()));
        else finish(generation);
    }

    private void cancelActive(long userId, long messageId, boolean emit) {
        ActiveGeneration generation = active.get(messageId);
        if (generation != null) cancelGeneration(generation, emit);
        else quota.releaseSlot(userId, messageId);
    }

    private void send(ActiveGeneration generation, String name, Object data) {
        if (generation.terminal.get() && !name.startsWith("message.")) return;
        try {
            generation.emitter.send(SseEmitter.event()
                .id(String.valueOf(generation.eventSequence.incrementAndGet())).name(name).data(data));
        } catch (Exception exception) {
            cancelGeneration(generation, false);
        }
    }

    private void heartbeat(ActiveGeneration generation) {
        if (generation.terminal.get()) return;
        try {
            generation.emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (Exception exception) {
            cancelGeneration(generation, false);
        }
    }

    private void sendTerminal(ActiveGeneration generation, String name, Object data) {
        try {
            generation.emitter.send(SseEmitter.event()
                .id(String.valueOf(generation.eventSequence.incrementAndGet())).name(name).data(data));
            generation.emitter.complete();
        } catch (Exception ignored) {
            generation.emitter.completeWithError(ignored);
        } finally {
            finish(generation);
        }
    }

    private void finish(ActiveGeneration generation) {
        if (generation.heartbeat != null) generation.heartbeat.cancel(false);
        if (generation.firstTokenTimeout != null) generation.firstTokenTimeout.cancel(false);
        quota.releaseSlot(generation.userId, generation.messageId);
        active.remove(generation.messageId, generation);
    }

    private void prepareMessages(long userId, long conversationId, long userMessageId, long assistantMessageId,
                                 String message, String clientMessageId, String mode) {
        PracticeAiConversationRow locked = mapper.lockOwnedConversation(conversationId, userId);
        if (locked == null) throw notFound();
        int count = mapper.countMessages(conversationId);
        if (count > MESSAGE_LIMIT - 2) {
            throw new PracticeAiException(409, "AI_CHAT_MESSAGE_LIMIT_REACHED", "当前题目对话已达上限", false);
        }
        if (mapper.insertUserMessage(userMessageId, conversationId, count + 1, message, clientMessageId) != 1
            || mapper.insertAssistantMessage(assistantMessageId, conversationId, userMessageId, count + 2, mode) != 1) {
            throw conflict();
        }
    }

    private PracticeAiConversationRow ownedConversation(long userId, long conversationId) {
        PracticeAiConversationRow row = mapper.selectOwnedConversation(conversationId, userId);
        if (row == null) throw notFound();
        return row;
    }

    private PracticeAiQuestionContextRow questionContext(PracticeAiConversationRow conversation, long userId) {
        PracticeAiQuestionContextRow context = mapper.selectQuestionContext(
            conversation.getPracticeSessionId(), userId, conversation.getQuestionOrder());
        if (context == null) throw notFound();
        return context;
    }

    private PracticeAiConversationVo conversationVo(PracticeAiConversationRow row, PracticeAiQuestionContextRow context) {
        return new PracticeAiConversationVo(String.valueOf(row.getId()), String.valueOf(row.getPracticeSessionId()),
            row.getQuestionOrder(), row.getStatus(), context.getAttemptId() == null ? "GUIDANCE_ONLY" : "FULL_EXPLANATION",
            row.getGeneratingAssistantMessageId() == null ? null : String.valueOf(row.getGeneratingAssistantMessageId()),
            row.getCreateTime(), row.getUpdateTime());
    }

    private PracticeAiMessageVo messageVo(PracticeAiMessageRow row) {
        return new PracticeAiMessageVo(String.valueOf(row.getId()), row.getSequenceNo(), row.getRole(), row.getContent(),
            row.getStatus(), row.getAnswerDisclosureMode(), row.getErrorCode(), row.getCreateTime(), row.getCompletedTime());
    }

    private CreatePracticeAiConversationVo replayCreate(PracticeAiIdempotencyRow action, String requestHash,
                                                         long userId, long sessionId, int questionOrder) {
        if (!requestHash.equals(action.getPayloadHash())) throw conflict();
        PracticeAiConversationRow row = mapper.selectConversationByItem(userId, sessionId, questionOrder);
        if (row == null) throw conflict();
        return new CreatePracticeAiConversationVo(false, conversationVo(row, questionContext(row, userId)));
    }

    private PracticeAiException duplicateMessage(PracticeAiMessageRow duplicate, long conversationId, String content) {
        if (duplicate.getConversationId() != conversationId || !duplicate.getContent().equals(content)) throw conflict();
        PracticeAiMessageRow assistant = mapper.selectAssistantReply(duplicate.getId());
        String code = assistant != null && "GENERATING".equals(assistant.getStatus())
            ? "AI_CHAT_GENERATION_IN_PROGRESS" : "AI_CHAT_GENERATION_ALREADY_RECORDED";
        return new PracticeAiException(409, code, "该消息已经受理", "AI_CHAT_GENERATION_IN_PROGRESS".equals(code),
            List.of(), new PracticeAiErrorVo.DetailsVo(String.valueOf(conversationId),
            assistant == null ? null : String.valueOf(assistant.getId()), null, "CONCURRENT_GENERATION"), null);
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || blank(properties.getBaseUrl()) || blank(properties.getApiKey())
            || blank(properties.getModel())) {
            throw new PracticeAiException(503, "AI_CHAT_DISABLED", "AI助教暂未启用", true);
        }
    }

    private PracticeAiException limited(int status, String code, String type, int seconds) {
        return new PracticeAiException(status, code, "AI对话请求过于频繁，请稍后再试", true, List.of(),
            new PracticeAiErrorVo.DetailsVo(null, null, seconds, type), null);
    }

    private PracticeAiException invalidField(String field, String code, String message) {
        return new PracticeAiException(400, "AI_CHAT_REQUEST_INVALID", "请求参数不正确", false,
            List.of(new PracticeAiErrorVo.FieldErrorVo(field, code, message)), null, null);
    }

    private PracticeAiException notFound() {
        return new PracticeAiException(404, "AI_CONVERSATION_NOT_FOUND", "AI对话不存在或无权访问", false);
    }

    private PracticeAiException conflict() {
        return new PracticeAiException(409, "AI_CHAT_IDEMPOTENCY_CONFLICT", "请求幂等标识冲突", false);
    }

    private PracticeAiException systemFailure(String message, Throwable cause) {
        return new PracticeAiException(500, "AI_CHAT_SYSTEM_FAILURE", message, true, List.of(), null, cause);
    }

    private String limitDelta(StringBuilder current, String delta) {
        int currentCodePoints = contentLength(current);
        int remaining = properties.getMaxOutputCharacters() - currentCodePoints;
        int acceptedCodePoints = Math.min(Math.min(remaining, 1000), delta.codePointCount(0, delta.length()));
        return acceptedCodePoints <= 0 ? "" : delta.substring(0, delta.offsetByCodePoints(0, acceptedCodePoints));
    }

    private int contentLength(CharSequence content) {
        return Character.codePointCount(content, 0, content.length());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw systemFailure("请求校验失败", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw systemFailure("响应序列化失败", exception);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class ActiveGeneration {
        private final long userId;
        private final long messageId;
        private final SseEmitter emitter;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean firstToken = new AtomicBoolean();
        private final AtomicInteger eventSequence = new AtomicInteger();
        private final StringBuilder content = new StringBuilder();
        private volatile AiGenerationHandle handle;
        private volatile ScheduledFuture<?> heartbeat;
        private volatile ScheduledFuture<?> firstTokenTimeout;

        private ActiveGeneration(long userId, long messageId, SseEmitter emitter) {
            this.userId = userId;
            this.messageId = messageId;
            this.emitter = emitter;
        }

        private void cancelProvider() {
            if (handle != null) handle.cancel();
        }
    }
}
