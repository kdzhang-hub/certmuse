package org.dromara.certmuse.assessment.service.impl;

import cn.hutool.core.util.IdUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.assessment.domain.PastPaperGoalRow;
import org.dromara.certmuse.assessment.domain.PastPaperIdempotencyRow;
import org.dromara.certmuse.assessment.domain.PastPaperPaperRow;
import org.dromara.certmuse.assessment.domain.PastPaperQuestionRow;
import org.dromara.certmuse.assessment.domain.PastPaperSessionRow;
import org.dromara.certmuse.assessment.domain.bo.PastPaperAnswerBo;
import org.dromara.certmuse.assessment.domain.bo.PastPaperStartBo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperErrorVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperListVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperPreviewVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperRevealVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperSetupVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperStartVo;
import org.dromara.certmuse.assessment.domain.vo.CompletePastPaperPracticeVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperPracticeItemVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperPracticeSessionVo;
import org.dromara.certmuse.assessment.domain.vo.StartPastPaperPracticeVo;
import org.dromara.certmuse.assessment.domain.vo.SubmitPastPaperPracticeItemVo;
import org.dromara.certmuse.assessment.mapper.PastPaperMapper;
import org.dromara.certmuse.assessment.service.PastPaperService;
import org.dromara.certmuse.assessment.service.FormalExamEngine;
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
import org.dromara.certmuse.assessment.support.AssessmentJsonSchema;
import org.dromara.certmuse.assessment.support.PastPaperException;
import org.dromara.certmuse.assessment.support.SubjectiveAnswerValidator;
import org.dromara.certmuse.assessment.support.SubjectiveGradingTasks;
import org.dromara.certmuse.assessment.service.FormalExamScoringRules;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.dromara.common.core.domain.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** U15 aggregate implementation; all learner questions are read from frozen snapshots after start. */
@Slf4j @Service @RequiredArgsConstructor
public class PastPaperServiceImpl implements PastPaperService {
    private static final Duration DISCLOSURE_BLOCK = Duration.ofHours(72);
    private static final Duration REPEAT_WINDOW = Duration.ofDays(7);
    private final PastPaperMapper mapper;
    private final JsonMapper jsonMapper;
    private final QuestionImageUrlService imageUrlService;
    private final FormalExamEngine formalExamEngine;
    private final SubjectiveGradingTasks subjectiveTasks;

    @Override public PastPaperSetupVo setup(Long userId) {
        Map<String, List<PastPaperSetupVo.SubjectVo>> subjects = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (PastPaperPaperRow row : mapper.selectPapers(null, null, null, null, 500, 0)) {
            String key = String.valueOf(row.getCertificationId()); names.put(key, row.getCertificationName());
            List<PastPaperSetupVo.SubjectVo> values = subjects.computeIfAbsent(key, ignored -> new ArrayList<>());
            for (JsonNode subject : tree(row.getSubjectsJson())) {
                PastPaperSetupVo.SubjectVo value = new PastPaperSetupVo.SubjectVo(subject.path("id").asText(), subject.path("name").asText());
                if (!values.contains(value)) values.add(value);
            }
        }
        PastPaperGoalRow goal = userId == null ? null : mapper.selectActiveGoal(userId);
        PastPaperSetupVo.GoalVo goalVo = goal == null ? null : new PastPaperSetupVo.GoalVo(
            String.valueOf(goal.getId()), goal.getRowVersion(), String.valueOf(goal.getCertificationId()),
            goal.getCertificationName());
        return new PastPaperSetupVo(subjects.entrySet().stream().map(entry ->
            new PastPaperSetupVo.CertificationVo(entry.getKey(), names.get(entry.getKey()), entry.getValue())).toList(), goalVo);
    }

