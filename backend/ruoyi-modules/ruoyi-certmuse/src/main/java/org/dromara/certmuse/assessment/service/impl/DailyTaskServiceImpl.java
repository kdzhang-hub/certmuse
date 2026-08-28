package org.dromara.certmuse.assessment.service.impl;

import cn.hutool.core.util.IdUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeAnsweringSessionRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeIdempotencyRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeItemRow;
import org.dromara.certmuse.assessment.domain.bo.SubmitDailyTaskItemBo;
import org.dromara.certmuse.assessment.domain.vo.CompleteDailyTaskVo;
import org.dromara.certmuse.assessment.domain.vo.DailyTaskItemVo;
import org.dromara.certmuse.assessment.domain.vo.DailyTaskSessionVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeErrorVo;
import org.dromara.certmuse.assessment.domain.vo.SubmitDailyTaskItemVo;
import org.dromara.certmuse.assessment.mapper.DailyTaskMapper;
import org.dromara.certmuse.assessment.service.ChoiceAnswerSettlementService;
import org.dromara.certmuse.assessment.service.DailyTaskService;
import org.dromara.certmuse.assessment.support.AssessmentJsonSchema;
import org.dromara.certmuse.assessment.support.DailyTaskException;
import org.dromara.certmuse.learning.service.LearningTaskService;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Transactional U11 daily-task answering implementation. */
@Service
@RequiredArgsConstructor
public class DailyTaskServiceImpl implements DailyTaskService {
    private static final String SUBMIT = "SUBMIT_DAILY_TASK_ITEM";
    private static final String COMPLETE = "COMPLETE_DAILY_TASK";
    private final DailyTaskMapper mapper;
    private final JsonMapper jsonMapper;
    private final QuestionImageUrlService imageUrlService;
    private final ChoiceAnswerSettlementService settlementService;
    private final LearningTaskService learningTaskService;

    @Override
    @Transactional(readOnly = true)
    public DailyTaskSessionVo session(long userId, String rawSessionId) {
        long sessionId = parseId(rawSessionId, "sessionId");
        KnowledgePracticeAnsweringSessionRow row = mapper.selectSession(sessionId, userId);
        if (row == null) throw failure(404, "TASK_SESSION_NOT_FOUND", "每日任务会话不存在", false);
        String status = switch (row.getStatus()) {
            case "in_progress" -> "IN_PROGRESS";
            case "submitted", "settling" -> "ALL_SUBMITTED";
            case "completed" -> "COMPLETED";
            default -> throw failure(409, "TASK_SESSION_NOT_ACTIVE", "每日任务会话当前不可答题", false);
        };
        List<DailyTaskSessionVo.NavigationItemVo> navigation = "COMPLETED".equals(status) ? List.of()
            : mapper.selectNavigation(sessionId).stream().map(item -> new DailyTaskSessionVo.NavigationItemVo(
                item.getQuestionOrder(), !Boolean.TRUE.equals(item.getSubmitted()) ? "UNANSWERED"
                    : Boolean.TRUE.equals(item.getCorrect()) ? "SUBMITTED_CORRECT" : "SUBMITTED_INCORRECT")).toList();
        String nextAction = switch (status) {
            case "IN_PROGRESS" -> "CONTINUE_PRACTICE";
            case "ALL_SUBMITTED" -> "COMPLETE_TASK";
            default -> "RETURN_DAILY_TASKS";
        };
        return new DailyTaskSessionVo(String.valueOf(sessionId), status, row.getTaskTitle(), row.getTotalCount(),
            row.getSubmittedCount(), navigation, nextAction);
    }

