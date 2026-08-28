package org.dromara.certmuse.assessment.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.DiagnosticCountRow;
import org.dromara.certmuse.assessment.domain.DiagnosticGoalRow;
import org.dromara.certmuse.assessment.domain.DiagnosticIdempotencyRow;
import org.dromara.certmuse.assessment.domain.DiagnosticItemRow;
import org.dromara.certmuse.assessment.domain.DiagnosticJobRow;
import org.dromara.certmuse.assessment.domain.DiagnosticReportRow;
import org.dromara.certmuse.assessment.domain.DiagnosticRevisionRow;
import org.dromara.certmuse.assessment.domain.DiagnosticSessionRow;
import org.dromara.certmuse.assessment.domain.DiagnosticTimerLeaseRow;
import org.dromara.certmuse.assessment.domain.DiagnosticTimingRow;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticDraftBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticFinishBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticPauseBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticStartBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticTimerEventBo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticDraftVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticErrorVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticFinishCheckVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticFinishVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticItemVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticPreflightVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticReportVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticSessionVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticStartVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticStatusVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticTimerEventVo;
import org.dromara.certmuse.assessment.mapper.DiagnosticMapper;
import org.dromara.certmuse.assessment.service.DiagnosticService;
import org.dromara.certmuse.assessment.support.AssessmentJsonSchema;
import org.dromara.certmuse.assessment.support.DiagnosticException;
import org.dromara.certmuse.assessment.support.SubjectiveAnswerValidator;
import org.dromara.certmuse.shared.schema.SharedJsonSchema;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.dromara.certmuse.question.service.CollectionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Transactional initial diagnostic aggregate; no current question or goal is consulted after creation. */
@Service
@RequiredArgsConstructor
public class DiagnosticServiceImpl implements DiagnosticService {
    private static final int MIN_QUESTION_COUNT = 10;
    private final DiagnosticMapper mapper;
    private final JsonMapper jsonMapper;
    private final QuestionImageUrlService imageUrlService;
    private final CollectionService collectionService;

