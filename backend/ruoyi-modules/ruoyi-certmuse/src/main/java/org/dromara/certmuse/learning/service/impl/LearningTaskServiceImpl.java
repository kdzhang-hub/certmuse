package org.dromara.certmuse.learning.service.impl;

import cn.hutool.core.util.IdUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.learning.domain.LearningTaskCandidateRow;
import org.dromara.certmuse.learning.domain.LearningTaskContentRow;
import org.dromara.certmuse.learning.domain.LearningTaskGoalRow;
import org.dromara.certmuse.learning.domain.LearningTaskIdempotencyRow;
import org.dromara.certmuse.learning.domain.LearningTaskQuestionRow;
import org.dromara.certmuse.learning.domain.LearningTaskRow;
import org.dromara.certmuse.learning.domain.LearningTaskSessionRow;
import org.dromara.certmuse.learning.domain.bo.LearningTaskQueryBo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskConflictDetailsVo;
import org.dromara.certmuse.learning.domain.vo.DailyTaskLearningContentVo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskErrorVo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskLaunchVo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskListItemVo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskPageVo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskSupplementVo;
import org.dromara.certmuse.learning.domain.vo.StartDailyTaskPracticeVo;
import org.dromara.certmuse.learning.mapper.LearningTaskMapper;
import org.dromara.certmuse.learning.service.LearningTaskService;
import org.dromara.certmuse.learning.support.LearningTaskException;
import org.dromara.certmuse.learning.support.LearningJsonSchema;
import org.dromara.certmuse.learning.support.LearningContentImageUrlService;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Transactional U10 implementation with database-enforced session and batch uniqueness. */
@Slf4j
@Service
public class LearningTaskServiceImpl implements LearningTaskService {
    private static final String LAUNCH = "LAUNCH_LEARNING_TASK";
    private static final String SUPPLEMENT = "SUPPLEMENT_LEARNING_TASKS";
    private static final String START_PRACTICE = "START_DAILY_TASK_PRACTICE";
    private static final int TARGET_POOL_SIZE = 4;
    private static final int MAX_QUESTION_COUNT = 8;
    private static final int LEARNING_MINUTES = 10;

    private final LearningTaskMapper mapper;
    private final JsonMapper jsonMapper;
    private final PlatformTransactionManager transactionManager;
    private final LearningContentImageUrlService learningContentImageUrlService;