    @Override
    @Transactional(readOnly = true)
    public DailyTaskItemVo item(long userId, String rawSessionId, int questionOrder) {
        long sessionId = parseId(rawSessionId, "sessionId");
        requireOrder(questionOrder);
        KnowledgePracticeAnsweringSessionRow session = mapper.selectSession(sessionId, userId);
        if (session == null) throw failure(404, "TASK_SESSION_NOT_FOUND", "每日任务会话不存在", false);
        if (!"in_progress".equals(session.getStatus())) {
            throw failure(409, "TASK_SESSION_NOT_ACTIVE", "每日任务会话当前不可答题", false);
        }
        KnowledgePracticeItemRow row = mapper.selectItem(sessionId, userId, questionOrder);
        if (row == null) throw failure(404, "TASK_ITEM_NOT_FOUND", "每日任务题目不存在", false);
        return itemVo(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitDailyTaskItemVo submit(long userId, String rawSessionId, int questionOrder, String rawRequestId,
                                        SubmitDailyTaskItemBo command) {
        long sessionId = parseId(rawSessionId, "sessionId");
        requireOrder(questionOrder);
        String requestId = normalizeRequestId(rawRequestId);
        String selected = singleAnswer(command);
        String payloadHash = hash(userId + ":" + sessionId + ":" + questionOrder + ":" + selected);
        KnowledgePracticeIdempotencyRow existing = mapper.selectIdempotency(SUBMIT, requestId);
        if (existing != null) return replay(existing, payloadHash, SubmitDailyTaskItemVo.class);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, SUBMIT, requestId, payloadHash) == 0) {
            existing = mapper.selectIdempotency(SUBMIT, requestId);
            if (existing == null) throw failure(409, "TASK_IDEMPOTENCY_CONFLICT", "请求号发生并发冲突", false);
            return replay(existing, payloadHash, SubmitDailyTaskItemVo.class);
        }
        KnowledgePracticeAnsweringSessionRow locked = mapper.lockSession(sessionId, userId);
        if (locked == null) throw failure(404, "TASK_SESSION_NOT_FOUND", "每日任务会话不存在", false);
        if (!"in_progress".equals(locked.getStatus())) {
            throw failure(409, "TASK_SESSION_NOT_ACTIVE", "每日任务会话当前不可答题", false);
        }
        KnowledgePracticeItemRow item = mapper.selectItem(sessionId, userId, questionOrder);
        if (item == null) throw failure(404, "TASK_ITEM_NOT_FOUND", "每日任务题目不存在", false);
        if (item.getAttemptId() != null) throw failure(409, "TASK_ITEM_ALREADY_SUBMITTED", "该题已提交", false);
        try {
            JsonNode presentation = jsonMapper.readTree(item.getPresentationSnapshot());
            JsonNode grading = jsonMapper.readTree(item.getGradingSnapshot());
            boolean valid = false;
            for (JsonNode option : presentation.path("options")) {
                if (selected.equals(option.path("label").asText())) { valid = true; break; }
            }
            if (!valid) throw failure(422, "TASK_ANSWER_INVALID", "所选答案不属于当前题目", false);
            List<String> correctLabels = texts(grading.path("answer").path("value"));
            if (correctLabels.size() != 1) throw new IllegalStateException("daily-task single answer snapshot is invalid");
            boolean correct = selected.equals(correctLabels.getFirst());
            long attemptId = IdUtil.getSnowflakeNextId();
            if (mapper.insertAttempt(attemptId, item.getSessionQuestionId(), userId, requestId) == 0) {
                throw failure(409, "TASK_ITEM_ALREADY_SUBMITTED", "该题已提交", false);
            }
            String answerData = VersionedJsonDocumentFactory.json(jsonMapper,
                AssessmentJsonSchema.KNOWLEDGE_PRACTICE_CHOICE_ANSWER,
                Map.of("answer_type", "option_keys", "selection_mode", "single", "value", List.of(selected)));
            mapper.insertAttemptAnswer(IdUtil.getSnowflakeNextId(), attemptId, answerData, correct);
            settlementService.settle(item, attemptId, correct, requestId);
            int submittedCount = mapper.countSubmittedItems(sessionId);
            if (submittedCount == item.getTotalCount() && mapper.submitSession(sessionId, userId) != 1) {
                throw new IllegalStateException("daily-task last-answer transition failed");
            }
            DailyTaskItemVo.SubmissionVo submission = new DailyTaskItemVo.SubmissionVo(List.of(selected), correct,
                correctLabels, grading.path("analysis").asText(null));
            SubmitDailyTaskItemVo result = new SubmitDailyTaskItemVo(questionOrder, submittedCount, submission);
            mapper.succeedIdempotency(idempotencyId, "cm_question_attempt", attemptId, response(result));
            return result;
        } catch (DailyTaskException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DailyTaskException(500, "TASK_OPERATION_FAILED", "每日任务答案提交失败", true, List.of(), exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompleteDailyTaskVo complete(long userId, String rawSessionId, String rawRequestId) {
        long sessionId = parseId(rawSessionId, "sessionId");
        String requestId = normalizeRequestId(rawRequestId);
        String payloadHash = hash(userId + ":" + sessionId);
        KnowledgePracticeIdempotencyRow existing = mapper.selectIdempotency(COMPLETE, requestId);
        if (existing != null) return replay(existing, payloadHash, CompleteDailyTaskVo.class);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, COMPLETE, requestId, payloadHash) == 0) {
            existing = mapper.selectIdempotency(COMPLETE, requestId);
            if (existing == null) throw failure(409, "TASK_IDEMPOTENCY_CONFLICT", "请求号发生并发冲突", false);
            return replay(existing, payloadHash, CompleteDailyTaskVo.class);
        }
        KnowledgePracticeAnsweringSessionRow locked = mapper.lockSession(sessionId, userId);
        if (locked == null) throw failure(404, "TASK_SESSION_NOT_FOUND", "每日任务会话不存在", false);
        if (!"submitted".equals(locked.getStatus())) {
            if ("in_progress".equals(locked.getStatus())) {
                throw failure(409, "TASK_NOT_ALL_SUBMITTED", "仍有题目未提交", false);
            }
            throw failure(409, "TASK_SESSION_NOT_ACTIVE", "每日任务会话当前不可完成", false);
        }
        KnowledgePracticeAnsweringSessionRow counts = mapper.selectSession(sessionId, userId);
        if (!counts.getTotalCount().equals(counts.getSubmittedCount())) {
            throw failure(409, "TASK_NOT_ALL_SUBMITTED", "仍有题目未提交", false);
        }
        if (mapper.settleSession(sessionId, userId) != 1) throw new IllegalStateException("settling transition failed");
        learningTaskService.completePracticeItem(userId, sessionId);
        if (mapper.completeSession(sessionId, userId) != 1) throw new IllegalStateException("completed transition failed");
        CompleteDailyTaskVo result = new CompleteDailyTaskVo(String.valueOf(sessionId), "COMPLETED",
            counts.getSubmittedCount(), "RETURN_DAILY_TASKS");
        mapper.succeedIdempotency(idempotencyId, "cm_learning_session", sessionId, response(result));
        return result;
    }

    private DailyTaskItemVo itemVo(KnowledgePracticeItemRow row) {
        try {
            JsonNode presentation = jsonMapper.readTree(row.getPresentationSnapshot());
            JsonNode grading = jsonMapper.readTree(row.getGradingSnapshot());
            List<DailyTaskItemVo.OptionVo> options = new ArrayList<>();
            presentation.path("options").forEach(o -> options.add(new DailyTaskItemVo.OptionVo(
                o.path("label").asText(), o.path("content").asText())));
            List<DailyTaskItemVo.ImageVo> images = new ArrayList<>();
            for (JsonNode image : presentation.path("images")) {
                images.add(new DailyTaskItemVo.ImageVo(imageUrlService.accessUrl(image.path("sourceUrl").asText(null),
                    image.path("storagePath").asText(null)), image.path("altText").asText(null),
                    image.path("sortOrder").asInt()));
            }
            DailyTaskItemVo.SubmissionVo submission = null;
            if (row.getAttemptId() != null) {
                JsonNode answer = jsonMapper.readTree(row.getAnswerData());
                submission = new DailyTaskItemVo.SubmissionVo(texts(answer.path("value")),
                    Boolean.TRUE.equals(row.getCorrect()), texts(grading.path("answer").path("value")),
                    grading.path("analysis").asText(null));
            }
            return new DailyTaskItemVo(String.valueOf(row.getSessionId()), row.getQuestionOrder(), row.getTotalCount(),
                new DailyTaskItemVo.QuestionVo("CHOICE", presentation.path("stem").asText(), "single", options, images), submission);
        } catch (Exception exception) {
            throw new DailyTaskException(500, "TASK_OPERATION_FAILED", "每日任务题目读取失败", true, List.of(), exception);
        }
    }

    private String singleAnswer(SubmitDailyTaskItemBo command) {
        if (command == null || command.getAnswer() == null || command.getAnswer().getValue() == null
            || command.getAnswer().getValue().size() != 1 || command.getAnswer().getValue().getFirst() == null
            || command.getAnswer().getValue().getFirst().isBlank()) {
            throw invalid("answer.value", "单选题必须且只能选择一个选项");
        }
        return command.getAnswer().getValue().getFirst();
    }

    private <T> T replay(KnowledgePracticeIdempotencyRow row, String payloadHash, Class<T> type) {
        if (!payloadHash.equals(row.getPayloadHash()) || !"succeeded".equals(row.getStatus()) || row.getResponseBody() == null) {
            throw failure(409, "TASK_IDEMPOTENCY_CONFLICT", "请求号已被其他载荷使用或仍在处理中", false);
        }
        try { return jsonMapper.treeToValue(jsonMapper.readTree(row.getResponseBody()).path("response").path("data"), type); }
        catch (Exception exception) {
            throw new DailyTaskException(500, "TASK_OPERATION_FAILED", "幂等响应读取失败", true, List.of(), exception);
        }
    }

    private String response(Object data) {
        return VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.DAILY_TASK_ACTION_IDEMPOTENCY,
            Map.of("response", Map.of("code", 200, "msg", "操作成功", "data", data)));
    }

    private List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private long parseId(String raw, String field) {
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) { throw invalid(field, "必须是十进制正整数"); }
    }

    private void requireOrder(int value) { if (value <= 0) throw invalid("questionOrder", "题序必须为正整数"); }

    private String normalizeRequestId(String raw) {
        try {
            if (raw == null || raw.length() != 36 || !raw.equals(raw.trim())) throw new IllegalArgumentException();
            return UUID.fromString(raw).toString();
        } catch (RuntimeException exception) { throw invalid("X-Request-Id", "请求号必须是规范 UUID"); }
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private DailyTaskException invalid(String field, String message) {
        return new DailyTaskException(400, "TASK_REQUEST_INVALID", "请求参数格式不正确", false,
            List.of(new KnowledgePracticeErrorVo.FieldErrorVo(field, "INVALID_FORMAT", message)), null);
    }

    private DailyTaskException failure(int status, String code, String message, boolean retryable) {
        return new DailyTaskException(status, code, message, retryable);
    }
}
