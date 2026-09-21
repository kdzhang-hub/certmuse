package org.dromara.certmuse.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.dromara.certmuse.assessment.domain.FormalExamItemRow;
import org.dromara.certmuse.assessment.domain.FormalExamSessionRow;
import org.dromara.certmuse.assessment.domain.PastPaperGoalRow;
import org.dromara.certmuse.assessment.domain.PastPaperIdempotencyRow;
import org.dromara.certmuse.assessment.domain.PastPaperPaperRow;
import org.dromara.certmuse.assessment.domain.PastPaperQuestionRow;
import org.dromara.certmuse.assessment.domain.bo.FormalExamDraftBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamTimerEventBo;
import org.dromara.certmuse.assessment.mapper.FormalExamMapper;
import org.dromara.certmuse.assessment.support.SimulationException;
import org.dromara.certmuse.assessment.support.SubjectiveGradingTasks;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("dev")
class FormalExamEngineWorkflowTest {
    private final FormalExamMapper mapper = mock(FormalExamMapper.class);
    private final FormalExamEngine engine = new FormalExamEngine(
        mapper, JsonMapper.builder().build(), mock(QuestionImageUrlService.class), mock(SubjectiveGradingTasks.class));

    @Test
    void startAllowsBlankChoiceAnalysisAndCreatesOneUniqueAttemptRequestIdPerQuestion() {
        String requestId = "00000000-0000-4000-8000-000000000003";
        PastPaperPaperRow paper = paper(71L);
        PastPaperGoalRow goal = goal();
        when(mapper.selectCurrentPaper(7L, "SIMULATION")).thenReturn(paper);
        when(mapper.selectActiveGoal(42L)).thenReturn(goal);
        PastPaperQuestionRow withoutAnalysis = question(1);
        withoutAnalysis.setAnalysis(null);
        when(mapper.selectCurrentQuestions(7L, "SIMULATION")).thenReturn(List.of(withoutAnalysis, question(2)));
        when(mapper.selectPublishedRuleVersion()).thenReturn(5L);
        when(mapper.nextFormalAttemptNo(42L, 9L, 71L, "simulation")).thenReturn(1);
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        FormalExamSessionRow created = session("in_progress", 0L);
        created.setCollectionRevisionId(71L);
        created.setFormalAttemptNo(1);
        created.setStartedTime(java.time.OffsetDateTime.parse("2026-08-20T10:00:00+08:00"));
        created.setTotalCount(2);
        when(mapper.selectSession(org.mockito.ArgumentMatchers.eq(42L), anyLong(),
            org.mockito.ArgumentMatchers.eq("simulation"))).thenReturn(created);

        engine.start(42L, 7L, "SIMULATION", "simulation", 71L, 3L, requestId);

        ArgumentCaptor<String> attemptRequests = ArgumentCaptor.forClass(String.class);
        verify(mapper, times(2)).insertDraftAttempt(anyLong(), anyLong(), anyLong(),
            org.mockito.ArgumentMatchers.eq(42L), attemptRequests.capture(), anyString());
        assertThat(attemptRequests.getAllValues()).hasSize(2).doesNotHaveDuplicates().doesNotContain(requestId);
    }