    @Override public PageResult<PastPaperListVo> list(Long userId, String certificationId, String subjectId, String keyword,
                                                       Integer pageNum, Integer pageSize) {
        Long cert = optionalId(certificationId, "certificationId"); Long subject = optionalId(subjectId, "subjectId");
        int page = pageNum == null ? 1 : pageNum; int size = pageSize == null ? 20 : pageSize;
        if (page < 1 || size < 1 || size > 100) throw invalid("pageSize", "OUT_OF_RANGE", "分页参数超出范围");
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && (normalizedKeyword.isEmpty() || normalizedKeyword.length() > 100)) throw invalid("keyword", "OUT_OF_RANGE", "关键词长度超出范围");
        return PageResult.build(mapPapers(mapper.selectPapers(userId, cert, subject, normalizedKeyword, size, (long) (page - 1) * size)),
            mapper.countPapers(cert, subject, normalizedKeyword));
    }

    @Override public PastPaperListVo detail(long collectionId) { return paperVo(requirePaper(collectionId)); }

    @Override public PastPaperPreviewVo preview(long collectionId) {
        PastPaperPaperRow paper = requirePaper(collectionId);
        return new PastPaperPreviewVo(String.valueOf(collectionId), String.valueOf(paper.getRevisionId()),
            mapper.selectPublishedQuestions(collectionId).stream().map(this::previewQuestion).toList());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public PastPaperRevealVo reveal(long userId, long collectionId, int questionOrder, String requestId) {
        String request = requestId(requestId); PastPaperPaperRow paper = requirePaper(collectionId);
        PastPaperQuestionRow question = mapper.selectPublishedQuestion(collectionId, positive(questionOrder, "questionOrder"));
        if (question == null) throw notFound("PAST_PAPER_NOT_FOUND", "真题不存在或不可见");
        String hash = hash("reveal:" + userId + ':' + collectionId + ':' + questionOrder);
        PastPaperIdempotencyRow old = mapper.selectIdempotency("PAST_PAPER_ANSWER_REVEAL", request);
        if (old != null) return replayReveal(old, hash);
        long id = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(id, "PAST_PAPER_ANSWER_REVEAL", request, hash) == 0) return replayReveal(mapper.selectIdempotency("PAST_PAPER_ANSWER_REVEAL", request), hash);
        mapper.insertDisclosure(IdUtil.getSnowflakeNextId(), userId, question, paper.getRevisionId(), request);
        OffsetDateTime now = OffsetDateTime.now();
        PastPaperRevealVo result = new PastPaperRevealVo(labels(tree(question.getAnswerJson()).path("value")), question.getAnalysis(), now, now.plus(DISCLOSURE_BLOCK));
        mapper.completeIdempotency(id, "cm_past_paper_answer_disclosure", question.getQuestionId(), response(result));
        return result;
    }

    @Override
    public PastPaperStartVo startExam(long userId, long collectionId, String requestId, PastPaperStartBo command) {
        FormalExamEngine.StartResult started = formalExamEngine.start(userId, collectionId, "PAST_PAPER", "past_paper_exam",
            id(command.getExpectedRevisionId(), "expectedRevisionId"), command.getExpectedGoalVersion(), requestId);
        return new PastPaperStartVo(started.sessionId(), started.totalCount(),
            started.resumed() ? "RESUME" : "START", started.answerPath());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public StartPastPaperPracticeVo startPractice(long userId, long collectionId, String requestId,
                                                   PastPaperStartBo command) {
        String request = requestId(requestId); PastPaperPaperRow paper = requirePaper(collectionId); PastPaperGoalRow goal = goal(userId);
        long expectedRevision = id(command.getExpectedRevisionId(), "expectedRevisionId");
        if (!Objects.equals(expectedRevision, paper.getRevisionId())) throw failure(409, "PAST_PAPER_REVISION_CHANGED", "题集已更新", true);
        if (!Objects.equals(command.getExpectedGoalVersion(), goal.getRowVersion())) throw failure(409, "PAST_PAPER_GOAL_VERSION_CONFLICT", "当前学习目标已变化", true);
        if (!Objects.equals(goal.getCertificationId(), paper.getCertificationId())) throw notFound("PAST_PAPER_NOT_FOUND", "真题不存在或不可见");
        List<PastPaperQuestionRow> questions = mapper.selectPublishedQuestions(collectionId);
        questions.forEach(this::normalizePracticeQuestion);
        validatePracticeQuestions(questions);
        String type = "past_paper_practice";
        String action = "PAST_PAPER_PRACTICE_START";
        String payloadHash = hash(action + ':' + userId + ':' + collectionId + ':' + expectedRevision + ':' + command.getExpectedGoalVersion());
        PastPaperIdempotencyRow old = mapper.selectIdempotency(action, request); if (old != null) return replay(old, payloadHash, StartPastPaperPracticeVo.class);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, action, request, payloadHash) == 0) return replay(mapper.selectIdempotency(action, request), payloadHash, StartPastPaperPracticeVo.class);
        PastPaperSessionRow active = mapper.selectActiveSession(userId, goal.getId(), paper.getRevisionId(), type);
        if (active != null) {
            StartPastPaperPracticeVo result = practiceStartVo(active);
            mapper.completeIdempotency(idempotencyId, "cm_learning_session", active.getId(), response(result));
            return result;
        }
        Long rule = mapper.selectPublishedRuleVersion(); if (rule == null) throw failure(503, "PAST_PAPER_RULE_VERSION_UNAVAILABLE", "画像规则暂不可用", true);
        long sessionId = IdUtil.getSnowflakeNextId();
        if (mapper.insertSession(sessionId, userId, goal, paper.getRevisionId(), type, rule, request, null, null) == 0) {
            PastPaperSessionRow concurrent = mapper.selectActiveSession(userId, goal.getId(), paper.getRevisionId(), type);
            if (concurrent == null) throw failure(409, "PAST_PAPER_PRACTICE_IDEMPOTENCY_CONFLICT", "练习创建发生并发冲突", false);
            StartPastPaperPracticeVo result = practiceStartVo(concurrent);
            mapper.completeIdempotency(idempotencyId, "cm_learning_session", concurrent.getId(), response(result));
            return result;
        }
        for (PastPaperQuestionRow question : questions) {
            Snapshots snapshots = snapshots(question); mapper.insertSessionQuestion(IdUtil.getSnowflakeNextId(), sessionId, question, snapshots.presentation(), snapshots.grading(), snapshots.knowledge());
        }
        StartPastPaperPracticeVo result = new StartPastPaperPracticeVo(String.valueOf(sessionId), questions.size(), path(type, sessionId));
        mapper.completeIdempotency(idempotencyId, "cm_learning_session", sessionId, response(result)); return result;
    }

    @Override public FormalExamSessionVo formalSession(long userId,long sessionId){return formalExamEngine.session(userId,sessionId,"past_paper_exam");}
    @Override public FormalExamItemVo formalItem(long userId,long sessionId,int order){return formalExamEngine.item(userId,sessionId,order,"past_paper_exam");}
    @Override public FormalExamSessionVo saveFormalDraft(long userId,long sessionId,int order,String requestId,FormalExamDraftBo command){return formalExamEngine.saveDraft(userId,sessionId,order,"past_paper_exam",requestId,command);}
    @Override public FormalExamTimerEventVo formalTimerEvent(long userId,long sessionId,String requestId,FormalExamTimerEventBo command){return formalExamEngine.timerEvent(userId,sessionId,"past_paper_exam",requestId,command);}
    @Override public FormalExamSessionVo pauseFormal(long userId,long sessionId,String requestId,FormalExamPauseBo command){return formalExamEngine.pause(userId,sessionId,"past_paper_exam",requestId,command);}
    @Override public FormalExamFinishCheckVo formalFinishCheck(long userId,long sessionId){return formalExamEngine.finishCheck(userId,sessionId,"past_paper_exam");}
    @Override public FormalExamFinishVo finishFormal(long userId,long sessionId,String requestId,FormalExamFinishBo command){return formalExamEngine.finish(userId,sessionId,"past_paper_exam",requestId,command);}
    @Override public FormalExamStatusVo formalStatus(long userId,long sessionId){return formalExamEngine.status(userId,sessionId,"past_paper_exam");}
    @Override public FormalExamResultVo formalResult(long userId,long sessionId){return formalExamEngine.result(userId,sessionId,"past_paper_exam");}
    @Override public FormalExamStatusVo regenerateFormal(long userId,long sessionId,String requestId){return formalExamEngine.regenerate(userId,sessionId,"past_paper_exam",requestId);}

    @Override
    public PastPaperPracticeSessionVo practiceSession(long userId, long sessionId) {
        PastPaperSessionRow session = requirePracticeSession(userId, sessionId);
        return practiceSessionVo(session);
    }

    @Override
    public PastPaperPracticeItemVo practiceItem(long userId, long sessionId, int questionOrder) {
        PastPaperSessionRow session = requirePracticeSession(userId, sessionId);
        PastPaperQuestionRow question = mapper.selectSessionQuestion(userId, sessionId,
            positive(questionOrder, "questionOrder"));
        if (question == null) {
            throw notFound("PAST_PAPER_PRACTICE_ITEM_NOT_FOUND", "练习题目不存在");
        }
        return practiceItemVo(session, question);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public SubmitPastPaperPracticeItemVo submitPractice(long userId,long sessionId,int questionOrder,String requestId,PastPaperAnswerBo command) {
        if (command != null && command.getAnswer() != null && command.getAnswer().getValue() != null) answer(command);
        String request=requestId(requestId);
        String payloadHash = hash("PAST_PAPER_PRACTICE_SUBMIT:" + userId + ':' + sessionId + ':' + questionOrder + ':' + write(command.getAnswer()));
        PastPaperIdempotencyRow old = mapper.selectIdempotency("PAST_PAPER_PRACTICE_SUBMIT", request);
        if (old != null) return replay(old, payloadHash, SubmitPastPaperPracticeItemVo.class);
        PastPaperSessionRow s=lock(userId,sessionId); requireType(s,"past_paper_practice"); requirePracticeActive(s);
        PastPaperQuestionRow q=mapper.selectSessionQuestion(userId,sessionId,positive(questionOrder,"questionOrder")); if(q==null) throw notFound("PAST_PAPER_PRACTICE_ITEM_NOT_FOUND","练习题目不存在"); if(q.getFinalAnswerJson()!=null) throw failure(409,"PAST_PAPER_PRACTICE_ITEM_ALREADY_SUBMITTED","该题已提交",false);
        String questionType = tree(q.getPresentationSnapshot()).path("questionType").asText();
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, "PAST_PAPER_PRACTICE_SUBMIT", request, payloadHash) == 0) {
            return replay(mapper.selectIdempotency("PAST_PAPER_PRACTICE_SUBMIT", request), payloadHash, SubmitPastPaperPracticeItemVo.class);
        }
        if ("CHOICE".equals(questionType)) {
            String selected = answer(command);
            validateAnswer(q, selected);
            settleQuestion(s,q,selected,request);
        } else {
            submitPracticeSubjective(s, q, command, request, questionType);
        }
        PastPaperPracticeItemVo submitted = practiceItemVo(s, mapper.selectSessionQuestion(userId,sessionId,questionOrder));
        int submittedCount = mapper.selectSession(userId, sessionId).getAnsweredCount();
        SubmitPastPaperPracticeItemVo result = new SubmitPastPaperPracticeItemVo(questionOrder, submittedCount,
            submitted.submission());
        mapper.completeIdempotency(idempotencyId, "cm_question_attempt", q.getSessionQuestionId(), response(result));
        return result;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public CompletePastPaperPracticeVo completePractice(long userId,long sessionId,String requestId) {
        String request = requestId(requestId);
        String payloadHash = hash("PAST_PAPER_PRACTICE_COMPLETE:" + userId + ':' + sessionId);
        PastPaperIdempotencyRow old = mapper.selectIdempotency("PAST_PAPER_PRACTICE_COMPLETE", request);
        if (old != null) return replay(old, payloadHash, CompletePastPaperPracticeVo.class);
        PastPaperSessionRow s=lock(userId,sessionId); requireType(s,"past_paper_practice"); if ("completed".equals(s.getStatus())) return completePracticeVo(s);
        if(!"in_progress".equals(s.getStatus())) throw failure(409,"PAST_PAPER_PRACTICE_SESSION_NOT_ACTIVE","练习不可结束",false);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, "PAST_PAPER_PRACTICE_COMPLETE", request, payloadHash) == 0) {
            return replay(mapper.selectIdempotency("PAST_PAPER_PRACTICE_COMPLETE", request), payloadHash, CompletePastPaperPracticeVo.class);
        }
        mapper.markSubmitted(sessionId,null); mapper.markSettling(sessionId); mapper.markCompleted(sessionId);
        CompletePastPaperPracticeVo result = completePracticeVo(s);
        mapper.completeIdempotency(idempotencyId, "cm_learning_session", sessionId, response(result));
        return result;
    }

    @Override @Transactional(rollbackFor = Exception.class) public void autoFinishExpired(int limit) { for(PastPaperSessionRow s:mapper.lockExpiredExamSessions(Math.max(1,Math.min(limit,100)))) finishExamLocked(s,"auto-"+s.getId(),null); }

    @Override @Transactional(rollbackFor = Exception.class)
    public void processPracticeSubjectiveGrading(int limit) {
        for (PastPaperQuestionRow question : mapper.selectPendingPracticeSubjectiveAttempts(Math.max(1, Math.min(limit, 100)))) {
            SubjectiveGradingTasks.TaskState state = subjectiveTasks.ensure(question.getQuestionRevisionId(),
                question.getSessionQuestionId(), question.getAttemptId(), question.getPresentationSnapshot(),
                question.getGradingSnapshot(), question.getKnowledgeSnapshot(), tree(question.getFinalAnswerJson()).path("value").asText());
            if (Set.of("RUBRIC_PENDING", "PENDING", "PROCESSING").contains(state.status())) continue;
            if ("FAILED".equals(state.status())) {
                mapper.failSubjectiveAttempt(question.getAttemptId(), tree(question.getGradingSnapshot()).path("reportScore").asText("0"),
                    write(Map.of("errorCode", state.errorCode() == null ? "AI_GRADING_FAILED" : state.errorCode())));
                continue;
            }
            SubjectiveAnswerValidator.GradingResult result = SubjectiveAnswerValidator.gradingResult(tree(state.rubric()), tree(state.result()));
            if (result == null) {
                mapper.failSubjectiveAttempt(question.getAttemptId(), tree(question.getGradingSnapshot()).path("reportScore").asText("0"),
                    write(Map.of("errorCode", "AI_OUTPUT_INVALID")));
                continue;
            }
            BigDecimal max = new BigDecimal(tree(question.getGradingSnapshot()).path("reportScore").asText("0"));
            mapper.scoreSubjectiveAttempt(question.getAttemptId(), max.multiply(result.scoreRate()).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                max.toPlainString(), result.scoreRate().toPlainString(), state.result());
            if (Boolean.TRUE.equals(question.getProfileApplied())) settlePracticeSubjectiveEvidence(question, result.scoreRate());
        }
    }

    private void finishExamLocked(PastPaperSessionRow s,String request,Long expectedVersion) { if(!"in_progress".equals(s.getStatus())) return; if(mapper.markSubmitted(s.getId(),expectedVersion)!=1) { if(expectedVersion!=null) throw version(); return; } for(PastPaperQuestionRow q:mapper.selectSessionQuestions(s.getUserId(),s.getId())) if(q.getFinalAnswerJson()==null) settleQuestion(s,q,singleValue(tree(q.getDraftAnswerJson())),request+"-"+q.getQuestionOrder()); mapper.markSettling(s.getId()); mapper.insertJob(IdUtil.getSnowflakeNextId(),jobKey(s.getId()),versioned(AssessmentJsonSchema.PAST_PAPER_RESULT,Map.of("sessionId",String.valueOf(s.getId())))); }

    @Override @Transactional(rollbackFor = Exception.class) public void dispatchResults(int limit) { for(Long sessionId: mapper.selectQueuedResultSessionIds(Math.max(1,Math.min(limit,100)))) if(mapper.claimJob(jobKey(sessionId))==1) buildResult(sessionId); }
    /** Runs in a worker transaction; the HTTP finish endpoint only enqueues this calculation. */
    @Transactional(rollbackFor = Exception.class) public void buildResult(long sessionId) { PastPaperSessionRow s=mapper.lockSessionForWorker(sessionId); if(s==null) return; try { BigDecimal score=BigDecimal.ZERO,max=BigDecimal.ZERO; for(PastPaperQuestionRow q:mapper.selectSessionQuestions(s.getUserId(),sessionId)){ JsonNode g=tree(q.getGradingSnapshot()); BigDecimal points=new BigDecimal(g.path("reportScore").asText("0")); max=max.add(points); if(Boolean.TRUE.equals(q.getCorrect())) score=score.add(points); } mapper.insertReport(IdUtil.getSnowflakeNextId(),s,"past_paper_exam",score.toPlainString(),max.toPlainString(),versioned(AssessmentJsonSchema.PAST_PAPER_SUBJECT_SCORES,Map.of("items",List.of())),versioned(AssessmentJsonSchema.PAST_PAPER_PROFILE_SUMMARY,Map.of("profileApplied",true))); mapper.markCompleted(sessionId); mapper.markJobSucceeded(jobKey(sessionId)); } catch(Exception ex){ log.error("Past-paper result generation failed, sessionId={}",sessionId,ex); mapper.markJobFailed(jobKey(sessionId),"result_generation_failed"); } }

    private void settleQuestion(PastPaperSessionRow s,PastPaperQuestionRow q,String selected,String request) { if(selected!=null) validateAnswer(q,selected); JsonNode grading=tree(q.getGradingSnapshot()); List<String> correct=labels(grading.path("answer").path("value")); boolean blank=selected==null, correctAnswer=!blank&&correct.size()==1&&selected.equals(correct.getFirst()); boolean repeat=mapper.countRecentEvidence(s.getUserId(),s.getGoalId(),q.getEvidenceGroupKey())>0; boolean profileApplied=!disclosedRecently(s.getUserId(),q.getQuestionId()); long attempt=IdUtil.getSnowflakeNextId(); if(mapper.insertAttempt(attempt,q.getSessionQuestionId(),s.getUserId(),request,blank,blank, !repeat,repeat,profileApplied)!=1) throw failure(409,"past_paper_practice".equals(s.getSessionType())?"PAST_PAPER_PRACTICE_ITEM_ALREADY_SUBMITTED":"PAST_PAPER_IDEMPOTENCY_CONFLICT","题目提交发生冲突",false); mapper.insertAttemptAnswer(IdUtil.getSnowflakeNextId(),attempt,answerJson(selected),correctAnswer?grading.path("reportScore").asText("1"):"0",grading.path("reportScore").asText("1"),correctAnswer?"1":"0"); for(JsonNode leaf:tree(q.getKnowledgeSnapshot()).path("items")){ long leafId=Long.parseLong(leaf.path("knowledgePointId").asText()); if(!correctAnswer) mapper.insertErrorRecord(IdUtil.getSnowflakeNextId(),s.getUserId(),s.getGoalId(),attempt,leafId,q.getEvidenceGroupKey()); if(profileApplied) insertEvidence(s,q,attempt,leafId,correctAnswer,repeat,request); } if(profileApplied){ mapper.aggregateSubjectProfiles(s.getUserId(),s.getGoalId(),s.getRuleVersionId()); mapper.aggregateOverallProfile(s.getUserId(),s.getGoalId(),s.getRuleVersionId()); } }
    private void submitPracticeSubjective(PastPaperSessionRow session, PastPaperQuestionRow question, PastPaperAnswerBo command, String request, String type) {
        if (!Set.of("CASE", "ESSAY").contains(type)) throw practiceInvalid("answer.questionType", "题型不支持练习");
        String text = command.getAnswer() == null ? null : command.getAnswer().getText();
        String answer = versioned(AssessmentJsonSchema.SUBJECTIVE_ANSWER, Map.of("questionType", type, "value", text == null ? "" : text));
        if (!SubjectiveAnswerValidator.validate(tree(answer), type).valid()) throw practiceInvalid("answer.text", "主观题答案格式不正确");
        boolean repeat = mapper.countRecentEvidence(session.getUserId(), session.getGoalId(), question.getEvidenceGroupKey()) > 0;
        boolean profileApplied = !disclosedRecently(session.getUserId(), question.getQuestionId());
        long attempt = IdUtil.getSnowflakeNextId();
        if (mapper.insertAttempt(attempt, question.getSessionQuestionId(), session.getUserId(), request, false, false, !repeat, repeat, profileApplied) != 1) {
            throw failure(409, "PAST_PAPER_PRACTICE_ITEM_ALREADY_SUBMITTED", "该题已提交", false);
        }
        mapper.insertPendingSubjectiveAttemptAnswer(IdUtil.getSnowflakeNextId(), attempt, answer,
            tree(question.getGradingSnapshot()).path("reportScore").asText("0"));
    }
    private void settlePracticeSubjectiveEvidence(PastPaperQuestionRow question, BigDecimal rate) {
        boolean repeat = mapper.countRecentEvidence(question.getUserId(), question.getGoalId(), question.getEvidenceGroupKey()) > 0;
        String coefficient = repeat ? "0.03" : "0.25";
        String direction = FormalExamScoringRules.direction(rate);
        for (JsonNode leaf : tree(question.getKnowledgeSnapshot()).path("items")) {
            long leafId = Long.parseLong(leaf.path("knowledgePointId").asText());
            if (FormalExamScoringRules.isMistake(rate)) mapper.insertErrorRecord(IdUtil.getSnowflakeNextId(), question.getUserId(), question.getGoalId(), question.getAttemptId(), leafId, question.getEvidenceGroupKey());
            Long settlement = mapper.insertSettlement(IdUtil.getSnowflakeNextId(), question.getUserId(), question.getGoalId(), question.getSessionId(), question.getAttemptId(), question.getExamSubjectId(), leafId, coefficient, question.getRuleVersionId(), "PAST_PAPER_PRACTICE_AI-" + question.getAttemptId() + '-' + leafId);
            mapper.insertEvidence(IdUtil.getSnowflakeNextId(), settlement, question.getUserId(), question.getGoalId(), question.getSessionId(), question.getAttemptId(), question.getExamSubjectId(), leafId, question.getEvidenceGroupKey(), "past_paper_practice", question.getDifficulty(), rate.toPlainString(), direction, coefficient, !repeat, question.getRuleVersionId());
            mapper.upsertScoredKnowledgeProfile(IdUtil.getSnowflakeNextId(), question.getUserId(), question.getGoalId(), leafId, rate.toPlainString(), coefficient, question.getRuleVersionId());
            mapper.updateSettlementFinal(settlement);
        }
        mapper.aggregateSubjectProfiles(question.getUserId(), question.getGoalId(), question.getRuleVersionId());
        mapper.aggregateOverallProfile(question.getUserId(), question.getGoalId(), question.getRuleVersionId());
    }
    private void insertEvidence(PastPaperSessionRow s,PastPaperQuestionRow q,long attempt,long leaf,boolean correct,boolean repeat,String request){ String coeff=repeat?"0.03":"past_paper_exam".equals(s.getSessionType())?"0.25":"0.15"; if(repeat){String last=mapper.selectLatestEvidenceTime(s.getUserId(),s.getGoalId(),q.getEvidenceGroupKey()); if(last!=null&&timestamp(last).plus(REPEAT_WINDOW).isAfter(OffsetDateTime.now())) return;} Long settlement=mapper.insertSettlement(IdUtil.getSnowflakeNextId(),s.getUserId(),s.getGoalId(),s.getId(),attempt,q.getExamSubjectId(),leaf,coeff,s.getRuleVersionId(),request+"-"+leaf); mapper.insertEvidence(IdUtil.getSnowflakeNextId(),settlement,s.getUserId(),s.getGoalId(),s.getId(),attempt,q.getExamSubjectId(),leaf,q.getEvidenceGroupKey(),s.getSessionType(),q.getDifficulty(),correct?"1":"0",correct?"positive":"negative",coeff,!repeat,s.getRuleVersionId()); mapper.upsertKnowledgeProfile(IdUtil.getSnowflakeNextId(),s.getUserId(),s.getGoalId(),leaf,correct,coeff,s.getRuleVersionId()); mapper.updateSettlementFinal(settlement); }
    private boolean disclosedRecently(long userId,long questionId){String time=mapper.selectLatestDisclosure(userId,questionId); return time!=null&&timestamp(time).plus(DISCLOSURE_BLOCK).isAfter(OffsetDateTime.now());}
    private PastPaperPracticeSessionVo practiceSessionVo(PastPaperSessionRow session) {
        List<PastPaperPracticeSessionVo.NavigationVo> navigation = mapper.selectSessionQuestions(session.getUserId(),
            session.getId()).stream().map(question -> new PastPaperPracticeSessionVo.NavigationVo(
                question.getQuestionOrder(), question.getFinalAnswerJson() == null ? "UNANSWERED"
                    : Boolean.TRUE.equals(question.getCorrect()) ? "SUBMITTED_CORRECT" : "SUBMITTED_INCORRECT"))
            .toList();
        String status = "completed".equals(session.getStatus()) ? "COMPLETED" : "IN_PROGRESS";
        String action = "COMPLETED".equals(status) ? "RETURN_PAST_PAPERS" : "CONTINUE_PRACTICE";
        return new PastPaperPracticeSessionVo(String.valueOf(session.getId()), status, session.getTotalCount(),
            session.getAnsweredCount(), navigation, action, "/learning/question-bank/past-papers");
    }
    private PastPaperPracticeItemVo practiceItemVo(PastPaperSessionRow session, PastPaperQuestionRow question) {
        JsonNode presentation = tree(question.getPresentationSnapshot());
        JsonNode grading = tree(question.getGradingSnapshot());
        List<PastPaperPracticeItemVo.OptionVo> options = new ArrayList<>();
        for (JsonNode option : presentation.path("options")) {
            options.add(new PastPaperPracticeItemVo.OptionVo(option.path("label").asText(), option.path("content").asText()));
        }
        List<PastPaperPracticeItemVo.ImageVo> images = new ArrayList<>();
        for (JsonNode image : presentation.path("images")) {
            images.add(new PastPaperPracticeItemVo.ImageVo(
                imageUrlService.accessUrl(image.path("sourceUrl").asText(null), image.path("storagePath").asText(null)),
                image.path("altText").asText(null), image.path("sortOrder").asInt()));
        }
        String type = presentation.path("questionType").asText();
        PastPaperPracticeItemVo.SubmissionVo submission = question.getFinalAnswerJson() == null ? null
            : "CHOICE".equals(type)
                ? new PastPaperPracticeItemVo.SubmissionVo(labels(tree(question.getFinalAnswerJson()).path("value")), null,
                    Boolean.TRUE.equals(question.getCorrect()), labels(grading.path("answer").path("value")), grading.path("analysis").asText(null), null)
                : new PastPaperPracticeItemVo.SubmissionVo(List.of(), tree(question.getFinalAnswerJson()).path("value").asText(),
                    null, List.of(), null, subjectiveGrading(question));
        return new PastPaperPracticeItemVo(String.valueOf(session.getId()), question.getQuestionOrder(),
            session.getTotalCount(), new PastPaperPracticeItemVo.QuestionVo(type,
                presentation.path("stem").asText(), "CHOICE".equals(type) ? "single" : null, options, images), submission);
    }
    private PastPaperPracticeItemVo.SubjectiveGradingVo subjectiveGrading(PastPaperQuestionRow question) {
        if ("pending".equals(question.getGradingStatus())) return new PastPaperPracticeItemVo.SubjectiveGradingVo("PENDING", null, question.getMaxScore(), null, null, List.of(), null);
        if ("failed".equals(question.getGradingStatus())) return new PastPaperPracticeItemVo.SubjectiveGradingVo("FAILED", null, question.getMaxScore(), null, null, List.of(), tree(question.getGradingResult()).path("errorCode").asText("AI_GRADING_FAILED"));
        List<PastPaperPracticeItemVo.GradingItemVo> items = new ArrayList<>();
        for (JsonNode item : tree(question.getGradingResult()).path("itemResults")) items.add(new PastPaperPracticeItemVo.GradingItemVo(item.path("code").asText(), item.path("scoreRate").asText()));
        return new PastPaperPracticeItemVo.SubjectiveGradingVo("SUCCEEDED", question.getScore(), question.getMaxScore(), question.getScoreRate(), tree(question.getGradingResult()).path("feedback").asText(), items, null);
    }
    private PastPaperPreviewVo.QuestionVo previewQuestion(PastPaperQuestionRow q){List<PastPaperPreviewVo.OptionVo> opts=new ArrayList<>();for(JsonNode o:tree(q.getOptionsJson()))opts.add(new PastPaperPreviewVo.OptionVo(o.path("label").asText(),o.path("content").asText()));List<PastPaperPreviewVo.ImageVo> imgs=new ArrayList<>();for(JsonNode i:tree(q.getImagesJson()))imgs.add(new PastPaperPreviewVo.ImageVo(i.path("sortOrder").asInt(),imageUrlService.accessUrl(i.path("sourceUrl").asText(null),i.path("storagePath").asText(null)),i.path("altText").asText(null)));return new PastPaperPreviewVo.QuestionVo(q.getQuestionOrder(),q.getQuestionType(),q.getStem(),opts,imgs);}
    private List<PastPaperListVo> mapPapers(List<PastPaperPaperRow> rows){return rows.stream().map(this::paperVo).toList();}
    private PastPaperListVo paperVo(PastPaperPaperRow r){List<PastPaperListVo.SubjectVo> subjects=new ArrayList<>();for(JsonNode s:tree(r.getSubjectsJson()))subjects.add(new PastPaperListVo.SubjectVo(s.path("id").asText(),s.path("name").asText()));List<String> types=labels(tree(r.getQuestionTypesJson()));boolean supported=!types.isEmpty()&&types.stream().allMatch(type->List.of("CHOICE","CASE","ESSAY").contains(type));PastPaperListVo.AccessVo practice=new PastPaperListVo.AccessVo(supported?"START":"BLOCKED",supported,supported?null:"PAST_PAPER_UNSUPPORTED_QUESTION_TYPE");PastPaperListVo.AccessVo exam=new PastPaperListVo.AccessVo(supported?(r.getActiveExamSessionId()==null?"START":"RESUME"):"BLOCKED",supported,supported?null:"PAST_PAPER_UNSUPPORTED_QUESTION_TYPE");return new PastPaperListVo(String.valueOf(r.getCollectionId()),String.valueOf(r.getRevisionId()),r.getCollectionName(),r.getCertificationName(),subjects,r.getQuestionCount(),r.getTotalReportScore(),r.getDurationMinutes(),types,r.getExamYear(),r.getExamMonth(),r.getPaperTypeCode(),r.getPaperTypeName(),practice,exam);}
    private void validatePracticeQuestions(List<PastPaperQuestionRow> questions) {
        if (questions.isEmpty()) throw practiceUnavailable("真题试卷为空");
        int expectedOrder = 1;
        for (PastPaperQuestionRow question : questions) {
            if (!Objects.equals(question.getQuestionOrder(), expectedOrder++)) throw practiceUnavailable("真题题序不连续");
            if (!Set.of("CHOICE", "CASE", "ESSAY").contains(question.getQuestionType())) throw practiceUnavailable("真题包含不支持的题型");
            if (question.getQuestionId() == null || question.getQuestionRevisionId() == null
                || question.getExamSubjectId() == null || question.getStem() == null || question.getStem().isBlank()
                || question.getAnswerJson() == null) {
                throw practiceUnavailable("真题快照源不完整");
            }
            JsonNode answer = tree(question.getAnswerJson());
            if (!"CHOICE".equals(question.getQuestionType())) continue;
            if (!"single".equals(answer.path("selection_mode").asText()) || labels(answer.path("value")).size() != 1) {
                throw practiceUnavailable("真题练习仅支持单选题");
            }
            List<String> optionLabels = new ArrayList<>();
            for (JsonNode option : tree(question.getOptionsJson())) {
                String label = option.path("label").asText();
                if (label.isBlank() || !optionLabels.add(label)) throw practiceUnavailable("真题选项标签不完整或重复");
            }
            if (optionLabels.isEmpty() || !optionLabels.contains(labels(answer.path("value")).getFirst())) {
                throw practiceUnavailable("真题答案不属于可选项");
            }
        }
    }
    /** Applies the existing stable question-level evidence-key fallback before freezing a practice snapshot. */
    private void normalizePracticeQuestion(PastPaperQuestionRow question) {
        if ((question.getEvidenceGroupKey() == null || question.getEvidenceGroupKey().isBlank()) && question.getQuestionId() != null) {
            question.setEvidenceGroupKey("Q:" + question.getQuestionId());
        }
    }
    private Snapshots snapshots(PastPaperQuestionRow q){Map<String,Object> p=new LinkedHashMap<>();p.put("questionType",q.getQuestionType());p.put("stem",q.getStem());p.put("selectionMode","CHOICE".equals(q.getQuestionType())?"single":null);p.put("options",tree(q.getOptionsJson()));p.put("images",tree(q.getImagesJson()));Map<String,Object> g=new LinkedHashMap<>();g.put("answer",tree(q.getAnswerJson()));g.put("rules",Map.of("questionType",q.getQuestionType()));g.put("analysis",q.getAnalysis());g.put("reportScore",q.getReportScore());return new Snapshots(versioned(AssessmentJsonSchema.PAST_PAPER_PRESENTATION,p),versioned(AssessmentJsonSchema.PAST_PAPER_GRADING,g),versioned(AssessmentJsonSchema.PAST_PAPER_KNOWLEDGE,Map.of("items",tree(q.getKnowledgeJson()))));}
    private PastPaperPaperRow requirePaper(long id){PastPaperPaperRow p=mapper.selectPaper(positive(id,"collectionId"));if(p==null)throw notFound("PAST_PAPER_NOT_FOUND","真题不存在或不可见");return p;} private PastPaperGoalRow goal(long u){PastPaperGoalRow g=mapper.selectActiveGoal(u);if(g==null)throw failure(409,"LEARNING_GOAL_NOT_ACTIVE","当前没有有效学习目标",false);return g;} private PastPaperSessionRow requirePracticeSession(long u,long id){PastPaperSessionRow s=mapper.selectSession(u,positive(id,"sessionId"));if(s==null||!"past_paper_practice".equals(s.getSessionType()))throw notFound("PAST_PAPER_PRACTICE_SESSION_NOT_FOUND","真题练习会话不存在");return s;} private PastPaperSessionRow lock(long u,long id){PastPaperSessionRow s=mapper.lockSession(u,positive(id,"sessionId"));if(s==null)throw notFound("PAST_PAPER_PRACTICE_SESSION_NOT_FOUND","真题练习会话不存在");return s;} private void requireType(PastPaperSessionRow s,String type){if(!type.equals(s.getSessionType()))throw notFound(sessionNotFoundCode(type),"真题会话不存在");} private String sessionNotFoundCode(String type){return "past_paper_practice".equals(type)?"PAST_PAPER_PRACTICE_SESSION_NOT_FOUND":"PAST_PAPER_EXAM_SESSION_NOT_FOUND";} private void requirePracticeActive(PastPaperSessionRow s){if(!"in_progress".equals(s.getStatus()))throw failure(409,"PAST_PAPER_PRACTICE_SESSION_NOT_ACTIVE","当前练习不可作答",false);} private void validateAnswer(PastPaperQuestionRow q,String selected){boolean found=false;JsonNode p=q.getPresentationSnapshot()==null?tree(q.getOptionsJson()):tree(q.getPresentationSnapshot()).path("options");for(JsonNode o:p)if(selected.equals(o.path("label").asText()))found=true;if(!found)throw practiceInvalid("answer.value","答案不属于冻结选项");} private String answer(PastPaperAnswerBo command){if(command==null||command.getAnswer()==null||command.getAnswer().getValue()==null||command.getAnswer().getValue().size()!=1||command.getAnswer().getValue().getFirst()==null||command.getAnswer().getValue().getFirst().isBlank())throw practiceInvalid("answer.value","必须且只能选择一个有效选项");return command.getAnswer().getValue().getFirst();}
    private String path(String type,long id){return "/learning/session/"+type.replace('_','-')+"?sessionId="+id;} private String jobKey(long id){return "PAST_PAPER:"+id;}
    private StartPastPaperPracticeVo practiceStartVo(PastPaperSessionRow s){return new StartPastPaperPracticeVo(String.valueOf(s.getId()),s.getTotalCount(),path(s.getSessionType(),s.getId()));} private CompletePastPaperPracticeVo completePracticeVo(PastPaperSessionRow s){return new CompletePastPaperPracticeVo(String.valueOf(s.getId()),"COMPLETED",s.getAnsweredCount(),"/learning/question-bank/past-papers");} private PastPaperRevealVo replayReveal(PastPaperIdempotencyRow r,String hash){return replay(r,hash,PastPaperRevealVo.class);} private <T> T replay(PastPaperIdempotencyRow r,String hash,Class<T> type){if(r==null||!hash.equals(r.getPayloadHash())||!"succeeded".equals(r.getStatus()))throw failure(409,type.getSimpleName().contains("PastPaperPractice")?"PAST_PAPER_PRACTICE_IDEMPOTENCY_CONFLICT":"PAST_PAPER_IDEMPOTENCY_CONFLICT","请求号已被使用",false);try{return jsonMapper.treeToValue(tree(r.getResponseBody()).path("response").path("data"),type);}catch(Exception e){throw failure(500,type.getSimpleName().contains("PastPaperPractice")?"PAST_PAPER_PRACTICE_SYSTEM_FAILURE":"PAST_PAPER_SYSTEM_FAILURE","真题响应回放失败",true);}}
    private JsonNode tree(String v){try{return v==null?jsonMapper.createObjectNode():jsonMapper.readTree(v);}catch(Exception e){throw failure(500,"PAST_PAPER_SYSTEM_FAILURE","冻结数据损坏",true);}} private String write(Object v){try{return jsonMapper.writeValueAsString(v);}catch(Exception e){throw failure(500,"PAST_PAPER_SYSTEM_FAILURE","真题数据序列化失败",true);}} private String versioned(AssessmentJsonSchema schema,Map<String,?> fields){return VersionedJsonDocumentFactory.json(jsonMapper,schema,fields);} private String response(Object data){return versioned(AssessmentJsonSchema.PAST_PAPER_IDEMPOTENCY,Map.of("response",Map.of("code",200,"msg","操作成功","data",data)));} private String answerJson(String value){return versioned(AssessmentJsonSchema.PAST_PAPER_CHOICE_ANSWER,Map.of("value",value==null?List.of():List.of(value)));} private String singleValue(JsonNode n){List<String> x=labels(n.path("value"));return x.isEmpty()?null:x.getFirst();} private List<String> labels(JsonNode n){List<String> out=new ArrayList<>();if(n.isArray())for(JsonNode x:n)out.add(x.asText());return out;} private OffsetDateTime timestamp(String value){return OffsetDateTime.parse(value.replace(' ','T'));} private String requestId(String r){try{if(r==null||!r.equals(r.trim())||r.length()!=36)return failRequest();return UUID.fromString(r).toString();}catch(Exception e){throw invalid("X-Request-Id","INVALID_FORMAT","请求号必须是UUID");}} private String failRequest(){throw invalid("X-Request-Id","INVALID_FORMAT","请求号必须是UUID");} private long id(String v,String f){try{long x=Long.parseLong(v);return positive(x,f);}catch(Exception e){throw invalid(f,"INVALID_FORMAT","必须是十进制正整数");}} private Long optionalId(String v,String f){return v==null||v.trim().isEmpty()?null:id(v.trim(),f);} private int positive(int v,String f){if(v<1)throw invalid(f,"OUT_OF_RANGE","必须大于0");return v;} private long positive(long v,String f){if(v<1)throw invalid(f,"OUT_OF_RANGE","必须大于0");return v;} private PastPaperException invalid(String f,String c,String m){return new PastPaperException(400,"PAST_PAPER_REQUEST_INVALID","请求参数格式不正确",false,List.of(new PastPaperErrorVo.FieldErrorVo(f,c,m)),null);} private PastPaperException practiceInvalid(String f,String m){return new PastPaperException(400,"PAST_PAPER_PRACTICE_REQUEST_INVALID","请求参数格式不正确",false,List.of(new PastPaperErrorVo.FieldErrorVo(f,"INVALID_FORMAT",m)),null);} private PastPaperException practiceUnavailable(String m){return failure(422,"PAST_PAPER_PRACTICE_UNAVAILABLE",m,false);} private PastPaperException notFound(String c,String m){return failure(404,c,m,false);} private PastPaperException version(){return failure(409,"PAST_PAPER_EXAM_SESSION_VERSION_CONFLICT","会话版本已变化",true);} private PastPaperException failure(int s,String c,String m,boolean r){return new PastPaperException(s,c,m,r);} private String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}} private record Snapshots(String presentation,String grading,String knowledge){}
}
