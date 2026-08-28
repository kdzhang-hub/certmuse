package org.dromara.certmuse.learning.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HtmlUtil;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.learning.domain.*;
import org.dromara.certmuse.learning.domain.bo.CreateMistakeCorrectionSessionBo;
import org.dromara.certmuse.learning.domain.bo.SubmitMistakeCorrectionItemBo;
import org.dromara.certmuse.learning.domain.vo.*;
import org.dromara.certmuse.learning.mapper.MistakeReviewMapper;
import org.dromara.certmuse.learning.service.MistakeReviewService;
import org.dromara.certmuse.learning.support.LearningJsonSchema;
import org.dromara.certmuse.learning.support.MistakeReviewLegacyIdempotencyPayloads;
import org.dromara.certmuse.learning.support.MistakeReviewException;
import org.dromara.certmuse.assessment.support.SubjectiveAnswerValidator;
import org.dromara.certmuse.assessment.support.SubjectiveGradingTasks;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Implements the V1 frozen original-question correction loop. */
@Service @RequiredArgsConstructor(onConstructor_ = @Autowired)
public class MistakeReviewServiceImpl implements MistakeReviewService {
    private static final String CREATE = "CREATE_MISTAKE_CORRECTION_SESSION";
    private static final String SUBMIT = "SUBMIT_MISTAKE_CORRECTION_ITEM";
    private static final String COMPLETE = "COMPLETE_MISTAKE_CORRECTION_SESSION";
    private final MistakeReviewMapper mapper;
    private final JsonMapper jsonMapper;
    private final QuestionImageUrlService imageUrlService;
    private final SubjectiveGradingTasks subjectiveTasks;

    /** Compatibility constructor retained for existing choice-only unit tests. */
    public MistakeReviewServiceImpl(MistakeReviewMapper mapper, JsonMapper jsonMapper, QuestionImageUrlService imageUrlService) {
        this(mapper, jsonMapper, imageUrlService, null);
    }

    @Override
    public MistakeListVo list(long userId, String keyword, String status, String knowledgePointId, String source,
                              Integer minWrongCount, Integer pageNum, Integer pageSize) {
        long goalId = goal(userId); String normalizedStatus = status == null ? "PENDING_CORRECTION" : status;
        validateList(keyword, normalizedStatus, knowledgePointId, source, minWrongCount);
        Map<Long, List<MistakeErrorRow>> grouped = group(mapper.selectErrors(userId, goalId));
        List<MistakeListVo.RowVo> rows = grouped.values().stream().map(this::row).filter(x -> x.status().equals(normalizedStatus))
            .filter(x -> source == null || x.sources().contains(source)).filter(x -> minWrongCount == null || x.wrongCount() >= minWrongCount)
            .filter(x -> keyword == null || keyword.isBlank() || contains(x, keyword.trim())).filter(x -> knowledgePointId == null || x.knowledgePoints().stream().anyMatch(k -> k.id().equals(knowledgePointId)))
            .sorted(Comparator.comparing(MistakeListVo.RowVo::lastWrongAt, Comparator.reverseOrder()).thenComparing(MistakeListVo.RowVo::questionId)).toList();
        int page = pageNum == null ? 1 : pageNum, size = pageSize == null ? 10 : pageSize;
        if (page < 1 || size < 1 || size > 100) throw invalid("page", "OUT_OF_RANGE", "分页参数不合法");
        long from = (long) (page - 1) * size;
        return new MistakeListVo(rows.stream().skip(from).limit(size).toList(), rows.size());
    }

