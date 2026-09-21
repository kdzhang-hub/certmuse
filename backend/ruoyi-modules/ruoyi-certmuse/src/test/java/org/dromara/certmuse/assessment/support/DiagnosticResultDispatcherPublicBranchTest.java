package org.dromara.certmuse.assessment.support;

import org.dromara.certmuse.assessment.domain.DiagnosticItemRow;
import org.dromara.certmuse.assessment.domain.DiagnosticJobRow;
import org.dromara.certmuse.assessment.domain.DiagnosticLeafRow;
import org.dromara.certmuse.assessment.domain.DiagnosticReportRow;
import org.dromara.certmuse.assessment.domain.DiagnosticSessionRow;
import org.dromara.certmuse.assessment.mapper.DiagnosticMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class DiagnosticResultDispatcherPublicBranchTest {
    private final DiagnosticMapper mapper = mock(DiagnosticMapper.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final DiagnosticResultDispatcher dispatcher = new DiagnosticResultDispatcher(mapper, jsonMapper, transactions,
        mock(org.dromara.certmuse.learning.service.MistakeFactRecorder.class));

    @Test
    void dispatchScoresCorrectIncorrectAndUnansweredSubmittedAnswers() {
        when(mapper.claimResultJob()).thenReturn(job(51L, "{\"sessionId\":\"21\",\"stage\":\"SUBMITTED\"}"));
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(mapper.lockSessionForWorker(21L)).thenReturn(session(21L, 1L, 10L, 90L));
        when(mapper.selectSubmittedItems(21L)).thenReturn(List.of(
            item(101L, 1001L, 7L, new BigDecimal("2"), "{\"value\":\"A\"}", "{\"answer\":{\"value\":\"A\"}}", "{\"items\":[]}"),
            item(102L, 1002L, 7L, new BigDecimal("3"), "{\"value\":\"B\"}", "{\"answer\":{\"value\":\"A\"}}", "{\"items\":[]}"),
            item(103L, 1003L, 9L, BigDecimal.ONE, "{\"value\":null}", "{\"answer\":{\"value\":\"A\"}}", "{\"items\":[]}")
        ));

        dispatcher.dispatch();

        verify(mapper).settleSession(21L);
        verify(mapper).scoreAnswer(101L, new BigDecimal("2"), new BigDecimal("2"), BigDecimal.ONE);
        verify(mapper).scoreAnswer(102L, BigDecimal.ZERO, new BigDecimal("3"), BigDecimal.ZERO);
        verify(mapper).scoreAnswer(103L, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO);
        verify(mapper).advanceJobStage(51L, "SCORING");
        verify(mapper).requeueJob(51L, "SCORING");
        verify(mapper, never()).succeedJob(51L);
    }

    @Test
    void dispatchBuildsAStageReportForCorrectIncorrectAndBlankAnswers() throws Exception {
        when(mapper.claimResultJob()).thenReturn(job(61L, "{\"sessionId\":\"31\",\"stage\":\"SCORING\"}"));
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(mapper.lockSessionForWorker(31L)).thenReturn(session(31L, 1L, 10L, 90L));
        when(mapper.selectSubmittedItems(31L)).thenReturn(List.of(
            item(101L, 1001L, 7L, new BigDecimal("2"), "{\"value\":\"A\"}", "{\"answer\":{\"value\":\"A\"}}", "{\"items\":[]}"),
            item(102L, 1002L, 7L, new BigDecimal("3"), "{\"value\":\"B\"}", "{\"answer\":{\"value\":\"A\"}}", "{\"items\":[]}"),
            item(103L, 1003L, 9L, BigDecimal.ONE, "{\"value\":null}", "{\"answer\":{\"value\":\"A\"}}", "{\"items\":[]}")
        ));

        dispatcher.dispatch();

        ArgumentCaptor<String> subjectScores = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertReport(anyLong(), eq(31L), eq(1L), eq(10L), eq(90L), eq(new BigDecimal("2")),
            eq(new BigDecimal("6")), subjectScores.capture(), summary.capture());

        JsonNode subjects = jsonMapper.readTree(subjectScores.getValue());
        assertThat(subjects.path("correctCount").asInt()).isEqualTo(1);
        assertThat(subjects.path("unansweredCount").asInt()).isEqualTo(1);
        assertThat(subjects.path("subjects").size()).isEqualTo(2);
        assertThat(subjects.path("subjects").get(0).path("examSubjectId").asText()).isEqualTo("7");
        assertThat(subjects.path("subjects").get(0).path("questionCount").asInt()).isEqualTo(2);
        assertThat(subjects.path("subjects").get(0).path("correctCount").asInt()).isEqualTo(1);
        assertThat(subjects.path("subjects").get(1).path("examSubjectId").asText()).isEqualTo("9");
        assertThat(jsonMapper.readTree(summary.getValue()).path("dataStatus").asText()).isEqualTo("PROFILE_PENDING");
        verify(mapper).advanceJobStage(61L, "REPORT_GENERATING");
        verify(mapper).requeueJob(61L, "REPORT_GENERATING");
        verify(mapper, never()).succeedJob(anyLong());
    }

    @Test
    void dispatchBuildsProfilesFromFrozenKnowledgeMappingsAndPreservesTheStageReport() throws Exception {
        when(mapper.claimResultJob()).thenReturn(job(70L, "{\"sessionId\":\"40\",\"stage\":\"REPORT_GENERATING\"}"));
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(mapper.lockSessionForWorker(40L)).thenReturn(session(40L, 2L, 11L, 91L));
        when(mapper.selectSubmittedItems(40L)).thenReturn(List.of(
            item(201L, 2001L, 7L, new BigDecimal("2"), "{\"value\":\"A\"}", "{\"answer\":{\"value\":\"A\"}}",
                "{\"items\":[{\"knowledgePointId\":100,\"examSubjectId\":7,\"importance\":3}]}"),
            item(202L, 2002L, 7L, new BigDecimal("3"), "{\"value\":\"B\"}", "{\"answer\":{\"value\":\"A\"}}",
                "{\"items\":[{\"knowledgePointId\":101,\"examSubjectId\":7,\"importance\":2}]}")
        ));
        when(mapper.selectLeafCatalog(40L)).thenReturn(List.of(
            leaf(100L, 7L, 3), leaf(101L, 7L, 2), leaf(200L, 9L, 3)
        ));
        when(mapper.selectReport(2L, 40L)).thenReturn(report("{\"schema_version\":\"diagnostic_report/1.0\",\"stage\":\"report\"}"));

        dispatcher.dispatch();

        verify(mapper, times(2)).insertSettlement(anyLong(), eq(2L), eq(11L), anyLong(), anyLong(), eq(40L), any(),
            eq(1), eq(91L), any());
        verify(mapper, times(2)).upsertKnowledgeProfile(anyLong(), eq(2L), eq(11L), anyLong(), any(), eq(1), eq("low"),
            eq("provisional"), eq(91L));
        verify(mapper).upsertSubjectProfile(anyLong(), eq(2L), eq(11L), eq(7L), eq(new BigDecimal("37.50000000")),
            eq(new BigDecimal("1.00000000")), eq(new BigDecimal("0.00000000")), eq(true), eq("low"), eq("provisional"), eq(0), eq(false), eq(false), eq(91L));

        ArgumentCaptor<String> overallSnapshot = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertOverallProfile(anyLong(), eq(2L), eq(11L), eq(91L), eq(new BigDecimal("37.50000000")),
            overallSnapshot.capture());
        JsonNode overall = jsonMapper.readTree(overallSnapshot.getValue());
        assertThat(overall.path("dataStatus").asText()).isEqualTo("PARTIAL");
        assertThat(overall.path("subjects").size()).isEqualTo(1);
        assertThat(overall.path("subjects").get(0).path("examSubjectId").asText()).isEqualTo("7");

        ArgumentCaptor<String> profileSummary = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertReport(anyLong(), eq(40L), eq(2L), eq(11L), eq(91L), eq(new BigDecimal("2")),
            eq(new BigDecimal("5")), eq("{\"schema_version\":\"diagnostic_report/1.0\",\"stage\":\"report\"}"), profileSummary.capture());
        JsonNode profile = jsonMapper.readTree(profileSummary.getValue());
        assertThat(profile.path("dataStatus").asText()).isEqualTo("INITIAL_PROFILE");
        assertThat(profile.path("confidence").asText()).isEqualTo("low");
        assertThat(profile.path("knowledgePoints").size()).isEqualTo(2);
        assertThat(profile.path("priorityDirections").size()).isEqualTo(2);
        assertThat(profile.path("knowledgePoints").get(0).path("knowledgePointId").asText()).isEqualTo("100");
        assertThat(profile.path("knowledgePoints").get(1).path("knowledgePointId").asText()).isEqualTo("101");
        verify(mapper).advanceJobStage(70L, "PROFILE_GENERATING");
        verify(mapper).requeueJob(70L, "PROFILE_GENERATING");
        verify(mapper, never()).succeedJob(anyLong());
    }

    @Test
    void dispatchCompletesTheDurableJobOnlyAfterTheProfileStage() {
        when(mapper.claimResultJob()).thenReturn(job(80L, "{\"sessionId\":\"50\",\"stage\":\"PROFILE_GENERATING\"}"));
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(mapper.lockSessionForWorker(50L)).thenReturn(session(50L, 3L, 12L, 92L));

        dispatcher.dispatch();

        verify(mapper).completeSession(50L);
        verify(mapper).advanceJobStage(80L, "COMPLETED");
        verify(mapper).succeedJob(80L);
        verify(mapper, never()).requeueJob(anyLong(), any());
    }

    private static DiagnosticJobRow job(long id, String payload) {
        DiagnosticJobRow job = new DiagnosticJobRow();
        job.setId(id);
        job.setPayload(payload);
        return job;
    }

    private static DiagnosticSessionRow session(long id, long userId, long goalId, long ruleVersionId) {
        DiagnosticSessionRow session = new DiagnosticSessionRow();
        session.setId(id);
        session.setUserId(userId);
        session.setGoalId(goalId);
        session.setRuleVersionId(ruleVersionId);
        return session;
    }

    private static DiagnosticItemRow item(long answerId, long attemptId, long subjectId, BigDecimal reportScore, String answer,
                                            String presentation, String knowledge) {
        DiagnosticItemRow item = new DiagnosticItemRow();
        item.setAnswerId(answerId);
        item.setAttemptId(attemptId);
        item.setExamSubjectId(subjectId);
        item.setReportScore(reportScore);
        item.setQuestionType("CHOICE");
        item.setAnswerData(answer);
        item.setGradingSnapshot(presentation);
        item.setScore(answer.contains("\"value\":\"A\"") ? reportScore : BigDecimal.ZERO);
        item.setKnowledgeSnapshot(knowledge);
        item.setEvidenceGroupKey("diagnostic-test-group");
        item.setElapsedSeconds(10);
        item.setTimerStatus("valid");
        item.setEstimatedSecondsSnapshot(100);
        return item;
    }

    private static DiagnosticLeafRow leaf(long id, long subjectId, int importance) {
        DiagnosticLeafRow leaf = new DiagnosticLeafRow();
        leaf.setId(id);
        leaf.setExamSubjectId(subjectId);
        leaf.setImportance(importance);
        return leaf;
    }

    private static DiagnosticReportRow report(String subjectScores) {
        DiagnosticReportRow report = new DiagnosticReportRow();
        report.setSubjectScores(subjectScores);
        return report;
    }
}