    public LearningTaskServiceImpl(LearningTaskMapper mapper, JsonMapper jsonMapper,
                                   PlatformTransactionManager transactionManager,
                                   LearningContentImageUrlService learningContentImageUrlService) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.transactionManager = transactionManager;
        this.learningContentImageUrlService = learningContentImageUrlService;
    }

    @Override
    @Transactional(readOnly = true)
    public LearningTaskPageVo page(long userId, LearningTaskQueryBo query) {
        LearningTaskGoalRow goal = requireGoal(mapper.selectActiveGoal(userId));
        String keyword = normalizeKeyword(query.getKeyword());
        int offset = Math.multiplyExact(query.getPageNum() - 1, query.getPageSize());
        int available = mapper.countIncompleteTasks(userId, goal.getId());
        long total = mapper.countTaskPage(userId, goal.getId(), keyword);
        List<LearningTaskListItemVo> rows = mapper.selectTaskPage(
            userId, goal.getId(), keyword, offset, query.getPageSize());
        rows.forEach(row -> row.setPhaseSummary(List.of("知识点学习", "知识点练习")));
        return new LearningTaskPageVo(rows, total, available == 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningTaskLaunchVo launch(long userId, String rawTaskId, String rawRequestId) {
        long taskId = parseTaskId(rawTaskId);
        String requestId = normalizeRequestId(rawRequestId);
        LearningTaskGoalRow goal = requireGoal(mapper.lockActiveGoal(userId));
        String payloadHash = hash(userId + ":" + goal.getId() + ":" + taskId);
        LearningTaskIdempotencyRow existing = mapper.selectIdempotency(LAUNCH, requestId);
        if (existing != null) {
            return replay(existing, payloadHash, LearningTaskLaunchVo.class);
        }
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, LAUNCH, requestId, payloadHash) == 0) {
            return replay(requireIdempotency(LAUNCH, requestId), payloadHash, LearningTaskLaunchVo.class);
        }

        LearningTaskSessionRow active = mapper.lockActiveDailySession(userId, goal.getId());
        LearningTaskRow task = mapper.lockOwnedTask(userId, goal.getId(), taskId);
        if (task == null) {
            throw failure(404, "TASK_NOT_FOUND", "学习任务不存在", false);
        }
        if (active != null && active.getSourceTaskId() != taskId) {
            throw activeTask(active);
        }
        if (task.isCompleted()) {
            throw failure(409, "TASK_ALREADY_COMPLETED", "学习任务已完成", false);
        }

        LearningTaskSessionRow session = active != null ? active : mapper.selectTaskSession(userId, goal.getId(), taskId);
        if (session != null) {
            LearningTaskLaunchVo result = continueSession(session, task);
            completeIdempotency(idempotencyId, session.getId(), result);
            return result;
        }

        long sessionId = IdUtil.getSnowflakeNextId();
        if (mapper.insertSession(sessionId, userId, goal, task, requestId) == 0) {
            LearningTaskSessionRow winner = mapper.lockActiveDailySession(userId, goal.getId());
            if (winner == null) {
                winner = mapper.selectTaskSession(userId, goal.getId(), taskId);
            }
            if (winner == null) {
                throw failure(409, "TASK_IDEMPOTENCY_CONFLICT", "任务启动发生并发冲突", false);
            }
            if (winner.getSourceTaskId() != taskId) {
                throw activeTask(winner);
            }
            LearningTaskLaunchVo result = continueSession(winner, task);
            completeIdempotency(idempotencyId, winner.getId(), result);
            return result;
        }
        List<LearningTaskQuestionRow> questions = mapper.selectTaskQuestions(task.getQuestionItemId());
        if (task.getQuestionCount() < 1 || task.getQuestionCount() > MAX_QUESTION_COUNT
            || questions.size() != task.getQuestionCount()) {
            throw new IllegalStateException("daily task frozen question count is inconsistent or out of range");
        }
        for (int index = 0; index < questions.size(); index++) {
            mapper.insertSessionQuestion(IdUtil.getSnowflakeNextId(), sessionId, index + 1, questions.get(index));
        }
        mapper.insertTaskAttempt(IdUtil.getSnowflakeNextId(), task.getKnowledgeItemId(), sessionId);
        LearningTaskLaunchVo result = new LearningTaskLaunchVo(String.valueOf(taskId), "OPEN_LEARNING_CONTENT", null);
        completeIdempotency(idempotencyId, sessionId, result);
        log.info("Learning task launched, requestId={}, goalId={}, taskId={}, action={}, sessionId={}",
            requestId, goal.getId(), taskId, result.nextAction(), sessionId);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public DailyTaskLearningContentVo learningContent(long userId, String rawTaskId) {
        long taskId = parseTaskId(rawTaskId);
        LearningTaskGoalRow goal = requireGoal(mapper.selectActiveGoal(userId));
        LearningTaskContentRow row = mapper.selectOwnedLearningContent(userId, goal.getId(), taskId);
        if (row == null) throw failure(404, "TASK_NOT_FOUND", "学习任务不存在", false);
        try {
            JsonNode root = jsonMapper.readTree(row.getTargetData());
            if (!LearningJsonSchema.DAILY_TASK_LEARNING_CONTENT.version().equals(root.path("schema_version").asText())) {
                throw new IllegalStateException("unsupported daily task learning-content snapshot");
            }
            List<DailyTaskLearningContentVo.SectionVo> sections = new ArrayList<>();
            for (JsonNode section : root.path("sections")) {
                List<LearningContentImage> images = new ArrayList<>();
                for (JsonNode image : section.path("images")) {
                    images.add(new LearningContentImage(image.path("stableId").asText(),
                        learningContentImageUrlService.accessUrl(image.path("storagePath").asText(null)),
                        image.path("altText").asText(null), image.path("sortOrder").asInt()));
                }
                images.sort(Comparator.comparingInt(LearningContentImage::sortOrder)
                    .thenComparing(LearningContentImage::stableId));
                sections.add(new DailyTaskLearningContentVo.SectionVo(section.path("order").asInt(),
                    section.path("title").asText(null), section.path("content").asText(),
                    images.stream().map(image -> new DailyTaskLearningContentVo.ImageVo(
                        image.url(), image.altText(), image.sortOrder())).toList()));
            }
            sections.sort(java.util.Comparator.comparingInt(DailyTaskLearningContentVo.SectionVo::order));
            return new DailyTaskLearningContentVo(String.valueOf(taskId), "LEARNING", root.path("title").asText(),
                root.path("knowledgePoint").asText(), row.getEstimatedMinutes(), sections, "START_PRACTICE");
        } catch (LearningTaskException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LearningTaskException(500, "TASK_OPERATION_FAILED", "教材内容读取失败", true,
                List.of(), null, exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StartDailyTaskPracticeVo startPractice(long userId, String rawTaskId, String rawRequestId) {
        long taskId = parseTaskId(rawTaskId);
        String requestId = normalizeRequestId(rawRequestId);
        LearningTaskGoalRow goal = requireGoal(mapper.lockActiveGoal(userId));
        String payloadHash = hash(userId + ":" + taskId);
        LearningTaskIdempotencyRow existing = mapper.selectIdempotency(START_PRACTICE, requestId);
        if (existing != null) return replay(existing, payloadHash, StartDailyTaskPracticeVo.class);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, START_PRACTICE, requestId, payloadHash) == 0) {
            return replay(requireIdempotency(START_PRACTICE, requestId), payloadHash, StartDailyTaskPracticeVo.class);
        }
        LearningTaskSessionRow active = mapper.lockActiveDailySession(userId, goal.getId());
        LearningTaskRow task = mapper.lockOwnedTask(userId, goal.getId(), taskId);
        if (task == null) throw failure(404, "TASK_NOT_FOUND", "学习任务不存在", false);
        if (active != null && active.getSourceTaskId() != taskId) throw activeTask(active);
        LearningTaskSessionRow session = active != null ? active : mapper.selectTaskSession(userId, goal.getId(), taskId);
        if (session == null) throw failure(404, "TASK_SESSION_NOT_FOUND", "请先启动学习任务", false);
        if ("invalid".equals(session.getStatus()) || "cancelled".equals(session.getStatus())) {
            throw failure(409, "TASK_SESSION_NOT_RECOVERABLE", "学习任务会话不可恢复", false);
        }
        if ("completed".equals(session.getStatus())) {
            throw failure(409, "TASK_SESSION_NOT_ACTIVE", "学习任务会话已完成", false);
        }
        mapper.completeLearningAttempt(task.getKnowledgeItemId(), session.getId());
        mapper.insertTaskAttempt(IdUtil.getSnowflakeNextId(), task.getQuestionItemId(), session.getId());
        mapper.startPracticeSession(session.getId());
        StartDailyTaskPracticeVo result = new StartDailyTaskPracticeVo(String.valueOf(taskId),
            String.valueOf(session.getId()), "OPEN_PRACTICE_SESSION");
        completeIdempotency(idempotencyId, session.getId(), result);
        return result;
    }

    @Override
    public void completePracticeItem(long userId, long sessionId) {
        if (mapper.completePracticeItem(userId, sessionId) == 0) {
            throw failure(409, "TASK_SESSION_NOT_ACTIVE", "每日任务练习项无法完成", false);
        }
    }

    @Override
    public LearningTaskSupplementVo supplement(long userId, String rawRequestId) {
        String requestId = normalizeRequestId(rawRequestId);
        LearningTaskGoalRow current = requireGoal(mapper.selectActiveGoal(userId));
        String payloadHash = hash(userId + ":" + current.getId());
        LearningTaskIdempotencyRow existing = mapper.selectIdempotency(SUPPLEMENT, requestId);
        if (existing != null) {
            return replay(existing, payloadHash, LearningTaskSupplementVo.class);
        }
        SupplementOutcome outcome = transactionTemplate().execute(status ->
            replenish(userId, current.getId(), "manual", requestId,
                "TASK_SUPPLEMENT:MANUAL:" + requestId, payloadHash, true));
        if (outcome == null) {
            throw new IllegalStateException("supplement transaction returned no outcome");
        }
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        return outcome.result();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> activeGoalIds(int offset, int limit) {
        return mapper.selectActiveGoalIds(offset, limit);
    }

    @Override
    public void supplementScheduled(long goalId, LocalDate businessDate) {
        String key = "TASK_SUPPLEMENT:SCHEDULED:" + goalId + ":" + businessDate;
        transactionTemplate().executeWithoutResult(status -> replenish(0, goalId, "scheduled", null, key, null, false));
    }

    private SupplementOutcome replenish(long expectedUserId, long goalId, String triggerType, String requestId,
                                         String generationKey, String payloadHash, boolean idempotent) {
        long idempotencyId = 0;
        if (idempotent) {
            LearningTaskIdempotencyRow existing = mapper.selectIdempotency(SUPPLEMENT, requestId);
            if (existing != null) {
                return new SupplementOutcome(replay(existing, payloadHash, LearningTaskSupplementVo.class), null);
            }
            idempotencyId = IdUtil.getSnowflakeNextId();
            if (mapper.insertIdempotency(idempotencyId, SUPPLEMENT, requestId, payloadHash) == 0) {
                return new SupplementOutcome(replay(requireIdempotency(SUPPLEMENT, requestId), payloadHash,
                    LearningTaskSupplementVo.class), null);
            }
        }
        LearningTaskGoalRow goal = mapper.lockGoalById(goalId);
        if (goal == null || (expectedUserId != 0 && goal.getUserId() != expectedUserId)) {
            LearningTaskException error = failure(422, "LEARNING_GOAL_NOT_ACTIVE", "当前没有有效学习目标", false);
            if (idempotent) persistFailure(idempotencyId, error);
            return new SupplementOutcome(null, error);
        }
        int before = mapper.countIncompleteTasks(goal.getUserId(), goal.getId());
        int requested = Math.max(0, TARGET_POOL_SIZE - before);
        Long ruleVersionId = requested == 0 ? null : mapper.selectUniquePublishedRuleVersion();
        long batchId = IdUtil.getSnowflakeNextId();
        String input = VersionedJsonDocumentFactory.json(jsonMapper, LearningJsonSchema.TASK_REPLENISHMENT_INPUT,
            Map.of("beforeAvailableCount", before, "requestedCount", requested,
                "goalId", String.valueOf(goal.getId())));
        if (mapper.insertBatch(batchId, goal.getUserId(), goal.getId(), triggerType, before, requested,
            ruleVersionId, requestId, generationKey, input) == 0) {
            if (mapper.restartFailedBatch(generationKey, before, requested, ruleVersionId, input) == 0) {
                return new SupplementOutcome(new LearningTaskSupplementVo(before, requested, 0, false), null);
            }
            Long retryBatchId = mapper.selectBatchIdByGenerationKey(generationKey);
            if (retryBatchId == null) throw new IllegalStateException("restarted supplement batch disappeared");
            batchId = retryBatchId;
        }
        if (requested == 0) {
            LearningTaskSupplementVo result = new LearningTaskSupplementVo(before, 0, 0, false);
            mapper.completeBatch(batchId, 0, resultSummary(List.of()));
            if (idempotent) completeIdempotency(idempotencyId, batchId, result);
            return new SupplementOutcome(result, null);
        }
        if (mapper.countPublishedRuleVersions() != 1 || ruleVersionId == null) {
            return failedSupplement(idempotencyId, idempotent, batchId, 503, "TASK_RULE_VERSION_UNAVAILABLE",
                "推荐规则暂不可用", true);
        }

        List<Long> createdTaskIds = new ArrayList<>();
        List<LearningTaskCandidateRow> candidates = mapper.selectCandidates(
            goal.getUserId(), goal.getId(), goal.getSyllabusVersionId(),
            LearningJsonSchema.DAILY_TASK_LEARNING_CONTENT.version(), requested * 4);
        int ordinal = 0;
        for (LearningTaskCandidateRow candidate : candidates) {
            if (createdTaskIds.size() >= requested) break;
            List<LearningTaskQuestionRow> questions = mapper.selectCandidateQuestions(goal.getUserId(), goal.getId(),
                goal.getSyllabusVersionId(), candidate.getKnowledgePointId(), MAX_QUESTION_COUNT);
            if (questions.isEmpty()) continue;
            ordinal++;
            int practiceMinutes = (questions.stream().mapToInt(LearningTaskQuestionRow::getEstimatedSeconds).sum() + 59) / 60;
            long taskId = IdUtil.getSnowflakeNextId();
            String recommendation = recommendation(candidate.getProfileStatus());
            String display = VersionedJsonDocumentFactory.json(jsonMapper, LearningJsonSchema.TASK_DISPLAY,
                Map.of("title", candidate.getKnowledgeName() + "专项学习", "knowledgePoint",
                    candidate.getKnowledgeName(), "recommendation", recommendation));
            String profile = VersionedJsonDocumentFactory.json(jsonMapper, LearningJsonSchema.TASK_PROFILE,
                Map.of("profileStatus", candidate.getProfileStatus(),
                    "algorithmVersion", "TASK_RECOMMENDATION_V1"));
            mapper.insertTask(taskId, goal.getUserId(), goal.getId(), candidate, batchId, ruleVersionId, ordinal,
                LEARNING_MINUTES + practiceMinutes, display, profile);
            long knowledgeItemId = IdUtil.getSnowflakeNextId();
            mapper.insertTaskItem(knowledgeItemId, taskId, 1, "knowledge", candidate, LEARNING_MINUTES,
                candidate.getContentSnapshot());
            long questionItemId = IdUtil.getSnowflakeNextId();
            mapper.insertTaskItem(questionItemId, taskId, 2, "question", candidate, practiceMinutes,
                VersionedJsonDocumentFactory.json(jsonMapper, LearningJsonSchema.TASK_QUESTION_ITEM,
                    Map.of("questionCount", questions.size(), "snapshotVersion", "1.0",
                        "selectionRuleVersion", "TASK_RECOMMENDATION_V1")));
            for (int index = 0; index < questions.size(); index++) {
                mapper.insertTaskQuestion(IdUtil.getSnowflakeNextId(), questionItemId, index + 1, questions.get(index));
            }
            createdTaskIds.add(taskId);
        }
        if (createdTaskIds.isEmpty()) {
            return failedSupplement(idempotencyId, idempotent, batchId, 422, "TASK_CONTENT_UNAVAILABLE",
                "当前没有可推荐的学习内容", true);
        }
        mapper.completeBatch(batchId, createdTaskIds.size(), resultSummary(createdTaskIds));
        LearningTaskSupplementVo result = new LearningTaskSupplementVo(before, requested, createdTaskIds.size(), true);
        if (idempotent) completeIdempotency(idempotencyId, batchId, result);
        log.info("Learning tasks supplemented, requestId={}, goalId={}, batchId={}, requested={}, created={}, trigger={}",
            requestId, goal.getId(), batchId, requested, createdTaskIds.size(), triggerType);
        return new SupplementOutcome(result, null);
    }

    private SupplementOutcome failedSupplement(long idempotencyId, boolean idempotent, long batchId, int status,
                                                String code, String message, boolean retryable) {
        LearningTaskException error = failure(status, code, message, retryable);
        mapper.failBatch(batchId, code, VersionedJsonDocumentFactory.json(jsonMapper,
            LearningJsonSchema.TASK_REPLENISHMENT_RESULT, Map.of("failureCode", code)));
        if (idempotent) persistFailure(idempotencyId, error);
        return new SupplementOutcome(null, error);
    }

    private LearningTaskLaunchVo continueSession(LearningTaskSessionRow session, LearningTaskRow task) {
        if ("completed".equals(session.getStatus())) {
            throw failure(409, "TASK_ALREADY_COMPLETED", "学习任务已完成", false);
        }
        if ("invalid".equals(session.getStatus()) || "cancelled".equals(session.getStatus())) {
            throw failure(409, "TASK_SESSION_NOT_RECOVERABLE", "学习任务会话不可恢复", false);
        }
        if (session.isLearningCompleted()) {
            mapper.insertTaskAttempt(IdUtil.getSnowflakeNextId(), task.getQuestionItemId(), session.getId());
            mapper.startPracticeSession(session.getId());
            return new LearningTaskLaunchVo(String.valueOf(task.getId()), "OPEN_PRACTICE_SESSION",
                String.valueOf(session.getId()));
        }
        return new LearningTaskLaunchVo(String.valueOf(task.getId()), "OPEN_LEARNING_CONTENT", null);
    }

    private LearningTaskException activeTask(LearningTaskSessionRow session) {
        String action = session.isLearningCompleted() ? "OPEN_PRACTICE_SESSION" : "OPEN_LEARNING_CONTENT";
        String exposedSessionId = session.isLearningCompleted() ? String.valueOf(session.getId()) : null;
        return new LearningTaskException(409, "TASK_SESSION_IN_PROGRESS", "已有其他学习任务正在进行", false,
            List.of(), new LearningTaskConflictDetailsVo(String.valueOf(session.getSourceTaskId()), action,
                exposedSessionId), null);
    }

    private void completeIdempotency(long id, long resourceId, Object result) {
        mapper.succeedIdempotency(id, "cm_learning_task", resourceId, response(200, result));
    }

    private void persistFailure(long id, LearningTaskException error) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", error.errorCode());
        data.put("retryable", error.retryable());
        data.put("traceId", null);
        data.put("fieldErrors", List.of());
        data.put("details", error.details());
        mapper.failIdempotency(id, error.status(), response(error.status(), data));
    }

    private <T> T replay(LearningTaskIdempotencyRow record, String payloadHash, Class<T> type) {
        if (!payloadHash.equals(record.getPayloadHash())) {
            throw failure(409, "TASK_IDEMPOTENCY_CONFLICT", "请求号已被其他载荷使用", false);
        }
        if ("failed".equals(record.getStatus()) && record.getResponseBody() != null) {
            try {
                JsonNode data = jsonMapper.readTree(record.getResponseBody()).path("response").path("data");
                throw failure(record.getResponseStatus(), data.path("errorCode").asText("TASK_OPERATION_FAILED"),
                    "上次请求未成功", data.path("retryable").asBoolean(false));
            } catch (LearningTaskException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("failed idempotency response cannot be read", exception);
            }
        }
        if (!"succeeded".equals(record.getStatus()) || record.getResponseBody() == null) {
            throw failure(409, "TASK_IDEMPOTENCY_CONFLICT", "相同请求正在处理中", false);
        }
        try {
            JsonNode data = jsonMapper.readTree(record.getResponseBody()).path("response").path("data");
            return jsonMapper.treeToValue(data, type);
        } catch (Exception exception) {
            throw new IllegalStateException("idempotency response cannot be read", exception);
        }
    }

    private LearningTaskIdempotencyRow requireIdempotency(String action, String requestId) {
        LearningTaskIdempotencyRow row = mapper.selectIdempotency(action, requestId);
        if (row == null) throw failure(409, "TASK_IDEMPOTENCY_CONFLICT", "请求号并发冲突", false);
        return row;
    }

    private LearningTaskGoalRow requireGoal(LearningTaskGoalRow goal) {
        if (goal == null) throw failure(422, "LEARNING_GOAL_NOT_ACTIVE", "当前没有有效学习目标", false);
        return goal;
    }

    private String normalizeKeyword(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw invalid("keyword", "INVALID_FORMAT", "关键词不能为空白");
        }
        if (normalized.length() > 100) {
            throw invalid("keyword", "OUT_OF_RANGE", "关键词不能超过100个字符");
        }
        return normalized;
    }

    private long parseTaskId(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw invalid("taskId", "INVALID_FORMAT", "任务ID必须是十进制正整数");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid("taskId", "OUT_OF_RANGE", "任务ID超出范围");
        }
    }

    private String normalizeRequestId(String value) {
        try {
            if (value == null || value.length() != 36 || !value.equals(value.trim())) throw new IllegalArgumentException();
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid("X-Request-Id", value == null ? "REQUIRED" : "INVALID_FORMAT", "请求号必须是标准UUID");
        }
    }

    private LearningTaskException invalid(String field, String code, String message) {
        return new LearningTaskException(400, "TASK_REQUEST_INVALID", "请求参数格式不正确", false,
            List.of(new LearningTaskErrorVo.FieldErrorVo(field, code, message)), null, null);
    }

    private LearningTaskException failure(int status, String code, String message, boolean retryable) {
        return new LearningTaskException(status, code, message, retryable);
    }

    private String recommendation(String profileStatus) {
        return switch (profileStatus) {
            case "urgent", "weak" -> "该知识点当前风险较高，建议优先巩固。";
            case "needs_retest", "pending_verification", "needs_review", "expired" -> "该知识点需要复习验证，已为你安排专项学习。";
            case "mastered", "mastery_candidate", "proficient" -> "根据当前学习记录安排复习，以保持掌握稳定。";
            default -> "根据当前学习记录安排专项学习。";
        };
    }

    private String resultSummary(List<Long> taskIds) {
        return VersionedJsonDocumentFactory.json(jsonMapper, LearningJsonSchema.TASK_REPLENISHMENT_RESULT,
            Map.of("createdTaskIds", taskIds.stream().map(String::valueOf).toList()));
    }

    private String response(int status, Object data) {
        return VersionedJsonDocumentFactory.json(jsonMapper, LearningJsonSchema.TASK_IDEMPOTENCY_RESPONSE,
            Map.of("response", Map.of("code", status,
                "msg", status == 200 ? "操作成功" : "请求失败", "data", data)));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private record SupplementOutcome(LearningTaskSupplementVo result, LearningTaskException failure) {
    }

    private record LearningContentImage(String stableId, String url, String altText, int sortOrder) {
    }
}
