package org.dromara.certmuse.assessment.service.impl;

import cn.hutool.core.util.IdUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeGoalRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeAnsweringSessionRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeItemRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeIdempotencyRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeNodeRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeQuestionRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeSessionRow;
import org.dromara.certmuse.assessment.domain.ReinforcementRoundRow;
import org.dromara.certmuse.assessment.domain.bo.StartKnowledgePracticeBo;
import org.dromara.certmuse.assessment.domain.bo.SubmitKnowledgePracticeItemBo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeErrorVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeMasteryVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeNodeVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeSetupVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeSessionVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeItemVo;
import org.dromara.certmuse.assessment.domain.vo.SubmitKnowledgePracticeItemVo;
import org.dromara.certmuse.assessment.domain.vo.CompleteKnowledgePracticeVo;
import org.dromara.certmuse.assessment.domain.vo.StartKnowledgePracticeVo;
import org.dromara.certmuse.assessment.domain.vo.ReinforcementSuggestionVo;
import org.dromara.certmuse.assessment.domain.vo.ReinforcementRoundVo;
import org.dromara.certmuse.assessment.domain.vo.ReinforcementResultVo;
import org.dromara.certmuse.assessment.mapper.KnowledgePracticeMapper;
import org.dromara.certmuse.assessment.service.KnowledgePracticeService;
import org.dromara.certmuse.assessment.service.ChoiceAnswerSettlementService;
import org.dromara.certmuse.assessment.support.AssessmentJsonSchema;
import org.dromara.certmuse.assessment.support.KnowledgePracticeLegacyIdempotencyPayloads;
import org.dromara.certmuse.assessment.support.ReinforcementRecommendationGenerator;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.dromara.certmuse.assessment.support.KnowledgePracticeException;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Transactional implementation of the U08 setup and session-freeze contract. */
@Slf4j
@Service
public class KnowledgePracticeServiceImpl implements KnowledgePracticeService {
    private static final int MAX_QUESTIONS = 150;
    private final KnowledgePracticeMapper mapper;
    private final JsonMapper jsonMapper;
    private final QuestionImageUrlService imageUrlService;
    private final ChoiceAnswerSettlementService answerSettlementService;
    private final ReinforcementRecommendationGenerator reinforcementRecommendationGenerator;
    private final TransactionTemplate transactionTemplate;