    @Override
    public MistakeDetailVo detail(long userId, long questionId) {
        if (questionId <= 0) throw invalid("questionId", "INVALID_FORMAT", "题目ID不合法");
        List<MistakeErrorRow> rows = group(mapper.selectErrors(userId, goal(userId))).get(questionId);
        if (rows == null) throw failure(404, "QUESTION_NOT_FOUND", "题目不存在");
        MistakeListVo.RowVo list = row(rows); MistakeErrorRow primary = rows.getFirst();
        List<MistakeDetailVo.HistoryVo> history = rows.stream().map(x -> new MistakeDetailVo.HistoryVo(
            String.valueOf(x.getAttemptId()), x.getDetectedTime(), source(x.getSessionType()),
            answerLabels(x.getAnswerData()), Boolean.TRUE.equals(x.getSkipped()))).toList();
        try {
            JsonNode p = optionalTree(primary.getPresentationSnapshot()), g = optionalTree(primary.getGradingSnapshot());
            boolean available = correctable(p, g);
            MistakeDetailVo.QuestionVo question = available ? question(p, g) : null;
            return new MistakeDetailVo(String.valueOf(questionId), list.stemPreview(), list.status(), list.wrongCount(), list.skipCount(), list.sources(), list.lastWrongAt(), question,
                available ? answerLabels(primary.getAnswerData()) : null, available ? texts(g.path("answer").path("value")) : null,
                available ? g.path("analysis").asText(null) : null, history, available, available ? null : block(primary));
        } catch (RuntimeException ex) {
            if (ex instanceof MistakeReviewException) throw ex;
            return new MistakeDetailVo(String.valueOf(questionId), list.stemPreview(), list.status(), list.wrongCount(), list.skipCount(), list.sources(), list.lastWrongAt(), null, null, null, null, history, false, "QUESTION_UNAVAILABLE");
        }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public CreateMistakeCorrectionSessionVo create(long userId, String requestId, CreateMistakeCorrectionSessionBo command) {
        String request = requestId(requestId); List<Long> ids = ids(command.getQuestionIds());
        PayloadHashes hashes = hashes(idempotencyPayload(Map.of("userId", userId, "questionIds", ids)),
            MistakeReviewLegacyIdempotencyPayloads.create(jsonMapper, userId, ids));
        CreateMistakeCorrectionSessionVo replay = replay(CREATE, request, hashes, CreateMistakeCorrectionSessionVo.class); if (replay != null) return replay;
        if (mapper.insertIdempotency(IdUtil.getSnowflakeNextId(), CREATE, request, hashes.canonical()) == 0) return requireReplay(CREATE, request, hashes, CreateMistakeCorrectionSessionVo.class);
        long goalId = lockGoal(userId);
        MistakeSessionRow active = mapper.selectActiveSession(userId, goalId);
        if (active != null) throw conflict("MISTAKE_CORRECTION_IN_PROGRESS", new MistakeErrorVo.DetailsVo(String.valueOf(active.getId()), null, null));
        Long rule = mapper.selectUniquePublishedRuleVersion(); if (rule == null) throw failure(503, "MISTAKE_RULE_VERSION_UNAVAILABLE", "订正规则暂不可用");
        Map<Long, List<MistakeErrorRow>> source = group(mapper.selectErrors(userId, goalId)); List<MistakeErrorRow> selected = new ArrayList<>();
        for (Long id : ids) { List<MistakeErrorRow> records = source.get(id); if (records == null || !"PENDING_CORRECTION".equals(row(records).status())) throw pending(id, "NOT_PENDING_CORRECTION"); MistakeErrorRow primary = records.getFirst(); if (!correctable(primary)) throw pending(id, block(primary)); selected.add(primary); }
        long sessionId = IdUtil.getSnowflakeNextId();
        if (mapper.insertSession(sessionId, userId, goalId, rule, request) == 0) {
            MistakeSessionRow concurrent = mapper.selectActiveSession(userId, goalId);
            if (concurrent != null) throw conflict("MISTAKE_CORRECTION_IN_PROGRESS", new MistakeErrorVo.DetailsVo(String.valueOf(concurrent.getId()), null, null));
            throw failure(409, "MISTAKE_IDEMPOTENCY_CONFLICT", "请求号冲突");
        }
        for (int i = 0; i < selected.size(); i++) mapper.insertSessionQuestion(IdUtil.getSnowflakeNextId(), sessionId, selected.get(i), i + 1);
        CreateMistakeCorrectionSessionVo result = new CreateMistakeCorrectionSessionVo(String.valueOf(sessionId), selected.size(), "/learning/session/correction?sessionId=" + sessionId);
        mapper.completeIdempotency(requireId(CREATE, request), sessionId, json(result)); return result;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public MistakeCorrectionSessionVo session(long userId, long sessionId) {
        MistakeSessionRow row = owned(sessionId, userId); if ("created".equals(row.getStatus())) { mapper.startSession(sessionId, userId); row = owned(sessionId, userId); }
        if ("completed".equals(row.getStatus())) return new MistakeCorrectionSessionVo(String.valueOf(sessionId), "COMPLETED", "错题订正", row.getTotalCount(), row.getSubmittedCount(), List.of(), "RETURN_MISTAKES", "/learning/mistakes");
        List<MistakeCorrectionSessionVo.NavigationVo> nav = mapper.selectItems(sessionId, userId).stream().map(x -> new MistakeCorrectionSessionVo.NavigationVo(x.getQuestionOrder(), x.getAttemptId() == null ? "UNANSWERED" : Boolean.TRUE.equals(x.getCorrect()) ? "SUBMITTED_CORRECT" : "SUBMITTED_INCORRECT")).toList();
        return new MistakeCorrectionSessionVo(String.valueOf(sessionId), "IN_PROGRESS", "错题订正", row.getTotalCount(), row.getSubmittedCount(), nav, "CONTINUE_CORRECTION", null);
    }

    @Override
    public MistakeCorrectionItemVo item(long userId, long sessionId, int questionOrder) {
        if (questionOrder < 1) throw invalid("questionOrder", "OUT_OF_RANGE", "题序不合法"); MistakeSessionItemRow x = mapper.selectItem(sessionId, userId, questionOrder);
        if (x == null) throw failure(404, "MISTAKE_CORRECTION_SESSION_NOT_FOUND", "订正会话不存在");
        refreshSubjectiveAttempt(x, userId);
        x = mapper.selectItem(sessionId, userId, questionOrder);
        JsonNode p = tree(x.getPresentationSnapshot()), g = tree(x.getGradingSnapshot());
        MistakeCorrectionItemVo.SubmissionVo submission = x.getAttemptId() == null ? null : new MistakeCorrectionItemVo.SubmissionVo(texts(tree(x.getAnswerData()).path("value")), Boolean.TRUE.equals(x.getCorrect()), texts(g.path("answer").path("value")), g.path("analysis").asText(null));
        return new MistakeCorrectionItemVo(String.valueOf(sessionId), questionOrder, x.getTotalCount(), new MistakeCorrectionItemVo.QuestionVo(p.path("questionType").asText(), p.path("stem").asText(), selection(g), options(p), images(p)), submission);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public SubmitMistakeCorrectionItemVo submit(long userId, long sessionId, int order, String requestId, SubmitMistakeCorrectionItemBo command) {
        String request = requestId(requestId); if (order < 1) throw invalid("questionOrder", "OUT_OF_RANGE", "题序不合法");
        List<String> selected = command.getAnswer().getValue() == null ? List.of() : command.getAnswer().getValue(); PayloadHashes hashes = hashes(idempotencyPayload(Map.of(
            "userId", userId, "sessionId", sessionId, "questionOrder", order, "answer", selected)),
            MistakeReviewLegacyIdempotencyPayloads.submit(jsonMapper, userId, sessionId, order, selected));
        SubmitMistakeCorrectionItemVo replay = replay(SUBMIT, request, hashes, SubmitMistakeCorrectionItemVo.class); if (replay != null) return replay;
        if (mapper.insertIdempotency(IdUtil.getSnowflakeNextId(), SUBMIT, request, hashes.canonical()) == 0) return requireReplay(SUBMIT, request, hashes, SubmitMistakeCorrectionItemVo.class);
        MistakeSessionRow session = mapper.lockOwnedSession(sessionId, userId); if (session == null) throw failure(404, "MISTAKE_CORRECTION_SESSION_NOT_FOUND", "订正会话不存在"); if (!"in_progress".equals(session.getStatus())) throw failure(409, "MISTAKE_CORRECTION_SESSION_NOT_ACTIVE", "订正会话不可继续");
        MistakeSessionItemRow item = mapper.selectItem(sessionId, userId, order); if (item == null) throw invalid("questionOrder", "OUT_OF_RANGE", "题序不存在"); if (item.getAttemptId() != null) throw failure(409, "MISTAKE_CORRECTION_ITEM_ALREADY_SUBMITTED", "该题已提交");
        JsonNode p = tree(item.getPresentationSnapshot()), g = tree(item.getGradingSnapshot()); boolean subjective = subjective(p); List<String> normalized = subjective ? List.of(subjectiveText(command)) : normalize(selected, p, selection(g)); List<String> answer = texts(g.path("answer").path("value")); boolean correct = !subjective && normalized.equals(normalize(answer, p, selection(g)));
        long attempt = IdUtil.getSnowflakeNextId(); if (mapper.insertAttempt(attempt, item, request) == 0) throw failure(409, "MISTAKE_CORRECTION_ITEM_ALREADY_SUBMITTED", "该题已提交");
        if (subjective) {
            mapper.insertPendingSubjectiveAttemptAnswer(IdUtil.getSnowflakeNextId(), attempt, answerDocument(normalized, selection(g)));
            SubjectiveGradingTasks.TaskState state = subjectiveTasks.ensure(item.getQuestionRevisionId(), item.getSessionQuestionId(), attempt, item.getPresentationSnapshot(), item.getGradingSnapshot(), item.getKnowledgeSnapshot(), normalized.getFirst());
            settleSubjectiveAttempt(item, userId, session.getGoalId(), attempt, request, state);
        } else mapper.insertAttemptAnswer(IdUtil.getSnowflakeNextId(), attempt, answerDocument(normalized, selection(g)), correct);
        if (correct) closeErrors(userId, session.getGoalId(), item.getQuestionId(), attempt, request);
        SubmitMistakeCorrectionItemVo result = new SubmitMistakeCorrectionItemVo(order, mapper.countSubmitted(sessionId), new SubmitMistakeCorrectionItemVo.FeedbackVo(normalized, correct, answer, g.path("analysis").asText(null), correct ? "CORRECTED" : "PENDING_CORRECTION"));
        mapper.completeIdempotency(requireId(SUBMIT, request), attempt, json(result)); return result;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public CompleteMistakeCorrectionSessionVo complete(long userId, long sessionId, String requestId) {
        String request = requestId(requestId); PayloadHashes hashes = hashes(idempotencyPayload(Map.of(
            "userId", userId, "sessionId", sessionId)),
            MistakeReviewLegacyIdempotencyPayloads.complete(jsonMapper, userId, sessionId));
        CompleteMistakeCorrectionSessionVo replay = replay(COMPLETE, request, hashes, CompleteMistakeCorrectionSessionVo.class); if (replay != null) return replay;
        if (mapper.insertIdempotency(IdUtil.getSnowflakeNextId(), COMPLETE, request, hashes.canonical()) == 0) return requireReplay(COMPLETE, request, hashes, CompleteMistakeCorrectionSessionVo.class);
        MistakeSessionRow row = mapper.lockOwnedSession(sessionId, userId); if (row == null) throw failure(404, "MISTAKE_CORRECTION_SESSION_NOT_FOUND", "订正会话不存在");
        if (!"completed".equals(row.getStatus())) {
            if (!("created".equals(row.getStatus()) || "in_progress".equals(row.getStatus()))) {
                throw failure(409, "MISTAKE_CORRECTION_SESSION_NOT_ACTIVE", "订正会话不可继续");
            }
            if ("created".equals(row.getStatus())) mapper.startSession(sessionId, userId);
            if (mapper.countPendingGrading(sessionId) > 0) throw failure(409, "MISTAKE_CORRECTION_GRADING_PENDING", "主观题正在AI评分");
            mapper.submitSession(sessionId, userId);
            mapper.settleSession(sessionId, userId);
            mapper.completeSession(sessionId, userId);
        }
        CompleteMistakeCorrectionSessionVo result = new CompleteMistakeCorrectionSessionVo(String.valueOf(sessionId), "COMPLETED", mapper.countSubmitted(sessionId), "/learning/mistakes"); mapper.completeIdempotency(requireId(COMPLETE, request), sessionId, json(result)); return result;
    }

    private Map<Long,List<MistakeErrorRow>> group(List<MistakeErrorRow> rows) { Map<Long,List<MistakeErrorRow>> out = new LinkedHashMap<>(); for (MistakeErrorRow x : rows) out.computeIfAbsent(x.getQuestionId(), k -> new ArrayList<>()).add(x); return out; }
    private MistakeListVo.RowVo row(List<MistakeErrorRow> rows) { MistakeErrorRow p = rows.getFirst(); long wrong = rows.stream().filter(x -> !Boolean.TRUE.equals(x.getSkipped())).map(MistakeErrorRow::getAttemptId).distinct().count(), skip = rows.stream().filter(x -> Boolean.TRUE.equals(x.getSkipped())).map(MistakeErrorRow::getAttemptId).distinct().count(); boolean pending = rows.stream().anyMatch(x -> Set.of("unresolved","correcting").contains(x.getErrorStatus())); boolean corrected = !pending && rows.stream().anyMatch(x -> "closed".equals(x.getErrorStatus()) && "correction_correct".equals(x.getStatusReason())); Map<String,String> sourceTimes = new HashMap<>(); for (MistakeErrorRow error : rows) sourceTimes.merge(source(error.getSessionType()), error.getDetectedTime(), (a,b) -> a.compareTo(b) >= 0 ? a : b); List<String> sources = sourceTimes.entrySet().stream().sorted(Map.Entry.<String,String>comparingByValue().reversed().thenComparing(Map.Entry::getKey)).map(Map.Entry::getKey).toList(); return new MistakeListVo.RowVo(String.valueOf(p.getQuestionId()), preview(p.getPresentationSnapshot()), knowledge(p.getCurrentKnowledgePoints()), pending ? "PENDING_CORRECTION" : corrected ? "CORRECTED" : "HIDDEN", wrong, skip, sources, p.getDetectedTime(), correctable(p), correctable(p) ? null : block(p)); }
    private boolean contains(MistakeListVo.RowVo x,String key){return x.stemPreview().contains(key)||x.knowledgePoints().stream().anyMatch(k->k.name().contains(key));}
    private List<MistakeListVo.KnowledgePointVo> knowledge(String json){ try { return tree(json).valueStream().map(x -> new MistakeListVo.KnowledgePointVo(x.path("id").asText(), x.path("name").asText())).filter(x->!x.name().isBlank()).toList(); }catch(Exception e){return List.of();} }
    private boolean correctable(MistakeErrorRow row){return correctable(optionalTree(row.getPresentationSnapshot()), optionalTree(row.getGradingSnapshot()));}
    private boolean correctable(JsonNode p, JsonNode g){if(p == null || g == null || p.path("stem").asText().isBlank())return false;String type=p.path("questionType").asText();return ("CHOICE".equals(type)&&!p.path("options").isEmpty()&&!texts(g.path("answer").path("value")).isEmpty()&&Set.of("single","multiple").contains(selection(g))) || Set.of("CASE","ESSAY").contains(type);}
    private String block(MistakeErrorRow row){try{return "CHOICE".equals(tree(row.getPresentationSnapshot()).path("questionType").asText())?"QUESTION_UNAVAILABLE":"UNSUPPORTED_QUESTION_TYPE";}catch(Exception e){return "QUESTION_UNAVAILABLE";}}
    private MistakeDetailVo.QuestionVo question(JsonNode p,JsonNode g){return new MistakeDetailVo.QuestionVo(p.path("questionType").asText(),p.path("stem").asText(),selection(g),optionsDetail(p),imagesDetail(p));}
    private List<MistakeDetailVo.OptionVo> optionsDetail(JsonNode p){return p.path("options").valueStream().map(x->new MistakeDetailVo.OptionVo(x.path("label").asText(),x.path("content").asText())).toList();}
    private List<MistakeCorrectionItemVo.OptionVo> options(JsonNode p){return p.path("options").valueStream().map(x->new MistakeCorrectionItemVo.OptionVo(x.path("label").asText(),x.path("content").asText())).toList();}
    private List<MistakeDetailVo.ImageVo> imagesDetail(JsonNode p){return p.path("images").<JsonNode>valueStream().sorted(Comparator.comparingInt((JsonNode x)->x.path("sortOrder").asInt()).thenComparing(x->x.path("id").asText())).map(x->new MistakeDetailVo.ImageVo(imageUrlService.accessUrl(x.path("sourceUrl").asText(null),x.path("storagePath").asText(null)),x.path("alt").asText(x.path("altText").asText(null)),x.path("sortOrder").asInt())).toList();}
    private List<MistakeCorrectionItemVo.ImageVo> images(JsonNode p){return p.path("images").<JsonNode>valueStream().sorted(Comparator.comparingInt((JsonNode x)->x.path("sortOrder").asInt()).thenComparing(x->x.path("id").asText())).map(x->new MistakeCorrectionItemVo.ImageVo(imageUrlService.accessUrl(x.path("sourceUrl").asText(null),x.path("storagePath").asText(null)),x.path("alt").asText(x.path("altText").asText(null)),x.path("sortOrder").asInt())).toList();}
    private String selection(JsonNode g){return g.path("rules").path("selectionMode").asText(g.path("answer").path("selection_mode").asText("single"));}
    private boolean subjective(JsonNode presentation){return Set.of("CASE","ESSAY").contains(presentation.path("questionType").asText());}
    private String subjectiveText(SubmitMistakeCorrectionItemBo command){String value=command.getAnswer().getText();if(value==null||value.isBlank()||value.codePointCount(0,value.length())>SubjectiveAnswerValidator.MAX_CODE_POINTS)throw invalid("answer.text","INVALID_FORMAT","主观题答案不合法");return value;}
    private void refreshSubjectiveAttempt(MistakeSessionItemRow item,long userId){if(!subjective(tree(item.getPresentationSnapshot()))||item.getAttemptId()==null||!"pending".equals(item.getGradingStatus()))return;List<String> answer=texts(tree(item.getAnswerData()).path("value"));if(answer.isEmpty())return;SubjectiveGradingTasks.TaskState state=subjectiveTasks.ensure(item.getQuestionRevisionId(),item.getSessionQuestionId(),item.getAttemptId(),item.getPresentationSnapshot(),item.getGradingSnapshot(),item.getKnowledgeSnapshot(),answer.getFirst());settleSubjectiveAttempt(item,userId,item.getGoalId(),item.getAttemptId(),"ai-settlement-"+item.getAttemptId(),state);}
    private void settleSubjectiveAttempt(MistakeSessionItemRow item,long userId,long goalId,long attempt,String request,SubjectiveGradingTasks.TaskState state){if("SUCCEEDED".equals(state.status())){SubjectiveAnswerValidator.GradingResult result=SubjectiveAnswerValidator.gradingResult(tree(state.rubric()),tree(state.result()));if(result==null){mapper.failSubjectiveAttempt(attempt,json(Map.of("errorCode","AI_OUTPUT_INVALID")));return;}java.math.BigDecimal rate=result.scoreRate();mapper.scoreSubjectiveAttempt(attempt,rate.toPlainString(),state.result());if(rate.compareTo(new java.math.BigDecimal("0.40"))>=0)closeErrors(userId,goalId,item.getQuestionId(),attempt,request);}else if("FAILED".equals(state.status()))mapper.failSubjectiveAttempt(attempt,json(Map.of("errorCode",state.errorCode()==null?"AI_GRADING_FAILED":state.errorCode())));}
    private void closeErrors(long userId,long goalId,long questionId,long attempt,String request){for(Long errorId:mapper.lockPendingErrorIds(userId,goalId,questionId)){mapper.insertCorrection(IdUtil.getSnowflakeNextId(),errorId,attempt,request+":"+errorId);mapper.closeError(errorId,userId);}}
    private List<String> normalize(List<String> input,JsonNode p,String mode){if(input==null||input.isEmpty()||input.stream().anyMatch(x->x==null||x.isBlank())||new HashSet<>(input).size()!=input.size()||("single".equals(mode)&&input.size()!=1))throw invalid("answer.value","INVALID_FORMAT","答案不合法"); Set<String> allowed=new HashSet<>();p.path("options").forEach(x->allowed.add(x.path("label").asText()));if(!allowed.containsAll(input))throw invalid("answer.value","INVALID_FORMAT","答案不属于当前题目");return p.path("options").valueStream().map(x->x.path("label").asText()).filter(input::contains).toList();}
    private List<Long> ids(List<String> values){if(values==null||values.isEmpty()||values.size()>20)throw invalid("questionIds","OUT_OF_RANGE","题目数量不合法");List<Long> out=new ArrayList<>();for(String v:values)try{long x=Long.parseLong(v);if(x<=0||out.contains(x))throw new NumberFormatException();out.add(x);}catch(Exception e){throw invalid("questionIds","INVALID_FORMAT","题目ID不合法");}return out;}
    private long goal(long userId){Long id=mapper.selectActiveGoal(userId);if(id==null)throw failure(422,"LEARNING_GOAL_NOT_ACTIVE","没有有效学习目标");return id;} private long lockGoal(long userId){Long id=mapper.lockActiveGoal(userId);if(id==null)throw failure(422,"LEARNING_GOAL_NOT_ACTIVE","没有有效学习目标");return id;}
    private String source(String s){return switch(s){case "initial_diagnosis"->"INITIAL_DIAGNOSIS";case "daily_task","task_test"->"DAILY_TASK";case "self_practice"->"SELF_PRACTICE";case "simulation"->"SIMULATION";case "past_paper_practice","past_paper_exam"->"PAST_PAPER";default->"UNKNOWN";};}
    private String preview(String snapshot){try{return HtmlUtil.cleanHtmlTag(tree(snapshot).path("stem").asText()).replaceAll("\\s+"," ").trim().codePoints().limit(100).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();}catch(Exception e){return "";}}
    private JsonNode tree(String raw){try{return jsonMapper.readTree(raw==null?"{}":raw);}catch(Exception e){throw failure(500,"MISTAKE_OPERATION_FAILED","读取冻结题目失败",e);}}
    private JsonNode optionalTree(String raw){try{return raw == null ? null : jsonMapper.readTree(raw);}catch(Exception ignored){return null;}}
    private List<String> answerLabels(String answerData){JsonNode answer = optionalTree(answerData);return answer == null ? List.of() : texts(answer.path("value"));}
    private List<String> texts(JsonNode n){return n.isArray()?n.valueStream().map(JsonNode::asText).toList():List.of();}
    private String answerDocument(List<String> value, String selectionMode) { return VersionedJsonDocumentFactory.json(jsonMapper, LearningJsonSchema.MISTAKE_CORRECTION_CHOICE_ANSWER, Map.of("answer_type", "option_keys", "selection_mode", selectionMode, "value", value)); }
    private String requestId(String x){if(x==null||!x.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))throw invalid("X-Request-Id","INVALID_FORMAT","请求号不合法");return x.toLowerCase(Locale.ROOT);} private String hash(Object x){return DigestUtil.sha256Hex(json(x));} private String json(Object x){return VersionedJsonDocumentFactory.json(jsonMapper, LearningJsonSchema.MISTAKE_REVIEW, Map.of("data",x));}
    private Map<String,Object> idempotencyPayload(Map<String,?> fields){return VersionedJsonDocumentFactory.flatFields(VersionedJsonDocumentFactory.IDEMPOTENCY_SCHEMA_VERSION_FIELD,LearningJsonSchema.MISTAKE_REVIEW,fields);} private PayloadHashes hashes(Object canonical,String legacy){return new PayloadHashes(hash(canonical),DigestUtil.sha256Hex(legacy));}
    private <T>T replay(String action,String request,PayloadHashes hashes,Class<T> type){MistakeIdempotencyRow x=mapper.selectIdempotency(action,request);if(x==null)return null;if(!hashes.matches(x.getPayloadHash()))throw failure(409,"MISTAKE_IDEMPOTENCY_CONFLICT","请求号冲突");if(!"succeeded".equals(x.getStatus()))throw failure(409,"MISTAKE_IDEMPOTENCY_CONFLICT","请求正在处理");try{return jsonMapper.readValue(tree(x.getResponseBody()).path("data").toString(),type);}catch(Exception e){throw failure(500,"MISTAKE_OPERATION_FAILED","幂等结果读取失败",e);}} private <T>T requireReplay(String a,String r,PayloadHashes h,Class<T> t){T x=replay(a,r,h,t);if(x==null)throw failure(409,"MISTAKE_IDEMPOTENCY_CONFLICT","请求正在处理");return x;} private long requireId(String a,String r){MistakeIdempotencyRow x=mapper.selectIdempotency(a,r);if(x==null)throw failure(500,"MISTAKE_OPERATION_FAILED","幂等记录不存在");return x.getId();}
    private MistakeSessionRow owned(long id,long user){MistakeSessionRow x=mapper.selectOwnedSession(id,user);if(x==null)throw failure(404,"MISTAKE_CORRECTION_SESSION_NOT_FOUND","订正会话不存在");return x;} private void validateList(String k,String st,String kp,String src,Integer n){if(k!=null&&(k.trim().isEmpty()||k.trim().length()>100))throw invalid("keyword","INVALID_FORMAT","关键字不合法");if(!Set.of("PENDING_CORRECTION","CORRECTED").contains(st))throw invalid("status","INVALID_FORMAT","状态不合法");if(src!=null&&!Set.of("INITIAL_DIAGNOSIS","DAILY_TASK","SELF_PRACTICE","SIMULATION").contains(src))throw invalid("source","INVALID_FORMAT","来源不合法");if(n!=null&&n!=2&&n!=3)throw invalid("minWrongCount","OUT_OF_RANGE","次数不合法");if(kp!=null)try{if(Long.parseLong(kp)<=0)throw new NumberFormatException();}catch(Exception e){throw invalid("knowledgePointId","INVALID_FORMAT","知识点ID不合法");}}
    private MistakeReviewException invalid(String f,String c,String m){return new MistakeReviewException(400,"MISTAKE_REQUEST_INVALID",m,false,List.of(new MistakeErrorVo.FieldErrorVo(f,c,m)),null,null);} private MistakeReviewException pending(long id,String reason){return new MistakeReviewException("UNSUPPORTED_QUESTION_TYPE".equals(reason)?422:422,"UNSUPPORTED_QUESTION_TYPE".equals(reason)?"MISTAKE_CORRECTION_UNSUPPORTED_TYPE":"QUESTION_UNAVAILABLE".equals(reason)?"MISTAKE_QUESTION_UNAVAILABLE":"MISTAKE_NOT_PENDING_CORRECTION","题目不可订正",false,List.of(),new MistakeErrorVo.DetailsVo(null,String.valueOf(id),reason),null);} private MistakeReviewException conflict(String code,MistakeErrorVo.DetailsVo d){return new MistakeReviewException(409,code,"当前错题订正状态不可继续",false,List.of(),d,null);} private MistakeReviewException failure(int s,String c,String m){return new MistakeReviewException(s,c,m);} private MistakeReviewException failure(int s,String c,String m,Throwable e){return new MistakeReviewException(s,c,m,true,List.of(),null,e);} private record PayloadHashes(String canonical,String legacy){private boolean matches(String stored){return canonical.equals(stored)||legacy.equals(stored);}}
}