    @Test
    void startRejectsAnotherPaperWhenTheSameExamTypeAlreadyHasAnActiveSession() {
        PastPaperPaperRow paper = paper(71L);
        FormalExamSessionRow active = session("in_progress", 2L);
        active.setCollectionRevisionId(70L);
        when(mapper.selectCurrentPaper(7L, "PAST_PAPER")).thenReturn(paper);
        when(mapper.selectActiveGoal(42L)).thenReturn(goal());
        when(mapper.selectCurrentQuestions(7L, "PAST_PAPER")).thenReturn(List.of(question(1)));
        when(mapper.selectActiveSession(42L, 9L, "past_paper_exam")).thenReturn(active);

        var exception = catchThrowableOfType(() -> engine.start(42L, 7L, "PAST_PAPER", "past_paper_exam",
            71L, 3L, "00000000-0000-4000-8000-000000000004"),
            org.dromara.certmuse.assessment.support.PastPaperException.class);

        assertThat(exception.status()).isEqualTo(409);
        assertThat(exception.errorCode()).isEqualTo("PAST_PAPER_EXAM_ACTIVE_SESSION_CONFLICT");
        verify(mapper, never()).insertSession(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(), anyLong(),
            anyString(), anyLong(), anyString(), org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void staleDraftVersionIsRejectedBeforeAnyAnswerMutation() {
        FormalExamSessionRow session = session("in_progress", 4L);
        FormalExamItemRow item = item();
        when(mapper.lockSession(42L, 9L, "simulation")).thenReturn(session);
        when(mapper.selectItem(42L, 9L, 1)).thenReturn(item);
        FormalExamDraftBo command = draft(3L);

        SimulationException exception = catchThrowableOfType(
            () -> engine.saveDraft(42L, 9L, 1, "simulation",
                "00000000-0000-4000-8000-000000000001", command),
            SimulationException.class);

        assertThat(exception.status()).isEqualTo(409);
        assertThat(exception.errorCode()).isEqualTo("SIMULATION_SESSION_VERSION_CONFLICT");
        verify(mapper, never()).updateDraft(anyLong(), anyString());
    }

    @Test
    void duplicateTimerRequestReplaysAfterTheOriginalRequestAutoSubmittedTheSession() {
        FormalExamSessionRow session = session("settling", 5L);
        when(mapper.lockSession(42L, 9L, "simulation")).thenReturn(session);
        when(mapper.selectItem(42L, 9L, 1)).thenReturn(item());
        String requestId = "00000000-0000-4000-8000-000000000002";
        String payload = "SIMULATION_TIMER:9:HEARTBEAT:1:lease-1:4";
        PastPaperIdempotencyRow replay = new PastPaperIdempotencyRow();
        replay.setPayloadHash(hash(payload));
        replay.setStatus("succeeded");
        replay.setResponseBody("""
            {"schema_version":"formal_exam_idempotency/1.0","response":{"data":{
              "eventAccepted":true,"leaseId":null,"questionOrder":1,"timerState":"STOPPED",
              "estimatedDurationSeconds":60,"effectiveElapsedSeconds":60,"serverTime":"2026-08-19T10:00:00+08:00"
            }}}
            """);
        when(mapper.selectIdempotency("SIMULATION_TIMER", requestId)).thenReturn(replay);
        FormalExamTimerEventBo command = timer(4L);

        var result = engine.timerEvent(42L, 9L, "simulation", requestId, command);

        assertThat(result.timerState()).isEqualTo("STOPPED");
        assertThat(result.effectiveElapsedSeconds()).isEqualTo(60);
        verify(mapper, never()).lockTimerLease(anyLong());
    }

    private static FormalExamSessionRow session(String status, long version) {
        FormalExamSessionRow row = new FormalExamSessionRow();
        row.setId(9L);
        row.setUserId(42L);
        row.setSessionType("simulation");
        row.setStatus(status);
        row.setRowVersion(version);
        row.setDurationSecondsSnapshot(60);
        return row;
    }

    private static FormalExamItemRow item() {
        FormalExamItemRow row = new FormalExamItemRow();
        row.setQuestionOrder(1);
        row.setAttemptId(11L);
        row.setAnswerId(12L);
        row.setPresentationSnapshot("""
            {"schema_version":"formal_exam_presentation/1.0","questionType":"CHOICE",
             "stem":"题干","options":[{"label":"A","content":"选项A"}],"images":[]}
            """);
        return row;
    }

    private static FormalExamDraftBo draft(long expectedVersion) {
        FormalExamDraftBo.AnswerBo answer = new FormalExamDraftBo.AnswerBo();
        answer.setChoiceValue(List.of("A"));
        FormalExamDraftBo command = new FormalExamDraftBo();
        command.setAnswer(answer);
        command.setExpectedSessionVersion(expectedVersion);
        command.setCurrentQuestionOrder(1);
        return command;
    }

    private static FormalExamTimerEventBo timer(long expectedVersion) {
        FormalExamTimerEventBo command = new FormalExamTimerEventBo();
        command.setEventType("HEARTBEAT");
        command.setQuestionOrder(1);
        command.setLeaseId("lease-1");
        command.setExpectedSessionVersion(expectedVersion);
        return command;
    }

    private static PastPaperGoalRow goal() {
        PastPaperGoalRow row = new PastPaperGoalRow();
        row.setId(9L);
        row.setCertificationId(8L);
        row.setSyllabusVersionId(6L);
        row.setRowVersion(3L);
        return row;
    }

    private static PastPaperPaperRow paper(long revisionId) {
        PastPaperPaperRow row = new PastPaperPaperRow();
        row.setCollectionId(7L);
        row.setRevisionId(revisionId);
        row.setCertificationId(8L);
        row.setCollectionName("正式试卷");
        row.setDurationMinutes(120);
        return row;
    }

    private static PastPaperQuestionRow question(int order) {
        PastPaperQuestionRow row = new PastPaperQuestionRow();
        row.setQuestionId(100L + order);
        row.setQuestionRevisionId(200L + order);
        row.setExamSubjectId(300L);
        row.setQuestionOrder(order);
        row.setQuestionType("CHOICE");
        row.setStem("题干" + order);
        row.setAnalysis("解析" + order);
        row.setAnswerJson("{\"value\":[\"A\"]}");
        row.setOptionsJson("[{\"label\":\"A\",\"content\":\"选项A\"},{\"label\":\"B\",\"content\":\"选项B\"}]");
        row.setImagesJson("[]");
        row.setKnowledgeJson("[{\"knowledgePointId\":\"1\",\"examSubjectId\":\"300\"}]");
        row.setDifficulty("medium");
        row.setEstimatedSeconds(60);
        row.setEvidenceGroupKey("Q:" + row.getQuestionId());
        row.setReportScore("1");
        return row;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