    public KnowledgePracticeServiceImpl(KnowledgePracticeMapper mapper, JsonMapper jsonMapper,
                                        QuestionImageUrlService imageUrlService,
                                        ChoiceAnswerSettlementService answerSettlementService,
                                        ReinforcementRecommendationGenerator reinforcementRecommendationGenerator,
                                        TransactionTemplate transactionTemplate) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.imageUrlService = imageUrlService;
        this.answerSettlementService = answerSettlementService;
        this.reinforcementRecommendationGenerator = reinforcementRecommendationGenerator;
        this.transactionTemplate = transactionTemplate;
    }


    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public KnowledgePracticeSetupVo setup(long userId) {
        KnowledgePracticeGoalRow goal = requireGoal(userId);
        requireDirectory(goal);
        List<KnowledgePracticeNodeRow> rows = mapper.selectVisibleNodes(userId, goal.getId(), goal.getSyllabusVersionId());
        return setupVo(goal, rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StartKnowledgePracticeVo start(long userId, String requestId, StartKnowledgePracticeBo command) {
        String normalizedRequestId = normalizeRequestId(requestId);
        long knowledgePointId = parseId(command.getKnowledgePointId());
        PayloadHashes payloadHashes = startHashes(userId, knowledgePointId, command.getExpectedGoalVersion());
        String payloadHash = payloadHashes.canonical();
        KnowledgePracticeIdempotencyRow existing = mapper.selectIdempotency(normalizedRequestId);
        if (existing != null) {
            return replay(existing, payloadHashes, userId);
        }

        KnowledgePracticeGoalRow goal = requireGoal(userId);
        if (!Objects.equals(goal.getRowVersion(), command.getExpectedGoalVersion())) {
            throw failure(409, "PRACTICE_GOAL_VERSION_CONFLICT", "当前学习目标已变化", true);
        }
        requireDirectory(goal);
        KnowledgePracticeNodeRow node = mapper.selectPracticeNode(
            userId, goal.getId(), goal.getSyllabusVersionId(), knowledgePointId);
        if (node == null) {
            throw failure(422, "PRACTICE_NODE_UNAVAILABLE", "当前知识点不可练习", false);
        }
        List<KnowledgePracticeQuestionRow> questions = mapper.selectQualifiedQuestions(
            goal.getSyllabusVersionId(), knowledgePointId);
        if (questions.isEmpty()) {
            throw failure(422, "PRACTICE_NODE_UNAVAILABLE", "当前知识点暂无可练题目", false);
        }
        if (questions.size() > MAX_QUESTIONS) {
            throw failure(422, "PRACTICE_QUESTION_LIMIT_EXCEEDED", "当前知识点题量超出首版上限", false);
        }
        Long ruleVersionId = mapper.selectPublishedRuleVersion();
        if (ruleVersionId == null) {
            throw failure(503, "PRACTICE_RULE_VERSION_UNAVAILABLE", "练习规则暂不可用", true);
        }

        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, normalizedRequestId, payloadHash) == 0) {
            KnowledgePracticeIdempotencyRow concurrent = mapper.selectIdempotency(normalizedRequestId);
            if (concurrent == null) {
                throw failure(409, "PRACTICE_IDEMPOTENCY_CONFLICT", "请求号已被占用", false);
            }
            return replay(concurrent, payloadHashes, userId);
        }

        long sessionId = IdUtil.getSnowflakeNextId();
        if (mapper.insertSession(sessionId, userId, goal, node.getExamSubjectId(), ruleVersionId, normalizedRequestId) == 0) {
            KnowledgePracticeSessionRow sameRequest = mapper.selectSessionByRequestId(normalizedRequestId);
            if (sameRequest != null) {
                throw failure(409, "PRACTICE_IDEMPOTENCY_CONFLICT", "请求号已被使用", false);
            }
            throw activeSession(userId, goal.getId());
        }

        int order = 1;
        for (KnowledgePracticeQuestionRow question : questions) {
            Snapshots snapshots = snapshots(question);
            mapper.insertSessionQuestion(IdUtil.getSnowflakeNextId(), sessionId, question, order++,
                snapshots.presentation(), snapshots.grading(), snapshots.knowledge());
        }
        StartKnowledgePracticeVo result = result(sessionId, questions.size());
        mapper.completeIdempotency(idempotencyId, sessionId, response(result));
        log.info("Knowledge practice created, requestId={}, goalId={}, knowledgePointId={}, questionCount={}, sessionId={}",
            normalizedRequestId, goal.getId(), knowledgePointId, questions.size(), sessionId);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgePracticeSessionVo session(long userId, long sessionId) {
        requirePositive(sessionId, "sessionId");
        KnowledgePracticeAnsweringSessionRow row = mapper.selectAnsweringSession(sessionId, userId);
        if (row == null) throw failure(404, "PRACTICE_SESSION_NOT_FOUND", "练习会话不存在", false);
        if ("created".equals(row.getStatus())) {
            mapper.startAnsweringSession(sessionId, userId);
            row = mapper.selectAnsweringSession(sessionId, userId);
        }
        if ("completed".equals(row.getStatus())) {
            return new KnowledgePracticeSessionVo(String.valueOf(sessionId), "COMPLETED", row.getTotalCount(),
                row.getSubmittedCount(), List.of(), "RETURN_KNOWLEDGE_PRACTICE", returnPath());
        }
        if (!"in_progress".equals(row.getStatus())) {
            throw failure(409, "PRACTICE_SESSION_NOT_ACTIVE", "练习会话当前不可答题", false);
        }
        List<KnowledgePracticeSessionVo.NavigationItemVo> navigation = mapper.selectNavigation(sessionId).stream()
            .map(item -> new KnowledgePracticeSessionVo.NavigationItemVo(item.getQuestionOrder(),
                navigationState(item))).toList();
        return new KnowledgePracticeSessionVo(String.valueOf(sessionId), "IN_PROGRESS", row.getTotalCount(),
            row.getSubmittedCount(), navigation, "CONTINUE_PRACTICE", null);
    }

    private String navigationState(org.dromara.certmuse.assessment.domain.KnowledgePracticeNavigationRow item) {
        if (!Boolean.TRUE.equals(item.getSubmitted())) return "UNANSWERED";
        return Boolean.TRUE.equals(item.getCorrect()) ? "SUBMITTED_CORRECT" : "SUBMITTED_INCORRECT";
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgePracticeItemVo item(long userId, long sessionId, int questionOrder) {
        requirePositive(sessionId, "sessionId");
        requirePositive(questionOrder, "questionOrder");
        KnowledgePracticeAnsweringSessionRow session = mapper.selectAnsweringSession(sessionId, userId);
        if (session == null) throw failure(404, "PRACTICE_SESSION_NOT_FOUND", "练习会话不存在", false);
        if (!"in_progress".equals(session.getStatus())) {
            throw failure(409, "PRACTICE_SESSION_NOT_ACTIVE", "练习会话当前不可答题", false);
        }
        KnowledgePracticeItemRow row = mapper.selectAnsweringItem(sessionId, userId, questionOrder);
        if (row == null) throw invalid("questionOrder", "OUT_OF_RANGE", "题序超出范围");
        try {
            JsonNode presentation = jsonMapper.readTree(row.getPresentationSnapshot());
            JsonNode grading = jsonMapper.readTree(row.getGradingSnapshot());
            List<KnowledgePracticeItemVo.OptionVo> options = new ArrayList<>();
            for (JsonNode option : presentation.path("options")) {
                options.add(new KnowledgePracticeItemVo.OptionVo(option.path("label").asText(), option.path("content").asText()));
            }
            List<KnowledgePracticeItemVo.ImageVo> images = new ArrayList<>();
            for (JsonNode image : presentation.path("images")) {
                String url = imageUrlService.accessUrl(image.path("sourceUrl").asText(null), image.path("storagePath").asText(null));
                images.add(new KnowledgePracticeItemVo.ImageVo(url, image.path("altText").asText(null), image.path("sortOrder").asInt()));
            }
            KnowledgePracticeItemVo.SubmissionVo submission = null;
            if (row.getAttemptId() != null) {
                JsonNode answer = jsonMapper.readTree(row.getAnswerData());
                submission = new KnowledgePracticeItemVo.SubmissionVo(texts(answer.path("value")),
                    Boolean.TRUE.equals(row.getCorrect()), texts(grading.path("answer").path("value")),
                    grading.path("analysis").asText(null));
            }
            return new KnowledgePracticeItemVo(String.valueOf(sessionId), questionOrder, row.getTotalCount(),
                new KnowledgePracticeItemVo.QuestionVo("CHOICE", presentation.path("stem").asText(), "single", options, images), submission);
        } catch (KnowledgePracticeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KnowledgePracticeException(500, "KNOWLEDGE_PRACTICE_ANSWERING_SYSTEM_FAILURE",
                "练习题目读取失败", true, List.of(), null, exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitKnowledgePracticeItemVo submit(long userId, long sessionId, int questionOrder, String requestId,
                                                SubmitKnowledgePracticeItemBo command) {
        requirePositive(sessionId, "sessionId");
        requirePositive(questionOrder, "questionOrder");
        String normalized = normalizeRequestId(requestId);
        String selected = requireSingleAnswer(command);
        String action = "SUBMIT_KNOWLEDGE_PRACTICE_ITEM";
        PayloadHashes payloadHashes = hashes(
            VersionedJsonDocumentFactory.json(jsonMapper,
                VersionedJsonDocumentFactory.IDEMPOTENCY_SCHEMA_VERSION_FIELD,
                AssessmentJsonSchema.KNOWLEDGE_PRACTICE_SUBMIT,
                submitHashFields(userId, sessionId, questionOrder, selected)),
            KnowledgePracticeLegacyIdempotencyPayloads.submit(jsonMapper, userId, sessionId, questionOrder, selected));
        String payloadHash = payloadHashes.canonical();
        KnowledgePracticeIdempotencyRow existing = mapper.selectActionIdempotency(action, normalized);
        if (existing != null) return replaySubmit(existing, payloadHashes, userId);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertActionIdempotency(idempotencyId, action, normalized, payloadHash) == 0) {
            existing = mapper.selectActionIdempotency(action, normalized);
            if (existing == null) throw failure(409, "PRACTICE_IDEMPOTENCY_CONFLICT", "请求号已被其他操作使用", false);
            return replaySubmit(existing, payloadHashes, userId);
        }
        KnowledgePracticeAnsweringSessionRow locked = mapper.lockAnsweringSession(sessionId, userId);
        if (locked == null) throw failure(404, "PRACTICE_SESSION_NOT_FOUND", "练习会话不存在", false);
        if (!"in_progress".equals(locked.getStatus())) {
            throw failure(409, "PRACTICE_SESSION_NOT_ACTIVE", "练习会话当前不可答题", false);
        }
        KnowledgePracticeItemRow item = mapper.selectAnsweringItem(sessionId, userId, questionOrder);
        if (item == null) throw invalid("questionOrder", "OUT_OF_RANGE", "题序超出范围");
        if (item.getAttemptId() != null) {
            throw failure(409, "PRACTICE_ITEM_ALREADY_SUBMITTED", "该题已提交，不能修改", false);
        }
        try {
            JsonNode presentation = jsonMapper.readTree(item.getPresentationSnapshot());
            JsonNode grading = jsonMapper.readTree(item.getGradingSnapshot());
            boolean valid = false;
            for (JsonNode option : presentation.path("options")) {
                if (selected.equals(option.path("label").asText())) { valid = true; break; }
            }
            if (!valid) throw failure(422, "PRACTICE_ANSWER_INVALID", "所选答案不属于当前题目", false);
            List<String> correctLabels = texts(grading.path("answer").path("value"));
            if (correctLabels.size() != 1) throw new IllegalStateException("frozen single-choice answer is invalid");
            boolean correct = selected.equals(correctLabels.getFirst());
            long attemptId = IdUtil.getSnowflakeNextId();
            if (mapper.insertAttempt(attemptId, item.getSessionQuestionId(), userId, normalized) == 0) {
                throw failure(409, "PRACTICE_ITEM_ALREADY_SUBMITTED", "该题已提交，不能修改", false);
            }
        String answerData = VersionedJsonDocumentFactory.json(jsonMapper,
                AssessmentJsonSchema.KNOWLEDGE_PRACTICE_CHOICE_ANSWER,
                Map.of("answer_type", "option_keys", "selection_mode", "single", "value", List.of(selected)));
            mapper.insertAttemptAnswer(IdUtil.getSnowflakeNextId(), attemptId, answerData, correct);
            answerSettlementService.settle(item, attemptId, correct, normalized);
            long reinforcementId = IdUtil.getSnowflakeNextId();
            mapper.insertInitialReinforcement(reinforcementId, userId, sessionId, questionOrder, correct);
            int submittedCount = mapper.countSubmittedItems(sessionId);
            KnowledgePracticeItemVo.SubmissionVo feedback = new KnowledgePracticeItemVo.SubmissionVo(
                List.of(selected), correct, correctLabels, grading.path("analysis").asText(null));
            SubmitKnowledgePracticeItemVo result = new SubmitKnowledgePracticeItemVo(questionOrder, submittedCount, feedback);
            mapper.succeedActionIdempotency(idempotencyId, "cm_question_attempt", attemptId,
                actionResponse(result));
            return result;
        } catch (KnowledgePracticeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KnowledgePracticeException(500, "KNOWLEDGE_PRACTICE_ANSWERING_SYSTEM_FAILURE",
                "练习提交失败", true, List.of(), null, exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompleteKnowledgePracticeVo complete(long userId, long sessionId, String requestId) {
        requirePositive(sessionId, "sessionId");
        String normalized = normalizeRequestId(requestId);
        String action = "COMPLETE_KNOWLEDGE_PRACTICE";
        PayloadHashes payloadHashes = hashes(
            VersionedJsonDocumentFactory.json(jsonMapper,
                VersionedJsonDocumentFactory.IDEMPOTENCY_SCHEMA_VERSION_FIELD,
                AssessmentJsonSchema.KNOWLEDGE_PRACTICE_COMPLETE,
                completeHashFields(userId, sessionId)),
            KnowledgePracticeLegacyIdempotencyPayloads.complete(jsonMapper, userId, sessionId));
        String payloadHash = payloadHashes.canonical();
        KnowledgePracticeIdempotencyRow existing = mapper.selectActionIdempotency(action, normalized);
        if (existing != null) return replayComplete(existing, payloadHashes, userId);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertActionIdempotency(idempotencyId, action, normalized, payloadHash) == 0) {
            existing = mapper.selectActionIdempotency(action, normalized);
            if (existing == null) throw failure(409, "PRACTICE_IDEMPOTENCY_CONFLICT", "请求号已被其他操作使用", false);
            return replayComplete(existing, payloadHashes, userId);
        }
        KnowledgePracticeAnsweringSessionRow locked = mapper.lockAnsweringSession(sessionId, userId);
        if (locked == null) throw failure(404, "PRACTICE_SESSION_NOT_FOUND", "练习会话不存在", false);
        if (!("created".equals(locked.getStatus()) || "in_progress".equals(locked.getStatus()) || "completed".equals(locked.getStatus()))) {
            throw failure(409, "PRACTICE_SESSION_NOT_ACTIVE", "练习会话当前不可结束", false);
        }
        if (!"completed".equals(locked.getStatus())) {
            if (mapper.submitAnsweringSession(sessionId, userId) != 1
                || mapper.settleAnsweringSession(sessionId, userId) != 1
                || mapper.completeAnsweringSession(sessionId, userId) != 1) {
                throw new IllegalStateException("knowledge practice completion transition failed");
            }
        }
        int submittedCount = mapper.countSubmittedItems(sessionId);
        CompleteKnowledgePracticeVo result = new CompleteKnowledgePracticeVo(String.valueOf(sessionId), "COMPLETED",
            submittedCount, returnPath());
        mapper.succeedActionIdempotency(idempotencyId, "cm_learning_session", sessionId,
            actionResponse(result));
        mapper.completeReinforcementRound(sessionId, userId);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ReinforcementSuggestionVo reinforcement(long userId, long sessionId, int questionOrder) {
        ReinforcementRoundRow round = mapper.selectReinforcementBySource(userId, sessionId, questionOrder);
        if (round == null) throw failure(409, "REINFORCEMENT_SOURCE_NOT_SUBMITTED", "来源题尚未提交", false);
        return suggestion(round);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dismissReinforcement(long userId, long sessionId, int questionOrder) {
        ReinforcementRoundRow round = mapper.selectReinforcementBySource(userId, sessionId, questionOrder);
        if (round == null) throw failure(409, "REINFORCEMENT_SOURCE_NOT_SUBMITTED", "来源题尚未提交", false);
        if (mapper.dismissReinforcement(round.getId(), userId) == 0 && !"DISMISSED".equals(round.getStatus())) {
            throw failure(409, "REINFORCEMENT_STATE_CONFLICT", "当前建议不可收起", false);
        }
    }

    @Override
    public ReinforcementRoundVo createReinforcement(long userId, long sessionId, int questionOrder, String requestId) {
        ReinforcementRoundRow round = mapper.selectReinforcementBySource(userId, sessionId, questionOrder);
        if (round == null) throw failure(409, "REINFORCEMENT_SOURCE_NOT_SUBMITTED", "来源题尚未提交", false);
        if ("STARTED".equals(round.getStatus()) || "COMPLETED".equals(round.getStatus())) return roundVo(round);
        if (!"READY".equals(round.getStatus())) {
            throw failure(409, "REINFORCEMENT_NOT_READY", "强化练习当前不可创建", false);
        }
        String normalized = normalizeRequestId(requestId);
        ReinforcementRecommendationGenerator.Recommendation recommendation =
            reinforcementRecommendationGenerator.generate(round);
        return Objects.requireNonNull(transactionTemplate.execute(status ->
            createRound(userId, round.getId(), normalized, recommendation)));
    }

    @Override
    @Transactional(readOnly = true)
    public ReinforcementRoundVo reinforcementRound(long userId, long roundId) {
        ReinforcementRoundRow round = mapper.selectReinforcementRound(roundId, userId);
        if (round == null) throw failure(404, "REINFORCEMENT_ROUND_NOT_FOUND", "强化轮次不存在", false);
        return roundVo(round);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReinforcementRoundVo continueReinforcement(long userId, long roundId, String requestId) {
        ReinforcementRoundRow previous = mapper.lockReinforcementRound(roundId, userId);
        if (previous == null) throw failure(404, "REINFORCEMENT_ROUND_NOT_FOUND", "强化轮次不存在", false);
        if (!"COMPLETED".equals(previous.getStatus())) {
            throw failure(409, "REINFORCEMENT_ROUND_NOT_COMPLETED", "上一轮尚未完成", false);
        }
        String normalized = normalizeRequestId(requestId);
        long nextId = IdUtil.getSnowflakeNextId();
        int inserted = mapper.insertNextReinforcement(nextId, previous, normalized);
        ReinforcementRoundRow next = mapper.selectReinforcementBySource(userId, previous.getSourceSessionId(),
            previous.getSourceQuestionOrder());
        if (inserted == 0 && (next == null || Objects.equals(next.getId(), previous.getId()))) {
            throw failure(409, "REINFORCEMENT_IDEMPOTENCY_CONFLICT", "请求号已被其他强化操作使用", false);
        }
        return createRound(userId, next.getId(), normalized, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ReinforcementResultVo reinforcementResult(long userId, long roundId) {
        ReinforcementRoundRow round = mapper.selectReinforcementRound(roundId, userId);
        if (round == null) throw failure(404, "REINFORCEMENT_ROUND_NOT_FOUND", "强化轮次不存在", false);
        if (!"COMPLETED".equals(round.getStatus())) throw failure(409, "REINFORCEMENT_ROUND_NOT_COMPLETED", "强化轮次尚未完成", false);
        List<org.dromara.certmuse.assessment.domain.KnowledgePracticeNavigationRow> rows = mapper.selectReinforcementResults(roundId);
        int correct = (int) rows.stream().filter(row -> Boolean.TRUE.equals(row.getCorrect())).count();
        boolean more = !mapper.selectReinforcementCandidates(round, round.getSyllabusVersionId()).isEmpty();
        return new ReinforcementResultVo(String.valueOf(roundId), round.getRoundNo(), correct, rows.size(),
            rows.isEmpty() ? 0 : correct * 100 / rows.size(), rows.stream().map(row ->
                new ReinforcementResultVo.ItemVo(row.getQuestionOrder(), Boolean.TRUE.equals(row.getCorrect()))).toList(),
            knowledgePoints(round), more, more ? "CONTINUE_REINFORCEMENT" : "NO_MORE_QUESTIONS",
            "/learning/session/practice?sessionId=" + round.getSourceSessionId()
                + "&questionOrder=" + round.getSourceQuestionOrder());
    }

    private ReinforcementRoundVo createRound(long userId, long roundId, String requestId,
                                             ReinforcementRecommendationGenerator.Recommendation recommendation) {
        ReinforcementRoundRow round = mapper.lockReinforcementRound(roundId, userId);
        if (round == null) throw failure(404, "REINFORCEMENT_ROUND_NOT_FOUND", "强化轮次不存在", false);
        if ("STARTED".equals(round.getStatus()) || "COMPLETED".equals(round.getStatus())) return roundVo(round);
        if (!"READY".equals(round.getStatus())) {
            String code = "UNAVAILABLE".equals(round.getStatus()) ? "REINFORCEMENT_NO_AVAILABLE_QUESTION" : "REINFORCEMENT_NOT_READY";
            throw failure(409, code, "强化练习当前不可创建", "PREPARING".equals(round.getStatus()));
        }
        KnowledgePracticeGoalRow goal = requireGoal(userId);
        if (!Objects.equals(goal.getId(), round.getGoalId()) || !Objects.equals(goal.getSyllabusVersionId(), round.getSyllabusVersionId())) {
            throw failure(409, "REINFORCEMENT_LEARNING_GOAL_CHANGED", "当前学习目标已变化", false);
        }
        ReinforcementRecommendationGenerator.Recommendation effectiveRecommendation = recommendation == null
            ? new ReinforcementRecommendationGenerator.Recommendation(round.getRecommendationReason(),
                round.getRecommendationSource(), round.getErrorCode())
            : recommendation;
        List<KnowledgePracticeQuestionRow> candidates = mapper.selectReinforcementCandidates(round, round.getSyllabusVersionId());
        if (candidates.isEmpty()) {
            mapper.updateReinforcementRecommendation(roundId, "UNAVAILABLE", effectiveRecommendation.reason(),
                effectiveRecommendation.source(), 0, "REINFORCEMENT_NO_AVAILABLE_QUESTION");
            throw failure(422, "REINFORCEMENT_NO_AVAILABLE_QUESTION", "题库中暂无更多未做题", false);
        }
        if (mapper.updateReinforcementRecommendation(roundId, "READY", effectiveRecommendation.reason(),
            effectiveRecommendation.source(), candidates.size(), effectiveRecommendation.errorCode()) == 0) {
            throw failure(409, "REINFORCEMENT_STATE_CONFLICT", "强化建议状态已变化", true);
        }
        Long ruleVersionId = mapper.selectPublishedRuleVersion();
        if (ruleVersionId == null) throw failure(503, "REINFORCEMENT_SYSTEM_UNAVAILABLE", "强化练习暂不可用", true);
        long sessionId = IdUtil.getSnowflakeNextId();
        if (mapper.insertReinforcementSession(sessionId, round, ruleVersionId, requestId) != 1) {
            throw failure(409, "REINFORCEMENT_LEARNING_GOAL_CHANGED", "当前学习目标已变化", false);
        }
        int order = 1;
        for (KnowledgePracticeQuestionRow candidate : candidates) {
            Snapshots frozen = snapshots(candidate);
            mapper.insertSessionQuestion(IdUtil.getSnowflakeNextId(), sessionId, candidate, order++,
                frozen.presentation(), frozen.grading(), frozen.knowledge());
        }
        if (mapper.startReinforcementRound(roundId, userId, sessionId, requestId, candidates.size()) != 1) {
            throw failure(409, "REINFORCEMENT_IDEMPOTENCY_CONFLICT", "强化轮次已被其他请求创建", false);
        }
        return new ReinforcementRoundVo(String.valueOf(roundId), round.getRoundNo(), "STARTED", candidates.size(),
            String.valueOf(sessionId), answerPath(sessionId, roundId), String.valueOf(round.getSourceSessionId()),
            round.getSourceQuestionOrder());
    }

    private ReinforcementSuggestionVo suggestion(ReinforcementRoundRow round) {
        List<String> actions = switch (round.getStatus()) {
            case "READY" -> List.of("START", "DISMISS");
            case "STARTED" -> List.of("ENTER");
            case "COMPLETED" -> List.of("VIEW_RESULT");
            default -> List.of();
        };
        return new ReinforcementSuggestionVo(String.valueOf(round.getId()), round.getStatus(), knowledgePoints(round),
            round.getRecommendationReason(), round.getRecommendationSource(), round.getEstimatedCount(),
            round.getReinforcementSessionId() == null ? null : String.valueOf(round.getReinforcementSessionId()),
            round.getReinforcementSessionId() == null ? null : answerPath(round.getReinforcementSessionId(), round.getId()), actions);
    }

    private List<ReinforcementSuggestionVo.KnowledgePointVo> knowledgePoints(ReinforcementRoundRow round) {
        try {
            List<ReinforcementSuggestionVo.KnowledgePointVo> result = new ArrayList<>();
            for (JsonNode item : jsonMapper.readTree(round.getKnowledgeSnapshot()).path("items")) {
                result.add(new ReinforcementSuggestionVo.KnowledgePointVo(item.path("knowledgePointId").asText(),
                    item.path("knowledgePointName").asText()));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new KnowledgePracticeException(500, "REINFORCEMENT_SYSTEM_UNAVAILABLE", "强化数据读取失败", true,
                List.of(), null, exception);
        }
    }

    private ReinforcementRoundVo roundVo(ReinforcementRoundRow round) {
        return new ReinforcementRoundVo(String.valueOf(round.getId()), round.getRoundNo(), round.getStatus(),
            round.getEstimatedCount(), round.getReinforcementSessionId() == null ? null : String.valueOf(round.getReinforcementSessionId()),
            round.getReinforcementSessionId() == null ? null : answerPath(round.getReinforcementSessionId(), round.getId()),
            String.valueOf(round.getSourceSessionId()), round.getSourceQuestionOrder());
    }

    private String answerPath(long sessionId, long roundId) {
        return "/learning/session/practice?sessionId=" + sessionId + "&reinforcementRoundId=" + roundId;
    }

    private String requireSingleAnswer(SubmitKnowledgePracticeItemBo command) {
        if (command == null || command.getAnswer() == null) throw invalid("answer", "REQUIRED", "答案不能为空");
        List<String> values = command.getAnswer().getValue();
        if (values == null || values.isEmpty()) throw invalid("answer.value", "REQUIRED", "请选择一个答案");
        if (values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()) {
            throw failure(422, "PRACTICE_ANSWER_INVALID", "单选题必须且只能选择一个有效选项", false);
        }
        return values.getFirst();
    }

    private SubmitKnowledgePracticeItemVo replaySubmit(KnowledgePracticeIdempotencyRow record, PayloadHashes hashes, long userId) {
        return replayAction(record, hashes, userId, SubmitKnowledgePracticeItemVo.class);
    }

    private CompleteKnowledgePracticeVo replayComplete(KnowledgePracticeIdempotencyRow record, PayloadHashes hashes, long userId) {
        return replayAction(record, hashes, userId, CompleteKnowledgePracticeVo.class);
    }

    private <T> T replayAction(KnowledgePracticeIdempotencyRow record, PayloadHashes hashes, long userId, Class<T> type) {
        if (!hashes.matches(record.getPayloadHash()) || !"succeeded".equals(record.getStatus()) || record.getResourceId() == null) {
            throw failure(409, "PRACTICE_IDEMPOTENCY_CONFLICT", "请求号已被其他请求使用", false);
        }
        try {
            JsonNode root = jsonMapper.readTree(record.getResponseBody());
            JsonNode response = root.has("response") ? root.path("response") : root;
            return jsonMapper.treeToValue(response.path("data"), type);
        } catch (Exception exception) {
            throw new KnowledgePracticeException(500, "KNOWLEDGE_PRACTICE_ANSWERING_SYSTEM_FAILURE",
                "练习响应回放失败", true, List.of(), null, exception);
        }
    }

    private List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private void requirePositive(long value, String field) {
        if (value <= 0) throw invalid(field, "OUT_OF_RANGE", "必须是十进制正整数");
    }

    private String returnPath() { return "/learning/question-bank/knowledge-practice"; }

    private KnowledgePracticeGoalRow requireGoal(long userId) {
        KnowledgePracticeGoalRow goal = mapper.selectCurrentGoal(userId);
        if (goal == null) {
            throw failure(409, "LEARNING_GOAL_NOT_ACTIVE", "当前没有有效学习目标", false);
        }
        if (!"active".equals(goal.getGoalStatus()) || !"0".equals(goal.getCertificationStatus())
            || goal.getTargetExamYear() == null || goal.getTargetExamMonth() == null) {
            throw failure(422, "LEARNING_NOT_ALLOWED", "当前学习目标暂不允许练习", false);
        }
        return goal;
    }

    private void requireDirectory(KnowledgePracticeGoalRow goal) {
        if (mapper.countDirectoryViolations(goal.getSyllabusVersionId()) > 0) {
            log.warn("Knowledge practice directory invalid, goalId={}, syllabusVersionId={}",
                goal.getId(), goal.getSyllabusVersionId());
            throw failure(422, "PRACTICE_DIRECTORY_INVALID", "知识点目录暂不可用", false);
        }
    }

    private KnowledgePracticeSetupVo setupVo(KnowledgePracticeGoalRow goal, List<KnowledgePracticeNodeRow> rows) {
        Map<Long, MutableNode> nodes = new LinkedHashMap<>();
        rows.forEach(row -> nodes.put(row.getId(), new MutableNode(row)));
        nodes.values().forEach(node -> {
            MutableNode parent = nodes.get(node.row().getParentId());
            if (parent != null) parent.children().add(node);
        });
        Map<Long, List<MutableNode>> rootsBySubject = new LinkedHashMap<>();
        nodes.values().stream().filter(node -> !nodes.containsKey(node.row().getParentId()))
            .forEach(node -> rootsBySubject.computeIfAbsent(node.row().getExamSubjectId(), ignored -> new ArrayList<>()).add(node));

        List<KnowledgePracticeSetupVo.SubjectVo> subjects = rootsBySubject.entrySet().stream()
            .sorted(Comparator.comparingInt(entry -> nodesFor(entry.getValue()).getSubjectOrder()))
            .map(entry -> {
                KnowledgePracticeNodeRow first = nodesFor(entry.getValue());
                return new KnowledgePracticeSetupVo.SubjectVo(String.valueOf(entry.getKey()), first.getSubjectName(),
                    entry.getValue().stream().map(this::nodeVo).toList());
            }).toList();
        return new KnowledgePracticeSetupVo(
            new KnowledgePracticeSetupVo.GoalVo(String.valueOf(goal.getId()), goal.getCertificationName(),
                goal.getSyllabusVersionName(), goal.getRowVersion()), subjects);
    }

    private KnowledgePracticeNodeRow nodesFor(List<MutableNode> nodes) {
        return nodes.getFirst().row();
    }

    private KnowledgePracticeNodeVo nodeVo(MutableNode node) {
        KnowledgePracticeNodeRow row = node.row();
        return new KnowledgePracticeNodeVo(String.valueOf(row.getId()),
            row.getParentId() == null ? null : String.valueOf(row.getParentId()), String.valueOf(row.getExamSubjectId()),
            row.getSyllabusNumber(), row.getSyllabusTitle(), row.getTreeDepth(), row.getSortOrder(), row.getImportance(),
            row.getQuestionCount(), new KnowledgePracticeMasteryVo(row.getCurrentDirectAbility(),
            row.getProfileStatus(), row.getConfidenceLevel()), node.children().stream().map(this::nodeVo).toList());
    }

    private Snapshots snapshots(KnowledgePracticeQuestionRow question) {
        try {
            Map<String, Object> presentation = VersionedJsonDocumentFactory.flatFields(
                AssessmentJsonSchema.KNOWLEDGE_PRACTICE_PRESENTATION, Map.of());
            presentation.put("questionType", question.getQuestionType());
            presentation.put("stem", question.getStem());
            presentation.put("difficulty", question.getDifficulty());
            presentation.put("estimatedSeconds", question.getEstimatedSeconds());
            presentation.put("options", jsonMapper.readTree(question.getOptionsJson()));
            presentation.put("images", jsonMapper.readTree(question.getImagesJson()));

            Map<String, Object> grading = VersionedJsonDocumentFactory.flatFields(
                AssessmentJsonSchema.KNOWLEDGE_PRACTICE_GRADING, Map.of());
            grading.put("questionType", question.getQuestionType());
            grading.put("answer", jsonMapper.readTree(question.getAnswerJson()));
            grading.put("analysis", question.getAnalysis());
            grading.put("maxScore", question.getMaxScore());
            grading.put("rules", Map.of("selectionMode", "single"));

        Map<String, Object> knowledge = VersionedJsonDocumentFactory.flatFields(
                AssessmentJsonSchema.KNOWLEDGE_PRACTICE_KNOWLEDGE,
                Map.of("items", jsonMapper.readTree(question.getKnowledgeJson())));
            return new Snapshots(write(presentation), write(grading), write(knowledge));
        } catch (Exception exception) {
            throw new KnowledgePracticeException(500, "KNOWLEDGE_PRACTICE_SYSTEM_FAILURE", "练习创建失败", true,
                List.of(), null, exception);
        }
    }

    private String normalizeRequestId(String requestId) {
        try {
            if (requestId == null || requestId.length() > 100 || !requestId.equals(requestId.trim())) throw new IllegalArgumentException();
            UUID uuid = UUID.fromString(requestId);
            String normalized = uuid.toString();
            if (requestId.length() != 36) throw new IllegalArgumentException();
            return normalized;
        } catch (RuntimeException exception) {
            throw invalid("X-Request-Id", "INVALID_FORMAT", "请求号必须是标准UUID");
        }
    }

    private long parseId(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw invalid("knowledgePointId", "OUT_OF_RANGE", "知识点ID超出范围"); }
    }

    private PayloadHashes startHashes(long userId, long knowledgePointId, long version) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("userId", String.valueOf(userId));
        fields.put("knowledgePointId", String.valueOf(knowledgePointId));
        fields.put("expectedGoalVersion", version);
        return hashes(VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.KNOWLEDGE_PRACTICE_START,
                fields),
            KnowledgePracticeLegacyIdempotencyPayloads.start(jsonMapper, userId, knowledgePointId, version));
    }

    private Map<String, Object> submitHashFields(long userId, long sessionId, int questionOrder, String selected) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("userId", String.valueOf(userId));
        fields.put("sessionId", String.valueOf(sessionId));
        fields.put("questionOrder", questionOrder);
        fields.put("value", List.of(selected));
        return fields;
    }

    private Map<String, Object> completeHashFields(long userId, long sessionId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("userId", String.valueOf(userId));
        fields.put("sessionId", String.valueOf(sessionId));
        return fields;
    }

    private PayloadHashes hashes(String canonicalPayload, String legacyPayload) {
        return new PayloadHashes(hash(canonicalPayload), hash(legacyPayload));
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private StartKnowledgePracticeVo replay(KnowledgePracticeIdempotencyRow record, PayloadHashes hashes, long userId) {
        if (!hashes.matches(record.getPayloadHash()) || !"succeeded".equals(record.getStatus()) || record.getResourceId() == null) {
            throw failure(409, "PRACTICE_IDEMPOTENCY_CONFLICT", "请求号已被其他请求使用", false);
        }
        KnowledgePracticeSessionRow session = mapper.selectOwnedSession(record.getResourceId(), userId);
        if (session == null) {
            throw failure(409, "PRACTICE_IDEMPOTENCY_CONFLICT", "请求号不属于当前用户", false);
        }
        try {
            JsonNode root = jsonMapper.readTree(record.getResponseBody());
            JsonNode response = root.has("response") ? root.path("response") : root;
            return jsonMapper.treeToValue(response.path("data"), StartKnowledgePracticeVo.class);
        } catch (Exception exception) {
            throw new KnowledgePracticeException(500, "KNOWLEDGE_PRACTICE_SYSTEM_FAILURE", "练习响应回放失败", true,
                List.of(), null, exception);
        }
    }

    private KnowledgePracticeException activeSession(long userId, long goalId) {
        KnowledgePracticeSessionRow session = mapper.selectActiveSession(userId, goalId);
        if (session == null) return failure(409, "PRACTICE_IDEMPOTENCY_CONFLICT", "练习创建发生并发冲突", false);
        return new KnowledgePracticeException(409, "PRACTICE_SESSION_IN_PROGRESS", "已有进行中的练习", false,
            List.of(), new KnowledgePracticeErrorVo.DetailsVo(path(session.getId())), null);
    }

    private StartKnowledgePracticeVo result(long sessionId, int total) {
        return new StartKnowledgePracticeVo(String.valueOf(sessionId), total, path(sessionId));
    }

    private String response(StartKnowledgePracticeVo result) {
        return VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.KNOWLEDGE_PRACTICE_IDEMPOTENCY,
            Map.of("response", Map.of("code", 200, "msg", "操作成功", "data", result)));
    }

    private String actionResponse(Object result) {
        return VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.KNOWLEDGE_PRACTICE_ACTION_IDEMPOTENCY,
            Map.of("response", Map.of("code", 200, "msg", "操作成功", "data", result)));
    }

    private String path(long sessionId) { return "/learning/session/practice?sessionId=" + sessionId; }

    private String write(Object value) {
        try { return jsonMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("knowledge-practice JSON serialization failed", exception); }
    }

    private KnowledgePracticeException invalid(String field, String code, String message) {
        return new KnowledgePracticeException(400, "PRACTICE_REQUEST_INVALID", "请求参数格式不正确", false,
            List.of(new KnowledgePracticeErrorVo.FieldErrorVo(field, code, message)), null, null);
    }

    private KnowledgePracticeException failure(int status, String code, String message, boolean retryable) {
        return new KnowledgePracticeException(status, code, message, retryable);
    }

    private record MutableNode(KnowledgePracticeNodeRow row, List<MutableNode> children) {
        private MutableNode(KnowledgePracticeNodeRow row) { this(row, new ArrayList<>()); }
    }
    private record Snapshots(String presentation, String grading, String knowledge) {}
    private record PayloadHashes(String canonical, String legacy) {
        private boolean matches(String stored) {
            return canonical.equals(stored) || legacy.equals(stored);
        }
    }
}