    @Override public DiagnosticPreflightVo preflight(Long userId) {
        DiagnosticGoalRow goal = goal(userId);
        DiagnosticSessionRow session = mapper.selectLatestSession(userId, goal.getId());
        if (session == null) {
            DiagnosticRevisionRow revision = readyRevision(goal);
            return new DiagnosticPreflightVo(goalVo(goal), "NOT_STARTED", "START_DIAGNOSTIC", revisionVo(revision), null, List.of());
        }
        DiagnosticCountRow count = mapper.countAnswers(userId, session.getId());
        String status = statusOf(session, null);
        String action = actionOf(status);
        DiagnosticPreflightVo.ExistingSessionVo existing = new DiagnosticPreflightVo.ExistingSessionVo(String.valueOf(session.getId()), count.getAnsweredCount(), count.getTotalCount(), session.getLastQuestionOrder(), session.getRowVersion());
        return new DiagnosticPreflightVo(goalVo(goal), status, action, null, existing, List.of());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public DiagnosticStartVo start(Long userId, String requestId, DiagnosticStartBo command) {
        requireRequestId(requestId);
        DiagnosticStartVo replay = begin(requestId, "DIAGNOSTIC_START", hash(command.getDiagnosticRevisionId() + ':' + command.getGoalVersion()), DiagnosticStartVo.class);
        if (replay != null) return replay;
        DiagnosticGoalRow goal = goal(userId);
        DiagnosticSessionRow old = mapper.selectLatestSession(userId, goal.getId());
        if (old != null && "in_progress".equals(old.getStatus())) {
            DiagnosticStartVo result = startFrom(old, userId, true);
            complete(requestId, result);
            return result;
        }
        if (!Objects.equals(goal.getRowVersion(), command.getGoalVersion())) throw conflict("目标已更新，请重新进入诊断说明", "START_DIAGNOSTIC");
        if (old != null) throw conflict("当前诊断不允许重新开始", actionOf(statusOf(old, null)));
        DiagnosticRevisionRow revision = readyRevision(goal);
        if (!String.valueOf(revision.getId()).equals(command.getDiagnosticRevisionId())) throw notReady();
        List<DiagnosticItemRow> items = mapper.selectRevisionItems(revision.getId());
        if (!collectionService.isFirstDiagnosticReady(revision.getId()) || items.size() != revision.getQuestionCount() || items.size() < MIN_QUESTION_COUNT || items.stream().anyMatch(i -> !Set.of("CHOICE", "CASE", "ESSAY").contains(i.getQuestionType())
            || i.getEstimatedSecondsSnapshot() == null || i.getEstimatedSecondsSnapshot() <= 0 || i.getKnowledgePointId() == null)) throw notReady();
        long sessionId = IdUtil.getSnowflakeNextId();
        mapper.insertSession(sessionId, userId, goal, revision, profileV8(), requestId);
        for (DiagnosticItemRow item : items) {
            long sq = IdUtil.getSnowflakeNextId(), attempt = IdUtil.getSnowflakeNextId();
            mapper.insertSessionQuestion(sq, sessionId, item);
            mapper.insertAttempt(attempt, sq, userId, "diagnostic-create-" + sessionId + '-' + item.getQuestionOrder());
            mapper.insertAnswer(IdUtil.getSnowflakeNextId(), attempt, emptyAnswer(item.getAnswerSchema(), item.getQuestionType()));
        }
        DiagnosticStartVo result = startFrom(mapper.lockSession(userId, sessionId), userId, false);
        complete(requestId, result);
        return result;
    }

    @Override public DiagnosticSessionVo session(Long userId, long sessionId) {
        DiagnosticSessionRow s = owned(userId, sessionId);
        DiagnosticCountRow c = mapper.countAnswers(userId, sessionId);
        List<DiagnosticSessionVo.NavigationVo> nav = mapper.selectSessionItems(userId, sessionId).stream().map(i ->
            new DiagnosticSessionVo.NavigationVo(i.getQuestionOrder(), i.getQuestionOrder().equals(s.getLastQuestionOrder()) ? "CURRENT" : answered(i.getAnswerData()) ? "ANSWERED" : "UNANSWERED")).toList();
        String status = statusOf(s, null);
        DiagnosticTimingRow timing = mapper.selectTiming(userId, sessionId);
        return new DiagnosticSessionVo(String.valueOf(sessionId), status, actionOf(status), s.getRowVersion(), s.getLastQuestionOrder(), c.getAnsweredCount(), c.getTotalCount() - c.getAnsweredCount(), nav,
            timing == null ? 0 : timing.getEstimatedDurationSeconds(), timing == null ? 0 : timing.getEffectiveElapsedSeconds(),
            timing == null ? OffsetDateTime.now().toString() : timing.getServerTime());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public DiagnosticItemVo item(Long userId, long sessionId, int order) {
        checkOrder(order); DiagnosticSessionRow s = owned(userId, sessionId); DiagnosticItemRow i = itemRow(userId, sessionId, order);
        try {
            JsonNode p = jsonMapper.readTree(i.getPresentationSnapshot());
            List<DiagnosticItemVo.OptionVo> options = new ArrayList<>();
            for (JsonNode o : p.path("options")) options.add(new DiagnosticItemVo.OptionVo(o.path("label").asText(), o.path("content").asText(), o.path("sortOrder").asInt()));
            List<DiagnosticItemVo.ImageVo> images = new ArrayList<>();
            for (JsonNode image : p.path("images")) images.add(new DiagnosticItemVo.ImageVo(imageUrlService.accessUrl(image.path("sourceUrl").asText(null), image.path("storagePath").asText(null)), image.path("alt").asText(null), image.path("sortOrder").asInt()));
            return new DiagnosticItemVo(order, p.path("questionType").asText(), p.path("stem").asText(), p.path("difficulty").asText(), options, images, jsonMapper.readTree(i.getAnswerData()), "SAVED", s.getRowVersion());
        } catch (Exception e) { throw new DiagnosticException(500, "DIAGNOSTIC_SYSTEM_FAILURE", "读取诊断题目失败", true, null, e); }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public DiagnosticDraftVo saveDraft(Long userId, long sessionId, int order, String requestId, DiagnosticDraftBo command) {
        checkOrder(order); requireRequestId(requestId); DiagnosticSessionRow s = ownedForUpdate(userId, sessionId); editable(s);
        DiagnosticDraftVo replay = begin(requestId, "DIAGNOSTIC_DRAFT_SAVE", hash(command.getAnswer().toString() + ':' + command.getExpectedSessionVersion() + ':' + order), DiagnosticDraftVo.class);
        if (replay != null) return replay;
        if (!Objects.equals(s.getRowVersion(), command.getExpectedSessionVersion())) throw versionConflict();
        DiagnosticItemRow i = itemRow(userId, sessionId, order); itemRow(userId, sessionId, command.getCurrentQuestionOrder());
        String questionType = jsonMapper.readTree(i.getPresentationSnapshot()).path("questionType").asText();
        if ("CHOICE".equals(questionType)) validateChoice(command.getAnswer()); else validateSubjective(command.getAnswer(), questionType);
        mapper.updateAnswer(i.getAnswerId(), command.getAnswer().toString());
        if (mapper.updateSessionPosition(sessionId, command.getExpectedSessionVersion(), command.getCurrentQuestionOrder()) != 1) throw versionConflict();
        DiagnosticCountRow c = mapper.countAnswers(userId, sessionId);
        DiagnosticDraftVo out = new DiagnosticDraftVo(OffsetDateTime.now().toString(), answered(command.getAnswer().toString()), c.getAnsweredCount(), command.getExpectedSessionVersion() + 1);
        complete(requestId, out); return out;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public DiagnosticTimerEventVo timerEvent(Long userId, long sessionId, String requestId, DiagnosticTimerEventBo command) {
        requireRequestId(requestId); checkOrder(command.getQuestionOrder());
        String type = command.getEventType() == null ? "" : command.getEventType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ENTER", "HEARTBEAT", "HIDDEN", "LEAVE").contains(type)) {
            throw new DiagnosticException(400, "DIAGNOSTIC_REQUEST_INVALID", "计时事件不正确");
        }
        DiagnosticTimerEventVo replay = begin(requestId, "DIAGNOSTIC_TIMER_EVENT",
            hash(type + ':' + command.getQuestionOrder() + ':' + String.valueOf(command.getLeaseId())), DiagnosticTimerEventVo.class);
        if (replay != null) return replay;
        DiagnosticSessionRow session = ownedForUpdate(userId, sessionId); editable(session);
        DiagnosticTimerLeaseRow lease = mapper.lockTimerLease(sessionId);
        OffsetDateTime now = OffsetDateTime.now();
        if ("ENTER".equals(type)) {
            if (lease != null) closeLease(lease, now, "新的窗口或设备进入");
            DiagnosticItemRow item = itemRow(userId, sessionId, command.getQuestionOrder());
            String leaseId = UUID.randomUUID().toString();
            mapper.insertTimerLease(sessionId, command.getQuestionOrder(), item.getAttemptId(), leaseId);
            mapper.activateTimer(item.getAttemptId());
            DiagnosticTimerEventVo out = timingVo(userId, sessionId, command.getQuestionOrder(), true, leaseId, "RUNNING");
            complete(requestId, out); return out;
        }
        if (lease == null || !Objects.equals(lease.getLeaseId(), command.getLeaseId()) || !Objects.equals(lease.getQuestionOrder(), command.getQuestionOrder())) {
            DiagnosticTimerEventVo out = timingVo(userId, sessionId, command.getQuestionOrder(), false, null, "REPLACED");
            complete(requestId, out); return out;
        }
        if ("HEARTBEAT".equals(type)) {
            if (expired(lease, now)) {
                mapper.markTimerInvalid(lease.getAttemptId(), "计时心跳超过20秒未确认");
                mapper.accumulateTimerInterval(lease.getAttemptId(), 0);
                mapper.deleteTimerLease(sessionId);
                DiagnosticTimerEventVo out = timingVo(userId, sessionId, command.getQuestionOrder(), false, null, "STOPPED");
                complete(requestId, out); return out;
            }
            accumulate(lease, now); mapper.deleteTimerLease(sessionId);
            mapper.insertTimerLease(sessionId, lease.getQuestionOrder(), lease.getAttemptId(), lease.getLeaseId());
            DiagnosticTimerEventVo out = timingVo(userId, sessionId, command.getQuestionOrder(), true, lease.getLeaseId(), "RUNNING");
            complete(requestId, out); return out;
        }
        closeLease(lease, now, "HIDDEN".equals(type) ? "页面隐藏" : "离开题目");
        DiagnosticTimerEventVo out = timingVo(userId, sessionId, command.getQuestionOrder(), true, null, "STOPPED");
        complete(requestId, out); return out;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public DiagnosticSessionVo pause(Long userId, long sessionId, String requestId, DiagnosticPauseBo command) {
        requireRequestId(requestId); checkOrder(command.getCurrentQuestionOrder()); DiagnosticSessionRow s = ownedForUpdate(userId, sessionId); editable(s);
        DiagnosticSessionVo replay = begin(requestId, "DIAGNOSTIC_PAUSE", hash(command.getCurrentQuestionOrder() + ":" + command.getExpectedSessionVersion()), DiagnosticSessionVo.class);
        if (replay != null) return replay;
        itemRow(userId, sessionId, command.getCurrentQuestionOrder());
        closeActiveTimer(sessionId);
        if (mapper.updateSessionPosition(sessionId, command.getExpectedSessionVersion(), command.getCurrentQuestionOrder()) != 1) throw versionConflict();
        DiagnosticSessionVo out = session(userId, sessionId); complete(requestId, out); return out;
    }

    @Override public DiagnosticFinishCheckVo finishCheck(Long userId, long sessionId) {
        DiagnosticSessionRow s = owned(userId, sessionId); DiagnosticCountRow c = mapper.countAnswers(userId, sessionId);
        return new DiagnosticFinishCheckVo(c.getAnsweredCount(), c.getTotalCount() - c.getAnsweredCount(), 0, s.getRowVersion(), c.getFirstUnanswered());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public DiagnosticFinishVo finish(Long userId, long sessionId, String requestId, DiagnosticFinishBo command) {
        requireRequestId(requestId); DiagnosticSessionRow s = ownedForUpdate(userId, sessionId);
        DiagnosticFinishVo replay = begin(requestId, "DIAGNOSTIC_FINISH", hash(String.valueOf(command.getExpectedSessionVersion())), DiagnosticFinishVo.class);
        if (replay != null) return replay;
        if (!"in_progress".equals(s.getStatus())) throw conflict("本次诊断已经提交", actionOf(statusOf(s, null)));
        closeActiveTimer(sessionId);
        if (mapper.submitSession(sessionId, command.getExpectedSessionVersion()) != 1) throw versionConflict();
        mapper.submitAttempts(sessionId);
        mapper.insertJob(IdUtil.getSnowflakeNextId(), "DIAGNOSTIC:" + sessionId,
            VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.DIAGNOSTIC_PIPELINE,
                Map.of("sessionId", String.valueOf(sessionId), "stage", "SUBMITTED")));
        DiagnosticFinishVo out = new DiagnosticFinishVo(String.valueOf(sessionId), "PROCESSING", "WAIT_PROCESSING", OffsetDateTime.now().toString()); complete(requestId, out); return out;
    }

    @Override public DiagnosticStatusVo status(Long userId, long sessionId) {
        DiagnosticSessionRow s = owned(userId, sessionId); DiagnosticJobRow job = mapper.selectJob("DIAGNOSTIC:" + sessionId);
        String status = statusOf(s, job); return statusVo(sessionId, status, job);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public DiagnosticStatusVo regenerate(Long userId, long sessionId, String requestId) {
        requireRequestId(requestId); ownedForUpdate(userId, sessionId); DiagnosticJobRow job = mapper.selectJob("DIAGNOSTIC:" + sessionId);
        DiagnosticStatusVo replay = begin(requestId, "DIAGNOSTIC_REGENERATE_RESULT", hash(""), DiagnosticStatusVo.class);
        if (replay != null) return replay;
        if (job == null || !"failed".equals(job.getStatus())) throw conflict("当前结果不可重试", job == null ? "WAIT_PROCESSING" : actionOf("PROCESSING"));
        mapper.retryJob("DIAGNOSTIC:" + sessionId);
        DiagnosticStatusVo out = status(userId, sessionId); complete(requestId, out); return out;
    }

    @Override public DiagnosticReportVo report(Long userId, long sessionId) {
        DiagnosticSessionRow s = owned(userId, sessionId); DiagnosticReportRow r = mapper.selectReport(userId, sessionId); DiagnosticJobRow job = mapper.selectJob("DIAGNOSTIC:" + sessionId);
        boolean profile = mapper.countSessionProfileEvidence(sessionId) > 0;
        if (r != null && "available".equals(r.getStatus())) try {
            JsonNode report = jsonMapper.readTree("{\"subjectScores\":" + r.getSubjectScores() + ",\"profileSummary\":" + r.getProfileSummary() + "}");
            if ("PARTIAL".equals(report.path("profileSummary").path("dataStatus").asText())) {
                return new DiagnosticReportVo("PARTIAL", profile ? "AVAILABLE" : "PARTIAL", "VIEW_DIAGNOSTIC_REPORT", report);
            }
            return new DiagnosticReportVo(profile ? "COMPLETED" : "FAILED", profile ? "AVAILABLE" : "FAILED", profile ? "VIEW_DIAGNOSTIC_REPORT" : "RETRY_DIAGNOSTIC_RESULT", report);
        } catch (Exception e) { throw new DiagnosticException(500, "DIAGNOSTIC_SYSTEM_FAILURE", "诊断报告数据损坏", true, null, e); }
        if (job != null && "failed".equals(job.getStatus())) throw new DiagnosticException(409, "DIAGNOSTIC_RESULT_FAILED", "诊断结果生成失败", false, "RETRY_DIAGNOSTIC_RESULT");
        throw new DiagnosticException(409, "DIAGNOSTIC_RESULT_PROCESSING", "诊断结果仍在生成", true, "WAIT_PROCESSING");
    }

    @Override public boolean hasInProgressDiagnostic(Long userId, long goalId) { DiagnosticSessionRow s=mapper.selectLatestSession(userId,goalId); return s != null && "in_progress".equals(s.getStatus()); }
    private DiagnosticGoalRow goal(Long userId) { DiagnosticGoalRow g=mapper.selectActiveGoal(userId); if(g==null) throw new DiagnosticException(422,"FIRST_DIAGNOSTIC_NOT_READY","尚未设置有效学习目标",true,"SET_GOAL"); return g; }
    private DiagnosticRevisionRow readyRevision(DiagnosticGoalRow g) { DiagnosticRevisionRow r=mapper.selectAvailableRevision(g.getCertificationId(),g.getSyllabusVersionId()); if(r==null||r.getQuestionCount()==null||r.getQuestionCount()<MIN_QUESTION_COUNT||!collectionService.isFirstDiagnosticReady(r.getId())) throw notReady(); return r; }
    private DiagnosticException notReady(){ return new DiagnosticException(422,"FIRST_DIAGNOSTIC_NOT_READY","首次诊断题集尚未准备完成",true,"START_DIAGNOSTIC"); }
    private DiagnosticSessionRow owned(Long u,long id){ DiagnosticSessionRow s=mapper.lockSession(u,id); if(s==null) throw new DiagnosticException(404,"DIAGNOSTIC_SESSION_NOT_FOUND","诊断会话不存在"); return s; }
    private DiagnosticSessionRow ownedForUpdate(Long u,long id){ return owned(u,id); }
    private DiagnosticItemRow itemRow(Long u,long s,int o){ DiagnosticItemRow i=mapper.selectSessionItem(u,s,o); if(i==null) throw new DiagnosticException(404,"DIAGNOSTIC_SESSION_NOT_FOUND","诊断题目不存在"); return i; }
    private void editable(DiagnosticSessionRow s){ if(!"in_progress".equals(s.getStatus())) throw conflict("当前诊断不可编辑",actionOf(statusOf(s,null))); }
    private DiagnosticException conflict(String m,String action){ return new DiagnosticException(409,"DIAGNOSTIC_STATE_CONFLICT",m,false,action); }
    private DiagnosticException versionConflict(){ return new DiagnosticException(409,"DIAGNOSTIC_SESSION_VERSION_CONFLICT","诊断进度已在其他设备更新",false,"CONTINUE_DIAGNOSTIC",List.of(new DiagnosticErrorVo.FieldErrorVo("expectedSessionVersion","VERSION_CONFLICT","会话版本已变化"))); }
    private void checkOrder(int o){ if(o<1) throw new DiagnosticException(400,"DIAGNOSTIC_REQUEST_INVALID","题序不正确"); }
    private void requireRequestId(String id){ if(id==null||id.isBlank()||id.length()>100) throw new DiagnosticException(400,"DIAGNOSTIC_REQUEST_INVALID","请求号不正确"); }
    private boolean answered(String json){ try{return jsonMapper.readTree(json).path("value").isNull()==false&&jsonMapper.readTree(json).path("value").isMissingNode()==false;}catch(Exception e){return false;} }
    private void validateChoice(JsonNode a){ if(!SharedJsonSchema.QUESTION_ANSWER.version().equals(a.path("schema_version").asText())||!"CHOICE".equals(a.path("answer_type").asText())) throw new DiagnosticException(422,"DIAGNOSTIC_ANSWER_INVALID","答案结构不正确"); }
    private void validateSubjective(JsonNode answer, String questionType) {
        if (!SubjectiveAnswerValidator.validate(answer, questionType).valid()) {
            throw new DiagnosticException(422, "DIAGNOSTIC_ANSWER_INVALID", "主观题答案结构不正确");
        }
    }
    private String emptyAnswer(String schema, String questionType){
        if (Set.of("CASE", "ESSAY").contains(questionType)) {
            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("questionType", questionType);
            answer.put("value", null);
            return VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.SUBJECTIVE_ANSWER, answer);
        }
        try{ JsonNode n=jsonMapper.readTree(schema); return emptyAnswerWithMode(n.path("selection_mode").asText("single")); }catch(Exception e){return emptyAnswerWithMode("single");}
    }
    private String emptyAnswerWithMode(String mode) { Map<String,Object> fields=new LinkedHashMap<>(); fields.put("answer_type","CHOICE"); fields.put("selection_mode",mode); fields.put("value",null); return VersionedJsonDocumentFactory.json(jsonMapper, SharedJsonSchema.QUESTION_ANSWER, fields); }
    private DiagnosticStartVo startFrom(DiagnosticSessionRow s,Long u,boolean resumed){ DiagnosticCountRow c=mapper.countAnswers(u,s.getId()); return new DiagnosticStartVo(String.valueOf(s.getId()),"IN_PROGRESS",resumed,c.getAnsweredCount(),c.getTotalCount(),s.getLastQuestionOrder(),s.getRowVersion()); }
    private DiagnosticPreflightVo.GoalVo goalVo(DiagnosticGoalRow g){return new DiagnosticPreflightVo.GoalVo(String.valueOf(g.getId()),String.valueOf(g.getCertificationId()),g.getCertificationName(),String.valueOf(g.getSyllabusVersionId()),g.getSyllabusVersionName(),g.getRowVersion());}
    private DiagnosticPreflightVo.RevisionVo revisionVo(DiagnosticRevisionRow r){return new DiagnosticPreflightVo.RevisionVo(String.valueOf(r.getId()),r.getQuestionCount(),r.getDurationMinutes(),List.of());}
    private long profileV8(){ return 900000000000000031L; }
    private DiagnosticTimerEventVo timingVo(long userId, long sessionId, int order, boolean accepted, String leaseId, String state) {
        DiagnosticTimingRow timing = mapper.selectTiming(userId, sessionId);
        return new DiagnosticTimerEventVo(accepted, leaseId, order, state,
            timing == null ? 0 : timing.getEstimatedDurationSeconds(), timing == null ? 0 : timing.getEffectiveElapsedSeconds(),
            timing == null ? OffsetDateTime.now().toString() : timing.getServerTime());
    }
    private void closeActiveTimer(long sessionId) {
        DiagnosticTimerLeaseRow lease = mapper.lockTimerLease(sessionId);
        if (lease != null) closeLease(lease, OffsetDateTime.now(), "会话动作结束计时");
    }
    private void closeLease(DiagnosticTimerLeaseRow lease, OffsetDateTime now, String reason) {
        if (expired(lease, now)) {
            mapper.markTimerInvalid(lease.getAttemptId(), reason + "前心跳已超时");
            mapper.accumulateTimerInterval(lease.getAttemptId(), 0);
        }
        else accumulate(lease, now);
        mapper.deleteTimerLease(lease.getSessionId());
    }
    private void accumulate(DiagnosticTimerLeaseRow lease, OffsetDateTime now) {
        int seconds = (int) Math.max(0, Duration.between(lease.getLastHeartbeatAt(), now).getSeconds());
        mapper.accumulateTimerInterval(lease.getAttemptId(), seconds);
    }
    private boolean expired(DiagnosticTimerLeaseRow lease, OffsetDateTime now) {
        return Duration.between(lease.getLastHeartbeatAt(), now).getSeconds() > 20;
    }
    /** Computes public state from the completed session and its two required result artifacts. */
    private String statusOf(DiagnosticSessionRow session, DiagnosticJobRow job) {
        if ("in_progress".equals(session.getStatus()) || "created".equals(session.getStatus())) return "IN_PROGRESS";
        if (job != null && "failed".equals(job.getStatus())) return "FAILED";
        if (!"completed".equals(session.getStatus())) return "PROCESSING";
        DiagnosticReportRow report = mapper.selectReport(session.getUserId(), session.getId());
        boolean reportAvailable = report != null && "available".equals(report.getStatus());
        boolean profileAvailable = mapper.countSessionProfileEvidence(session.getId()) > 0;
        if (reportAvailable && partialReport(report)) return "PARTIAL";
        return reportAvailable && profileAvailable ? "COMPLETED" : "FAILED";
    }
    private boolean partialReport(DiagnosticReportRow report) {
        try { return "PARTIAL".equals(jsonMapper.readTree(report.getProfileSummary()).path("dataStatus").asText()); }
        catch (Exception exception) { return false; }
    }
    private String actionOf(String s){return switch(s){case "NOT_STARTED"->"START_DIAGNOSTIC";case "IN_PROGRESS"->"CONTINUE_DIAGNOSTIC";case "COMPLETED","PARTIAL"->"VIEW_DIAGNOSTIC_REPORT";case "FAILED"->"RETRY_DIAGNOSTIC_RESULT";default->"WAIT_PROCESSING";};}
    private DiagnosticStatusVo statusVo(long id, String status, DiagnosticJobRow job) {
        String stage = job == null ? "SUBMITTED" : jobStage(job.getPayload());
        List<DiagnosticStatusVo.StageVo> stages = List.of("SUBMITTED", "SCORING", "REPORT_GENERATING", "PROFILE_GENERATING", "COMPLETED")
            .stream().map(code -> new DiagnosticStatusVo.StageVo(code,
                code.equals(stage) ? ("FAILED".equals(status) ? "FAILED" : "RUNNING")
                    : (stageIndex(code) < stageIndex(stage) ? "COMPLETED" : "PENDING")))
            .toList();
        return new DiagnosticStatusVo(String.valueOf(id), status, actionOf(status), stages,
            "FAILED".equals(status) ? new DiagnosticStatusVo.FailureVo("诊断结果生成失败", null, true) : null);
    }
    private String jobStage(String p){ try{return jsonMapper.readTree(p).path("stage").asText("SUBMITTED");}catch(Exception e){return"SUBMITTED";} }
    private int stageIndex(String x){return List.of("SUBMITTED","SCORING","REPORT_GENERATING","PROFILE_GENERATING","COMPLETED").indexOf(x);}
    private <T> T begin(String requestId, String action, String payloadHash, Class<T> responseType) {
        DiagnosticIdempotencyRow old = mapper.selectDiagnosticIdempotency(requestId);
        if (old != null) {
            if (!Objects.equals(old.getPayloadHash(), payloadHash)) {
                throw new DiagnosticException(409, "DIAGNOSTIC_IDEMPOTENCY_CONFLICT", "请求号已被使用");
            }
            if (!"succeeded".equals(old.getStatus()) || old.getResponseBody() == null) {
                throw new DiagnosticException(409, "DIAGNOSTIC_IDEMPOTENCY_CONFLICT", "相同请求仍在处理中");
            }
            try {
                JsonNode root = jsonMapper.readTree(old.getResponseBody());
                JsonNode response = root.path("response");
                return jsonMapper.treeToValue(response.isMissingNode() ? root : response, responseType);
            } catch (Exception exception) {
                throw new DiagnosticException(500, "DIAGNOSTIC_SYSTEM_FAILURE", "读取幂等结果失败", true, null, exception);
            }
        }
        try {
            mapper.insertIdempotency(IdUtil.getSnowflakeNextId(), action, requestId, payloadHash);
            return null;
        } catch (DataIntegrityViolationException exception) {
            throw new DiagnosticException(409, "DIAGNOSTIC_IDEMPOTENCY_CONFLICT", "请求号已被使用",
                false, null, exception);
        }
    }
    private void complete(String requestId, Object response) {
        DiagnosticIdempotencyRow record = mapper.selectDiagnosticIdempotency(requestId);
        try {
            Map<String, Object> payload = VersionedJsonDocumentFactory.flatFields(
                AssessmentJsonSchema.DIAGNOSTIC_IDEMPOTENCY, Map.of());
            payload.put("response", response);
            mapper.completeIdempotency(record.getId(), jsonMapper.writeValueAsString(payload));
        } catch (Exception exception) {
            throw new DiagnosticException(500, "DIAGNOSTIC_SYSTEM_FAILURE", "保存幂等结果失败", true, null, exception);
        }
    }
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
