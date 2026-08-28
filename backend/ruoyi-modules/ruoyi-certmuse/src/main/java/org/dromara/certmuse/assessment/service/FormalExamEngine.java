package org.dromara.certmuse.assessment.service;

import cn.hutool.core.util.IdUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.FormalExamItemRow;
import org.dromara.certmuse.assessment.domain.FormalExamJobRow;
import org.dromara.certmuse.assessment.domain.FormalExamSessionRow;
import org.dromara.certmuse.assessment.domain.FormalExamTimerLeaseRow;
import org.dromara.certmuse.assessment.domain.PastPaperGoalRow;
import org.dromara.certmuse.assessment.domain.PastPaperIdempotencyRow;
import org.dromara.certmuse.assessment.domain.PastPaperPaperRow;
import org.dromara.certmuse.assessment.domain.PastPaperQuestionRow;
import org.dromara.certmuse.assessment.domain.bo.FormalExamDraftBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamFinishBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamPauseBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamTimerEventBo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamFinishCheckVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamFinishVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamItemVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamResultVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamSessionVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamStatusVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamTimerEventVo;
import org.dromara.certmuse.assessment.mapper.FormalExamMapper;
import org.dromara.certmuse.assessment.support.PastPaperException;
import org.dromara.certmuse.assessment.support.SimulationException;
import org.dromara.certmuse.assessment.support.SubjectiveAnswerValidator;
import org.dromara.certmuse.assessment.support.SubjectiveGradingTasks;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.dromara.certmuse.shared.schema.JsonSchemaVersion;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Shared formal-exam lifecycle; it never reads or writes diagnostic state. */
@Service
@RequiredArgsConstructor
public class FormalExamEngine {
    private static final Duration HEARTBEAT_EXPIRY = Duration.ofSeconds(20);
    private final FormalExamMapper mapper;
    private final JsonMapper jsonMapper;
    private final QuestionImageUrlService imageUrlService;
    private final SubjectiveGradingTasks subjectiveTasks;

    public record StartResult(String sessionId, String revisionId, boolean resumed, int formalAttemptNo,
                              String startedTime, int durationSeconds, int totalCount, String answerPath) { }

    @Transactional(rollbackFor = Exception.class)
    public StartResult start(long userId, long collectionId, String collectionType, String sessionType,
                             long expectedRevisionId, long expectedGoalVersion, String requestId) {
        String request = requestId(requestId, sessionType);
        String action = action(sessionType, "START");
        String payloadHash = hash(action + ':' + userId + ':' + collectionId + ':' + expectedRevisionId + ':' + expectedGoalVersion);
        PastPaperIdempotencyRow old = mapper.selectIdempotency(action, request);
        if (old != null) return replay(old, payloadHash, StartResult.class, sessionType);
        PastPaperPaperRow paper = mapper.selectCurrentPaper(collectionId, collectionType);
        if (paper == null) throw failure(sessionType, 404, code(sessionType, "NOT_FOUND"), "试卷不存在或不可见", false);
        PastPaperGoalRow goal = mapper.selectActiveGoal(userId);
        if (goal == null) throw failure(sessionType, 409, "LEARNING_GOAL_NOT_ACTIVE", "当前没有有效学习目标", false);
        if (!Objects.equals(expectedRevisionId, paper.getRevisionId())) {
            throw failure(sessionType, 409, code(sessionType, "REVISION_CHANGED"), "试卷已更新", true);
        }
        if (!Objects.equals(expectedGoalVersion, goal.getRowVersion())) {
            throw failure(sessionType, 409, code(sessionType, "GOAL_VERSION_CONFLICT"), "学习目标已变化", true);
        }
        if (!Objects.equals(goal.getCertificationId(), paper.getCertificationId())) {
            throw failure(sessionType, 422, code(sessionType, "GOAL_SCOPE_MISMATCH"), "试卷不属于当前学习目标", false);
        }
        if (paper.getDurationMinutes() == null || paper.getDurationMinutes() <= 0) {
            throw failure(sessionType, 422, code(sessionType, "UNAVAILABLE"), "试卷时长不可用", false);
        }
        List<PastPaperQuestionRow> questions = mapper.selectCurrentQuestions(collectionId, collectionType);
        validateQuestions(sessionType, questions);
        FormalExamSessionRow active = mapper.selectActiveSession(userId, goal.getId(), sessionType);
        if (active != null) {
            if (!Objects.equals(active.getCollectionRevisionId(), paper.getRevisionId())) {
                throw failure(sessionType, 409, code(sessionType, "ACTIVE_SESSION_CONFLICT"),
                    "当前已有进行中的同类型考试，请先完成或继续该考试", false);
            }
            long replayId = IdUtil.getSnowflakeNextId();
            if (mapper.insertIdempotency(replayId, action, request, payloadHash) == 0) {
                return replay(mapper.selectIdempotency(action, request), payloadHash, StartResult.class, sessionType);
            }
            StartResult resumed = startResult(active, true);
            mapper.completeIdempotency(replayId, "cm_learning_session", active.getId(), response(resumed));
            return resumed;
        }
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, action, request, payloadHash) == 0) {
            return replay(mapper.selectIdempotency(action, request), payloadHash, StartResult.class, sessionType);
        }
        Long ruleVersionId = mapper.selectPublishedRuleVersion();
        if (ruleVersionId == null) throw failure(sessionType, 503, code(sessionType, "RULE_VERSION_UNAVAILABLE"), "画像规则暂不可用", true);
        int attemptNo = mapper.nextFormalAttemptNo(userId, goal.getId(), paper.getRevisionId(), sessionType);
        int durationSeconds = paper.getDurationMinutes() * 60;
        long sessionId = IdUtil.getSnowflakeNextId();
        mapper.insertSession(sessionId, userId, goal, paper.getRevisionId(), sessionType, ruleVersionId, request,
            durationSeconds, attemptNo);
        for (PastPaperQuestionRow question : questions) {
            long sessionQuestionId = IdUtil.getSnowflakeNextId();
            Snapshots snapshots = snapshots(question);
            mapper.insertSessionQuestion(sessionQuestionId, sessionId, question, snapshots.presentation(),
                snapshots.grading(), snapshots.knowledge());
            mapper.insertDraftAttempt(IdUtil.getSnowflakeNextId(), IdUtil.getSnowflakeNextId(), sessionQuestionId,
                userId, attemptRequestId(request, question.getQuestionOrder()), emptyAnswer(question.getQuestionType()));
        }
        FormalExamSessionRow created = mapper.selectSession(userId, sessionId, sessionType);
        StartResult result = startResult(created, false);
        mapper.completeIdempotency(idempotencyId, "cm_learning_session", sessionId, response(result));
        return result;
    }

    public FormalExamSessionVo session(long userId, long sessionId, String sessionType) {
        return sessionVo(requireSession(userId, sessionId, sessionType));
    }

    public FormalExamItemVo item(long userId, long sessionId, int questionOrder, String sessionType) {
        FormalExamSessionRow session = requireSession(userId, sessionId, sessionType);
        FormalExamItemRow item = mapper.selectItem(userId, sessionId, positive(questionOrder, sessionType));
        if (item == null) throw failure(sessionType, 400, code(sessionType, "REQUEST_INVALID"), "题序超出范围", false);
        return itemVo(session, item);
    }

    @Transactional(rollbackFor = Exception.class)
    public FormalExamSessionVo saveDraft(long userId, long sessionId, int questionOrder, String sessionType,
                                         String requestId, FormalExamDraftBo command) {
        String request = requestId(requestId, sessionType);
        FormalExamSessionRow session = lock(userId, sessionId, sessionType);
        FormalExamItemRow item = mapper.selectItem(userId, sessionId, positive(questionOrder, sessionType));
        if (item == null) throw failure(sessionType, 400, code(sessionType, "REQUEST_INVALID"), "题序超出范围", false);
        String answer = normalizeDraft(item, command, sessionType);
        String action = action(sessionType, "DRAFT");
        String payloadHash = hash(action + ':' + userId + ':' + sessionId + ':' + questionOrder + ':' + answer + ':' + command.getExpectedSessionVersion());
        PastPaperIdempotencyRow old = mapper.selectIdempotency(action, request);
        if (old != null) return replay(old, payloadHash, FormalExamSessionVo.class, sessionType);
        editable(session, sessionType);
        if (!Objects.equals(command.getExpectedSessionVersion(), session.getRowVersion())) throw version(sessionType);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, action, request, payloadHash) == 0) {
            return replay(mapper.selectIdempotency(action, request), payloadHash, FormalExamSessionVo.class, sessionType);
        }
        mapper.updateDraft(item.getAnswerId(), answer);
        if (mapper.updateSessionPosition(sessionId, session.getRowVersion(), command.getCurrentQuestionOrder()) != 1) {
            throw version(sessionType);
        }
        FormalExamSessionVo result = sessionVo(requireSession(userId, sessionId, sessionType));
        mapper.completeIdempotency(idempotencyId, "cm_learning_session", sessionId, response(result));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public FormalExamTimerEventVo timerEvent(long userId, long sessionId, String sessionType, String requestId,
                                             FormalExamTimerEventBo command) {
        String request = requestId(requestId, sessionType);
        String type = command.getEventType().trim().toUpperCase();
        if (!List.of("ENTER", "HEARTBEAT", "HIDDEN", "LEAVE").contains(type)) {
            throw failure(sessionType, 400, code(sessionType, "REQUEST_INVALID"), "计时事件不正确", false);
        }
        FormalExamSessionRow session = lock(userId, sessionId, sessionType);
        FormalExamItemRow item = mapper.selectItem(userId, sessionId, command.getQuestionOrder());
        if (item == null) throw failure(sessionType, 400, code(sessionType, "REQUEST_INVALID"), "题序超出范围", false);
        String action = action(sessionType, "TIMER");
        String payloadHash = hash(action + ':' + sessionId + ':' + type + ':' + command.getQuestionOrder() + ':'
            + command.getLeaseId() + ':' + command.getExpectedSessionVersion());
        PastPaperIdempotencyRow old = mapper.selectIdempotency(action, request);
        if (old != null) return replay(old, payloadHash, FormalExamTimerEventVo.class, sessionType);
        editable(session, sessionType);
        if (!Objects.equals(command.getExpectedSessionVersion(), session.getRowVersion())) throw version(sessionType);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, action, request, payloadHash) == 0) {
            return replay(mapper.selectIdempotency(action, request), payloadHash, FormalExamTimerEventVo.class, sessionType);
        }
        FormalExamTimerLeaseRow lease = mapper.lockTimerLease(sessionId);
        OffsetDateTime now = OffsetDateTime.now();
        FormalExamTimerEventVo result;
        if ("ENTER".equals(type)) {
            if (lease != null) closeLease(session, lease, now, "新的窗口或设备进入");
            String leaseId = UUID.randomUUID().toString();
            mapper.insertTimerLease(sessionId, item.getQuestionOrder(), item.getAttemptId(), leaseId);
            mapper.activateTimer(item.getAttemptId());
            result = timingVo(session, item.getQuestionOrder(), true, leaseId, "RUNNING");
        } else if (lease == null || !Objects.equals(lease.getLeaseId(), command.getLeaseId())) {
            result = timingVo(session, item.getQuestionOrder(), false, null, "REPLACED");
        } else if ("HEARTBEAT".equals(type)) {
            if (expired(lease, now)) {
                mapper.markTimerInvalid(lease.getAttemptId(), "计时心跳超过20秒未确认");
                mapper.deleteTimerLease(sessionId);
                result = timingVo(session, item.getQuestionOrder(), false, null, "STOPPED");
            } else {
                accumulate(session, lease, now);
                mapper.deleteTimerLease(sessionId);
                mapper.insertTimerLease(sessionId, item.getQuestionOrder(), item.getAttemptId(), lease.getLeaseId());
                result = timingVo(session, item.getQuestionOrder(), true, lease.getLeaseId(), "RUNNING");
            }
        } else {
            closeLease(session, lease, now, "页面停止计时");
            result = timingVo(session, item.getQuestionOrder(), true, null, "STOPPED");
        }
        if (effectiveElapsed(userId, sessionId) >= session.getDurationSecondsSnapshot()) {
            closeActiveTimer(session);
            finishLocked(session, null);
            result = timingVo(session, item.getQuestionOrder(), true, null, "STOPPED");
        }
        mapper.completeIdempotency(idempotencyId, "cm_learning_session", sessionId, response(result));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public FormalExamSessionVo pause(long userId, long sessionId, String sessionType, String requestId,
                                     FormalExamPauseBo command) {
        String request = requestId(requestId, sessionType);
        FormalExamSessionRow session = lock(userId, sessionId, sessionType);
        String action = action(sessionType, "PAUSE");
        String payloadHash = hash(action + ':' + sessionId + ':' + command.getCurrentQuestionOrder() + ':' + command.getExpectedSessionVersion());
        PastPaperIdempotencyRow old = mapper.selectIdempotency(action, request);
        if (old != null) return replay(old, payloadHash, FormalExamSessionVo.class, sessionType);
        editable(session, sessionType);
        if (!Objects.equals(command.getExpectedSessionVersion(), session.getRowVersion())) throw version(sessionType);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, action, request, payloadHash) == 0) {
            return replay(mapper.selectIdempotency(action, request), payloadHash, FormalExamSessionVo.class, sessionType);
        }
        closeActiveTimer(session);
        if (mapper.updateSessionPosition(sessionId, session.getRowVersion(), command.getCurrentQuestionOrder()) != 1) throw version(sessionType);
        FormalExamSessionVo result = sessionVo(requireSession(userId, sessionId, sessionType));
        mapper.completeIdempotency(idempotencyId, "cm_learning_session", sessionId, response(result));
        return result;
    }

    public FormalExamFinishCheckVo finishCheck(long userId, long sessionId, String sessionType) {
        FormalExamSessionRow session = requireSession(userId, sessionId, sessionType);
        return new FormalExamFinishCheckVo(session.getRowVersion(), session.getAnsweredCount(),
            session.getTotalCount() - session.getAnsweredCount(), session.getTotalCount(), "in_progress".equals(session.getStatus()));
    }

    @Transactional(rollbackFor = Exception.class)
    public FormalExamFinishVo finish(long userId, long sessionId, String sessionType, String requestId,
                                     FormalExamFinishBo command) {
        String request = requestId(requestId, sessionType);
        FormalExamSessionRow session = lock(userId, sessionId, sessionType);
        String action = action(sessionType, "FINISH");
        String payloadHash = hash(action + ':' + sessionId + ':' + command.getExpectedSessionVersion());
        PastPaperIdempotencyRow old = mapper.selectIdempotency(action, request);
        if (old != null) return replay(old, payloadHash, FormalExamFinishVo.class, sessionType);
        if ("submitted".equals(session.getStatus()) || "settling".equals(session.getStatus()) || "completed".equals(session.getStatus())) {
            long replayId = IdUtil.getSnowflakeNextId();
            if (mapper.insertIdempotency(replayId, action, request, payloadHash) == 0) {
                return replay(mapper.selectIdempotency(action, request), payloadHash, FormalExamFinishVo.class, sessionType);
            }
            FormalExamFinishVo result = finishVo(session);
            mapper.completeIdempotency(replayId, "cm_learning_session", sessionId, response(result));
            return result;
        }
        editable(session, sessionType);
        if (!Objects.equals(command.getExpectedSessionVersion(), session.getRowVersion())) throw version(sessionType);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, action, request, payloadHash) == 0) {
            return replay(mapper.selectIdempotency(action, request), payloadHash, FormalExamFinishVo.class, sessionType);
        }
        closeActiveTimer(session);
        finishLocked(session, command.getExpectedSessionVersion());
        FormalExamFinishVo result = finishVo(session);
        mapper.completeIdempotency(idempotencyId, "cm_learning_session", sessionId, response(result));
        return result;
    }

    public FormalExamStatusVo status(long userId, long sessionId, String sessionType) {
        FormalExamSessionRow session = requireSession(userId, sessionId, sessionType);
        return statusVo(session, mapper.selectJobStatus(jobKey(session)));
    }

    @Transactional(rollbackFor = Exception.class)
    public FormalExamStatusVo regenerate(long userId, long sessionId, String sessionType, String requestId) {
        String request = requestId(requestId, sessionType);
        FormalExamSessionRow session = requireSession(userId, sessionId, sessionType);
        String action = action(sessionType, "RESULT_RETRY");
        String payloadHash = hash(action + ':' + userId + ':' + sessionId + ':' + session.getRowVersion());
        PastPaperIdempotencyRow old = mapper.selectIdempotency(action, request);
        if (old != null) return replay(old, payloadHash, FormalExamStatusVo.class, sessionType);
        if (!"failed".equals(mapper.selectJobStatus(jobKey(session)))) {
            throw failure(sessionType, 409, code(sessionType, "RESULT_NOT_RETRYABLE"), "当前结果不可重试", false);
        }
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, action, request, payloadHash) == 0) {
            return replay(mapper.selectIdempotency(action, request), payloadHash, FormalExamStatusVo.class, sessionType);
        }
        mapper.retryJob(jobKey(session));
        FormalExamStatusVo result = statusVo(session, "queued");
        mapper.completeIdempotency(idempotencyId, "cm_learning_session", sessionId, response(result));
        return result;
    }

    public FormalExamResultVo result(long userId, long sessionId, String sessionType) {
        FormalExamSessionRow session = requireSession(userId, sessionId, sessionType);
        if (!"completed".equals(session.getStatus()) || !"succeeded".equals(mapper.selectJobStatus(jobKey(session)))) {
            throw failure(sessionType, 409, code(sessionType, "NOT_FINISHED"), "考试结果仍在生成", true);
        }
        JsonNode report = tree(mapper.selectReport(userId, sessionId), sessionType);
        List<FormalExamResultVo.ItemResultVo> questions = mapper.selectItems(userId, sessionId).stream()
            .map(item -> resultItem(item, sessionType)).toList();
        int correct = (int) questions.stream().filter(item -> Boolean.TRUE.equals(item.correct())).count();
        int wrong = (int) questions.stream().filter(item -> Boolean.FALSE.equals(item.correct())).count();
        int unanswered = (int) questions.stream().filter(FormalExamResultVo.ItemResultVo::unanswered).count();
        int aiFailed = (int) questions.stream().filter(item -> "failed".equals(item.gradingStatus())).count();
        return new FormalExamResultVo(String.valueOf(sessionId), kind(sessionType), session.getTitle(), aiFailed == 0 ? "COMPLETED" : "PARTIAL",
            aiFailed == 0, aiFailed,
            report.path("score").asText("0"), report.path("maxScore").asText("0"), effectiveElapsed(userId, sessionId),
            correct, wrong, unanswered, questions);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean processJob(FormalExamJobRow job) {
        long sessionId = Long.parseLong(tree(job.getPayload(), "simulation").path("sessionId").asText());
        FormalExamSessionRow session = mapper.lockSessionForWorker(sessionId);
        if (session == null) return true;
        if ("completed".equals(session.getStatus())) return true;
        if (!"settling".equals(session.getStatus())) {
            throw new IllegalStateException("formal exam is not ready for result generation");
        }
        boolean waiting = false;
        for (FormalExamItemRow item : mapper.selectItems(session.getUserId(), sessionId)) {
            if (Set.of("graded", "failed").contains(item.getGradingStatus())) continue;
            JsonNode presentation = tree(item.getPresentationSnapshot(), session.getSessionType());
            JsonNode grading = tree(item.getGradingSnapshot(), session.getSessionType());
            JsonNode answer = tree(item.getAnswerData(), session.getSessionType());
            String questionType = presentation.path("questionType").asText();
            BigDecimal itemMax = decimal(grading.path("reportScore").asText("0"));
            boolean blank = !answered(answer);
            if (blank) {
                mapper.gradeAnswer(item.getAnswerId(), "0", itemMax.toPlainString(), "0", "AUTO_SKIP",
                    json(Map.of("status", "BLANK")));
                mapper.gradeAttempt(item.getAttemptId(), true, false);
                continue;
            }
            if ("CHOICE".equals(questionType)) {
                BigDecimal score = labels(answer.path("value")).equals(labels(grading.path("answer").path("value")))
                    ? itemMax : BigDecimal.ZERO;
                BigDecimal rate = score.signum() == 0 ? BigDecimal.ZERO : BigDecimal.ONE;
                mapper.gradeAnswer(item.getAnswerId(), score.toPlainString(), itemMax.toPlainString(), rate.toPlainString(),
                    "AUTO_CHOICE", json(Map.of("status", "GRADED")));
                mapper.gradeAttempt(item.getAttemptId(), false, true);
                settleEvidence(session, item, rate, job.getBusinessKey());
                continue;
            }
            SubjectiveGradingTasks.TaskState state = subjectiveTasks.ensure(item.getQuestionRevisionId(),
                item.getSessionQuestionId(), item.getAttemptId(), item.getPresentationSnapshot(),
                item.getGradingSnapshot(), item.getKnowledgeSnapshot(), answer.path("value").asText());
            if (Set.of("RUBRIC_PENDING", "PENDING", "PROCESSING").contains(state.status())) {
                waiting = true;
                continue;
            }
            if ("FAILED".equals(state.status())) {
                mapper.failAnswer(item.getAnswerId(), itemMax.toPlainString(),
                    json(Map.of("errorCode", state.errorCode() == null ? "AI_GRADING_FAILED" : state.errorCode())));
                mapper.gradeAttempt(item.getAttemptId(), false, false);
                continue;
            }
            SubjectiveAnswerValidator.GradingResult result = SubjectiveAnswerValidator.gradingResult(
                tree(state.rubric(), session.getSessionType()), tree(state.result(), session.getSessionType()));
            if (result == null) {
                mapper.failAnswer(item.getAnswerId(), itemMax.toPlainString(), json(Map.of("errorCode", "AI_OUTPUT_INVALID")));
                mapper.gradeAttempt(item.getAttemptId(), false, false);
                continue;
            }
            BigDecimal score = itemMax.multiply(result.scoreRate()).setScale(2, RoundingMode.HALF_UP);
            mapper.gradeAnswer(item.getAnswerId(), score.toPlainString(), itemMax.toPlainString(),
                result.scoreRate().toPlainString(), "AI", state.result());
            mapper.gradeAttempt(item.getAttemptId(), false, true);
            settleEvidence(session, item, result.scoreRate(), job.getBusinessKey());
        }
        if (waiting) return false;
        List<FormalExamItemRow> completedItems = mapper.selectItems(session.getUserId(), sessionId);
        BigDecimal total = completedItems.stream().filter(item -> !"failed".equals(item.getGradingStatus()))
            .map(item -> decimal(item.getScore())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal max = completedItems.stream().map(item -> decimal(item.getMaxScore()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (completedItems.stream().anyMatch(item -> Boolean.TRUE.equals(item.getProfileApplied()))) {
            mapper.aggregateSubjectProfiles(session.getUserId(), session.getGoalId(), session.getRuleVersionId());
            mapper.aggregateOverallProfile(session.getUserId(), session.getGoalId(), session.getRuleVersionId());
        }
        mapper.insertReport(IdUtil.getSnowflakeNextId(), session, session.getSessionType(), total.toPlainString(),
            max.toPlainString(), versioned(Schema.SUBJECT_SCORES, Map.of("items", List.of())),
            versioned(Schema.PROFILE_SUMMARY, Map.of("profileApplied", true)));
        mapper.markCompleted(sessionId);
        return true;
    }

    private void settleEvidence(FormalExamSessionRow session, FormalExamItemRow item, BigDecimal rate, String request) {
        boolean repeat = mapper.countRecentEvidence(session.getUserId(), session.getGoalId(), item.getEvidenceGroupKey()) > 0;
        String coefficient = repeat ? "0.03" : "0.25";
        String direction = FormalExamScoringRules.direction(rate);
        for (JsonNode leaf : tree(item.getKnowledgeSnapshot(), session.getSessionType()).path("items")) {
            long leafId = Long.parseLong(leaf.path("knowledgePointId").asText());
            if (FormalExamScoringRules.isMistake(rate)) {
                mapper.insertErrorRecord(IdUtil.getSnowflakeNextId(), session.getUserId(), session.getGoalId(),
                    item.getAttemptId(), leafId, item.getEvidenceGroupKey());
            }
            Long settlementId = mapper.insertSettlement(IdUtil.getSnowflakeNextId(), session.getUserId(), session.getGoalId(),
                session.getId(), item.getAttemptId(), item.getExamSubjectId(), leafId, coefficient,
                session.getRuleVersionId(), request + '-' + item.getAttemptId() + '-' + leafId,
                rate.toPlainString(), direction);
            mapper.insertEvidence(IdUtil.getSnowflakeNextId(), settlementId, session.getUserId(), session.getGoalId(),
                session.getId(), item.getAttemptId(), item.getExamSubjectId(), leafId, item.getEvidenceGroupKey(),
                session.getSessionType(), item.getDifficulty(), rate.toPlainString(), direction, coefficient, !repeat,
                session.getRuleVersionId(), item.getElapsedSeconds(), item.getTimerStatus());
            mapper.upsertKnowledgeProfile(IdUtil.getSnowflakeNextId(), session.getUserId(), session.getGoalId(), leafId,
                rate.toPlainString(), direction, coefficient, session.getRuleVersionId());
            mapper.updateSettlementFinal(settlementId);
        }
    }

    private void finishLocked(FormalExamSessionRow session, Long expectedVersion) {
        if (mapper.markSubmitted(session.getId(), expectedVersion) != 1) {
            if (expectedVersion != null) throw version(session.getSessionType());
            return;
        }
        mapper.submitAttempts(session.getId());
        mapper.markSettling(session.getId());
        mapper.insertJob(IdUtil.getSnowflakeNextId(), jobKey(session),
            versioned(Schema.RESULT_JOB, Map.of("sessionId", String.valueOf(session.getId()), "sessionType", session.getSessionType())));
    }

    private FormalExamSessionVo sessionVo(FormalExamSessionRow session) {
        List<FormalExamItemRow> items = mapper.selectItems(session.getUserId(), session.getId());
        int answered = (int) items.stream().filter(item -> answered(tree(item.getAnswerData(), session.getSessionType()))).count();
        List<FormalExamSessionVo.NavigationVo> navigation = items.stream().map(item ->
            new FormalExamSessionVo.NavigationVo(item.getQuestionOrder(),
                item.getQuestionOrder().equals(session.getLastQuestionOrder()) ? "CURRENT" :
                    answered(tree(item.getAnswerData(), session.getSessionType())) ? "ANSWERED" : "UNANSWERED")).toList();
        FormalExamTimerLeaseRow activeLease = mapper.selectTimerLease(session.getId());
        FormalExamSessionVo.LeaseVo lease = activeLease == null
            ? new FormalExamSessionVo.LeaseVo(false, null, null, null)
            : new FormalExamSessionVo.LeaseVo(true, activeLease.getQuestionOrder(), activeLease.getLeaseId(),
                activeLease.getLastHeartbeatAt().toString());
        return new FormalExamSessionVo(String.valueOf(session.getId()), kind(session.getSessionType()), session.getTitle(),
            session.getStatus().toUpperCase(), session.getRowVersion(), session.getLastQuestionOrder() == null ? 1 : session.getLastQuestionOrder(),
            items.size(), answered, items.size() - answered, session.getDurationSecondsSnapshot(),
            items.stream().map(FormalExamItemRow::getElapsedSeconds).filter(Objects::nonNull).mapToInt(Integer::intValue).sum(),
            OffsetDateTime.now().toString(), lease, navigation, returnPath(session.getSessionType()));
    }

    private FormalExamItemVo itemVo(FormalExamSessionRow session, FormalExamItemRow item) {
        JsonNode presentation = tree(item.getPresentationSnapshot(), session.getSessionType());
        JsonNode answer = tree(item.getAnswerData(), session.getSessionType());
        List<FormalExamItemVo.OptionVo> options = new ArrayList<>();
        for (JsonNode option : presentation.path("options")) {
            options.add(new FormalExamItemVo.OptionVo(option.path("label").asText(), option.path("content").asText()));
        }
        List<FormalExamItemVo.ImageVo> images = new ArrayList<>();
        for (JsonNode image : presentation.path("images")) {
            images.add(new FormalExamItemVo.ImageVo(image.path("sortOrder").asInt(),
                imageUrlService.accessUrl(image.path("sourceUrl").asText(null), image.path("storagePath").asText(null)),
                image.path("altText").asText(null)));
        }
        String type = presentation.path("questionType").asText();
        return new FormalExamItemVo(item.getQuestionOrder(), session.getTotalCount(), type, presentation.path("stem").asText(),
            "CHOICE".equals(type) ? "single" : null, options, images,
            "CHOICE".equals(type) ? labels(answer.path("value")) : List.of(),
            "CHOICE".equals(type) ? null : answer.path("value").asText(null), session.getRowVersion(),
            answered(answer) ? item.getGradingStatus() : null);
    }

    private FormalExamResultVo.ItemResultVo resultItem(FormalExamItemRow item, String sessionType) {
        JsonNode presentation = tree(item.getPresentationSnapshot(), sessionType);
        JsonNode grading = tree(item.getGradingSnapshot(), sessionType);
        JsonNode answer = tree(item.getAnswerData(), sessionType);
        String type = presentation.path("questionType").asText();
        BigDecimal rate = decimal(item.getScoreRate());
        boolean blank = !answered(answer);
        Boolean correct = "CHOICE".equals(type) ? rate.compareTo(BigDecimal.ONE) == 0 : null;
        boolean disclose = !"failed".equals(item.getGradingStatus());
        return new FormalExamResultVo.ItemResultVo(item.getQuestionOrder(), type, presentation.path("stem").asText(),
            "CHOICE".equals(type) ? options(presentation) : List.of(),
            resultImages(presentation),
            "CHOICE".equals(type) ? labels(answer.path("value")) : List.of(),
            "CHOICE".equals(type) ? null : answer.path("value").asText(null), blank, correct,
            disclose ? item.getScore() : null, item.getMaxScore(), disclose && "CHOICE".equals(type) ? labels(grading.path("answer").path("value")) : List.of(),
            disclose && !"CHOICE".equals(type) ? grading.path("answer").path("value").asText(null) : null,
            disclose ? ("CHOICE".equals(type) ? grading.path("analysis").asText(null) : feedback(item)) : null,
            Boolean.TRUE.equals(item.getProfileApplied()), item.getGradingSource(), item.getGradingRevisionNo() == null ? 1 : item.getGradingRevisionNo(),
            item.getGradingStatus(), gradingItems(item));
    }

    private List<FormalExamResultVo.OptionVo> options(JsonNode presentation) {
        List<FormalExamResultVo.OptionVo> options = new ArrayList<>();
        for (JsonNode option : presentation.path("options")) {
            options.add(new FormalExamResultVo.OptionVo(option.path("label").asText(), option.path("content").asText()));
        }
        return List.copyOf(options);
    }

    private List<FormalExamResultVo.ImageVo> resultImages(JsonNode presentation) {
        List<FormalExamResultVo.ImageVo> images = new ArrayList<>();
        for (JsonNode image : presentation.path("images")) {
            images.add(new FormalExamResultVo.ImageVo(image.path("sortOrder").asInt(),
                imageUrlService.accessUrl(image.path("sourceUrl").asText(null), image.path("storagePath").asText(null)),
                image.path("altText").asText(null)));
        }
        return List.copyOf(images);
    }

    private String feedback(FormalExamItemRow item) {
        return tree(item.getGradingResult(), "simulation").path("feedback").asText(null);
    }

    private List<FormalExamResultVo.GradingItemVo> gradingItems(FormalExamItemRow item) {
        if (!"graded".equals(item.getGradingStatus()) || !"AI".equals(item.getGradingSource())) return List.of();
        List<FormalExamResultVo.GradingItemVo> values = new ArrayList<>();
        for (JsonNode result : tree(item.getGradingResult(), "simulation").path("itemResults")) {
            values.add(new FormalExamResultVo.GradingItemVo(result.path("code").asText(), result.path("scoreRate").asText()));
        }
        return List.copyOf(values);
    }

    private FormalExamTimerEventVo timingVo(FormalExamSessionRow session, int order, boolean accepted, String leaseId, String state) {
        int elapsed = effectiveElapsed(session.getUserId(), session.getId());
        return new FormalExamTimerEventVo(accepted, leaseId, order, state, session.getDurationSecondsSnapshot(), elapsed,
            OffsetDateTime.now().toString());
    }

    private int effectiveElapsed(long userId, long sessionId) {
        return mapper.selectItems(userId, sessionId).stream().map(FormalExamItemRow::getElapsedSeconds)
            .filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }

    private void closeActiveTimer(FormalExamSessionRow session) {
        FormalExamTimerLeaseRow lease = mapper.lockTimerLease(session.getId());
        if (lease != null) closeLease(session, lease, OffsetDateTime.now(), "会话动作停止计时");
    }

    private void closeLease(FormalExamSessionRow session, FormalExamTimerLeaseRow lease,
                            OffsetDateTime now, String reason) {
        if (expired(lease, now)) mapper.markTimerInvalid(lease.getAttemptId(), reason + "前心跳已超时");
        else accumulate(session, lease, now);
        mapper.deleteTimerLease(lease.getSessionId());
    }

    private void accumulate(FormalExamSessionRow session, FormalExamTimerLeaseRow lease, OffsetDateTime now) {
        int requested = (int) Math.max(0, Duration.between(lease.getLastHeartbeatAt(), now).getSeconds());
        int remaining = Math.max(0, session.getDurationSecondsSnapshot()
            - effectiveElapsed(session.getUserId(), session.getId()));
        mapper.accumulateTimer(lease.getAttemptId(), Math.min(requested, remaining));
    }

    private boolean expired(FormalExamTimerLeaseRow lease, OffsetDateTime now) {
        return Duration.between(lease.getLastHeartbeatAt(), now).compareTo(HEARTBEAT_EXPIRY) > 0;
    }

    private Snapshots snapshots(PastPaperQuestionRow question) {
        Map<String, Object> presentation = new LinkedHashMap<>();
        presentation.put("questionType", question.getQuestionType());
        presentation.put("stem", question.getStem());
        presentation.put("options", tree(question.getOptionsJson(), "simulation"));
        presentation.put("images", tree(question.getImagesJson(), "simulation"));
        Map<String, Object> grading = new LinkedHashMap<>();
        grading.put("answer", tree(question.getAnswerJson(), "simulation"));
        grading.put("analysis", question.getAnalysis() == null ? "" : question.getAnalysis());
        grading.put("reportScore", question.getReportScore());
        return new Snapshots(versioned(Schema.PRESENTATION, presentation), versioned(Schema.GRADING, grading),
            versioned(Schema.KNOWLEDGE, Map.of("items", tree(question.getKnowledgeJson(), "simulation"))));
    }

    private void validateQuestions(String sessionType, List<PastPaperQuestionRow> questions) {
        if (questions.isEmpty()) throw failure(sessionType, 422, code(sessionType, "UNAVAILABLE"), "试卷为空", false);
        int expected = 1;
        for (PastPaperQuestionRow question : questions) {
            if (question.getQuestionOrder() == null || question.getQuestionOrder() != expected++
                || !List.of("CHOICE", "CASE", "ESSAY").contains(question.getQuestionType())) {
                throw failure(sessionType, 422, code(sessionType, "UNAVAILABLE"), "试卷题序或题型无效", false);
            }
            if (question.getQuestionId() == null || question.getQuestionId() < 1
                || question.getQuestionRevisionId() == null || question.getQuestionRevisionId() < 1
                || question.getExamSubjectId() == null || question.getExamSubjectId() < 1
                || question.getStem() == null || question.getStem().isBlank()
                || question.getEstimatedSeconds() == null || question.getEstimatedSeconds() < 1
                || question.getEvidenceGroupKey() == null || question.getEvidenceGroupKey().isBlank()
                || requiredDecimal(question.getReportScore(), sessionType).signum() <= 0) {
                throw failure(sessionType, 422, code(sessionType, "UNAVAILABLE"), "试卷分值无效", false);
            }
            JsonNode options = sourceTree(question.getOptionsJson(), sessionType);
            JsonNode answer = sourceTree(question.getAnswerJson(), sessionType);
            JsonNode images = sourceTree(question.getImagesJson(), sessionType);
            JsonNode knowledge = sourceTree(question.getKnowledgeJson(), sessionType);
            if (!images.isArray() || !knowledge.isArray() || knowledge.isEmpty()) unavailable(sessionType, "试卷冻结数据不完整");
            for (JsonNode leaf : knowledge) {
                if (leaf.path("knowledgePointId").asLong(0) < 1
                    || leaf.path("examSubjectId").asLong(0) != question.getExamSubjectId()) {
                    unavailable(sessionType, "试卷知识点快照无效");
                }
            }
            if ("CHOICE".equals(question.getQuestionType())) {
                if (!options.isArray() || options.size() < 2 || !answer.path("value").isArray()
                    || answer.path("value").size() != 1) unavailable(sessionType, "选择题冻结数据无效");
                Set<String> labels = new HashSet<>();
                for (JsonNode option : options) {
                    String label = option.path("label").asText();
                    if (label.isBlank() || !labels.add(label) || option.path("content").asText().isBlank()) {
                        unavailable(sessionType, "选择题选项无效");
                    }
                }
                if (!labels.contains(answer.path("value").get(0).asText())) {
                    unavailable(sessionType, "选择题答案无效");
                }
            } else if (!answer.path("value").isTextual() || answer.path("value").asText().isBlank()) {
                unavailable(sessionType, "主观题参考答案无效");
            }
        }
    }

    private JsonNode sourceTree(String value, String sessionType) {
        try {
            return value == null ? jsonMapper.createObjectNode() : jsonMapper.readTree(value);
        } catch (Exception exception) {
            throw failure(sessionType, 422, code(sessionType, "UNAVAILABLE"), "试卷冻结数据损坏", false);
        }
    }

    private BigDecimal requiredDecimal(String value, String sessionType) {
        try {
            return new BigDecimal(value);
        } catch (Exception exception) {
            throw failure(sessionType, 422, code(sessionType, "UNAVAILABLE"), "试卷分值无效", false);
        }
    }

    private void unavailable(String sessionType, String message) {
        throw failure(sessionType, 422, code(sessionType, "UNAVAILABLE"), message, false);
    }

    private String normalizeDraft(FormalExamItemRow item, FormalExamDraftBo command, String sessionType) {
        String type = tree(item.getPresentationSnapshot(), sessionType).path("questionType").asText();
        if ("CHOICE".equals(type)) {
            List<String> value = command.getAnswer().getChoiceValue();
            if (value == null || value.size() > 1 || command.getAnswer().getTextValue() != null) {
                throw failure(sessionType, 422, code(sessionType, "ANSWER_INVALID"), "选择题答案无效", false);
            }
            if (!value.isEmpty()) {
                boolean exists = false;
                for (JsonNode option : tree(item.getPresentationSnapshot(), sessionType).path("options")) {
                    if (value.getFirst().equals(option.path("label").asText())) exists = true;
                }
                if (!exists) throw failure(sessionType, 422, code(sessionType, "ANSWER_INVALID"), "答案不属于冻结选项", false);
            }
            return versioned(Schema.CHOICE_ANSWER, Map.of("answer_type", "option_keys", "selection_mode", "single", "value", value));
        }
        String text = command.getAnswer().getTextValue();
        if (command.getAnswer().getChoiceValue() != null || text != null && text.length() > 50000) {
            throw failure(sessionType, 422, code(sessionType, "ANSWER_INVALID"), "主观题答案无效", false);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("answer_type", "text");
        fields.put("value", text == null || text.isBlank() ? null : text);
        return versioned(Schema.TEXT_ANSWER, fields);
    }

    private String emptyAnswer(String questionType) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if ("CHOICE".equals(questionType)) {
            fields.put("answer_type", "option_keys"); fields.put("selection_mode", "single"); fields.put("value", List.of());
            return versioned(Schema.CHOICE_ANSWER, fields);
        }
        fields.put("answer_type", "text"); fields.put("value", null);
        return versioned(Schema.TEXT_ANSWER, fields);
    }

    private FormalExamSessionRow requireSession(long userId, long sessionId, String sessionType) {
        FormalExamSessionRow session = mapper.selectSession(userId, positive(sessionId, sessionType), sessionType);
        if (session == null) throw failure(sessionType, 404, code(sessionType, "SESSION_NOT_FOUND"), "考试会话不存在", false);
        return session;
    }

    private FormalExamSessionRow lock(long userId, long sessionId, String sessionType) {
        FormalExamSessionRow session = mapper.lockSession(userId, positive(sessionId, sessionType), sessionType);
        if (session == null) throw failure(sessionType, 404, code(sessionType, "SESSION_NOT_FOUND"), "考试会话不存在", false);
        return session;
    }

    private void editable(FormalExamSessionRow session, String sessionType) {
        if (!"in_progress".equals(session.getStatus())) throw failure(sessionType, 409, code(sessionType, "SESSION_NOT_ACTIVE"), "考试会话不可编辑", false);
    }

    private StartResult startResult(FormalExamSessionRow session, boolean resumed) {
        return new StartResult(String.valueOf(session.getId()), String.valueOf(session.getCollectionRevisionId()), resumed,
            session.getFormalAttemptNo(), session.getStartedTime().toString(), session.getDurationSecondsSnapshot(),
            session.getTotalCount(), "in_progress".equals(session.getStatus())
                ? answerPath(session.getSessionType(), session.getId()) : resultPath(session.getSessionType(), session.getId()));
    }

    private FormalExamFinishVo finishVo(FormalExamSessionRow session) {
        return new FormalExamFinishVo(String.valueOf(session.getId()), "PROCESSING", "WAIT_PROCESSING", resultPath(session.getSessionType(), session.getId()));
    }

    private FormalExamStatusVo statusVo(FormalExamSessionRow session, String job) {
        String status = "failed".equals(job) ? "FAILED" : "completed".equals(session.getStatus()) && "succeeded".equals(job) ? "COMPLETED" : "PROCESSING";
        List<FormalExamStatusVo.StageVo> stages = List.of(
            new FormalExamStatusVo.StageVo("SUBMITTED", "in_progress".equals(session.getStatus()) ? "PENDING" : "COMPLETED"),
            new FormalExamStatusVo.StageVo("SCORING", "completed".equals(session.getStatus()) ? "COMPLETED" : "failed".equals(job) ? "FAILED" : "RUNNING"),
            new FormalExamStatusVo.StageVo("REPORT_GENERATING", "completed".equals(session.getStatus()) ? "COMPLETED" : "PENDING"),
            new FormalExamStatusVo.StageVo("PROFILE_UPDATING", "completed".equals(session.getStatus()) ? "COMPLETED" : "PENDING"),
            new FormalExamStatusVo.StageVo("COMPLETED", "completed".equals(session.getStatus()) ? "COMPLETED" : "PENDING"));
        FormalExamStatusVo.FailureVo failure = "FAILED".equals(status) ? new FormalExamStatusVo.FailureVo("结果生成失败", null, true) : null;
        return new FormalExamStatusVo(String.valueOf(session.getId()), status,
            "FAILED".equals(status) ? "RETRY_RESULT" : "COMPLETED".equals(status) ? "VIEW_RESULT" : "WAIT_PROCESSING", stages, failure);
    }

    private String jobKey(FormalExamSessionRow session) { return "FORMAL_EXAM:" + session.getId(); }
    private String answerPath(String sessionType, long sessionId) { return "/learning/question-bank/" + (isPast(sessionType) ? "past-papers/exam" : "mock-exams/exam") + "?sessionId=" + sessionId; }
    private String resultPath(String sessionType, long sessionId) { return "/learning/question-bank/" + (isPast(sessionType) ? "past-papers/result" : "mock-exams/result") + "?sessionId=" + sessionId; }
    private String returnPath(String sessionType) { return "/learning/question-bank/" + (isPast(sessionType) ? "past-papers" : "mock-exams"); }
    private String kind(String sessionType) { return isPast(sessionType) ? "PAST_PAPER_EXAM" : "SIMULATION"; }
    private boolean isPast(String sessionType) { return "past_paper_exam".equals(sessionType); }
    private String action(String sessionType, String suffix) { return (isPast(sessionType) ? "PAST_PAPER_EXAM_" : "SIMULATION_") + suffix; }
    private String code(String sessionType, String suffix) { return (isPast(sessionType) ? "PAST_PAPER_EXAM_" : "SIMULATION_") + suffix; }
    private long positive(long value, String sessionType) { if (value < 1) throw failure(sessionType, 400, code(sessionType, "REQUEST_INVALID"), "ID必须大于0", false); return value; }
    private int positive(int value, String sessionType) { if (value < 1) throw failure(sessionType, 400, code(sessionType, "REQUEST_INVALID"), "题序必须大于0", false); return value; }
    private RuntimeException version(String sessionType) { return failure(sessionType, 409, code(sessionType, "SESSION_VERSION_CONFLICT"), "会话版本已变化", true); }
    private RuntimeException failure(String sessionType, int status, String errorCode, String message, boolean retryable) {
        return isPast(sessionType) ? new PastPaperException(status, errorCode, message, retryable)
            : new SimulationException(status, errorCode, message, retryable);
    }

    private String requestId(String requestId, String sessionType) {
        try {
            if (requestId == null || !requestId.equals(requestId.trim()) || requestId.length() != 36) throw new IllegalArgumentException();
            return UUID.fromString(requestId).toString();
        } catch (Exception exception) {
            throw failure(sessionType, 400, code(sessionType, "REQUEST_INVALID"), "请求号必须是UUID", false);
        }
    }

    private String attemptRequestId(String startRequestId, int questionOrder) {
        return UUID.nameUUIDFromBytes((startRequestId + ":attempt:" + questionOrder)
            .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private <T> T replay(PastPaperIdempotencyRow row, String payloadHash, Class<T> type, String sessionType) {
        if (row == null || !payloadHash.equals(row.getPayloadHash()) || !"succeeded".equals(row.getStatus())) {
            throw failure(sessionType, 409, code(sessionType, "IDEMPOTENCY_CONFLICT"), "请求号已被使用", false);
        }
        try { return jsonMapper.treeToValue(tree(row.getResponseBody(), sessionType).path("response").path("data"), type); }
        catch (Exception exception) { throw failure(sessionType, 500, code(sessionType, "SYSTEM_FAILURE"), "响应回放失败", true); }
    }

    private boolean answered(JsonNode answer) {
        JsonNode value = answer.path("value");
        return value.isArray() ? !value.isEmpty() : value.isTextual() && !value.asText().isBlank();
    }
    private List<String> labels(JsonNode node) { List<String> values = new ArrayList<>(); if (node.isArray()) for (JsonNode item : node) values.add(item.asText()); return values; }
    private BigDecimal decimal(String value) { try { return value == null ? BigDecimal.ZERO : new BigDecimal(value); } catch (Exception exception) { return BigDecimal.ZERO; } }
    private JsonNode tree(String value, String sessionType) { try { return value == null ? jsonMapper.createObjectNode() : jsonMapper.readTree(value); } catch (Exception exception) { throw failure(sessionType, 500, code(sessionType, "SYSTEM_FAILURE"), "冻结数据损坏", true); } }
    private String versioned(Schema schema, Map<String, ?> fields) { return VersionedJsonDocumentFactory.json(jsonMapper, schema, fields); }
    private String json(Object value) { try { return jsonMapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("formal exam grading serialization failed", exception); } }
    private String response(Object data) { return versioned(Schema.IDEMPOTENCY, Map.of("response", Map.of("code", 200, "msg", "操作成功", "data", data))); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private record Snapshots(String presentation, String grading, String knowledge) { }
    private enum Schema implements JsonSchemaVersion {
        PRESENTATION("formal_exam_presentation/1.0"), GRADING("formal_exam_grading/1.0"),
        KNOWLEDGE("formal_exam_knowledge/1.0"), CHOICE_ANSWER("formal_exam_choice_answer/1.0"),
        TEXT_ANSWER("formal_exam_text_answer/1.0"), IDEMPOTENCY("formal_exam_idempotency/1.0"),
        RESULT_JOB("formal_exam_result/1.0"), SUBJECT_SCORES("formal_exam_subject_scores/1.0"),
        PROFILE_SUMMARY("formal_exam_profile_summary/1.0");
        private final String version;
        Schema(String version) { this.version = version; }
        @Override public String version() { return version; }
    }
}
