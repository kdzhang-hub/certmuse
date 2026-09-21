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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Observable durable-worker outcomes for profile boundaries and malformed frozen input. */
@Tag("dev")
class DiagnosticResultDispatcherProfileEdgeCaseTest {
    private final DiagnosticMapper mapper = mock(DiagnosticMapper.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final DiagnosticResultDispatcher dispatcher = new DiagnosticResultDispatcher(mapper, json, transactions,
        mock(org.dromara.certmuse.learning.service.MistakeFactRecorder.class));

    @Test
    void dispatchBuildsAMediumConfidenceInitialProfileFromRepeatedFrozenEvidence() throws Exception {
        when(mapper.claimResultJob()).thenReturn(job(801L, "{\"sessionId\":\"701\",\"stage\":\"REPORT_GENERATING\"}"));
        transaction();
        when(mapper.lockSessionForWorker(701L)).thenReturn(session(701L, 61L, 71L, 81L));
        when(mapper.selectSubmittedItems(701L)).thenReturn(List.of(
            item(901L, 1001L, "{\"value\":\"A\"}", "{\"answer\":{\"value\":\"A\"}}", knowledge(501L, 7L, 3), 100),
            item(902L, 1002L, "{\"value\":\"A\"}", "{\"answer\":{\"value\":\"A\"}}", knowledge(501L, 7L, 3), 100)
        ));
        when(mapper.selectLeafCatalog(701L)).thenReturn(List.of(leaf(501L, 7L, 3)));
        when(mapper.selectReport(61L, 701L)).thenReturn(null);

        dispatcher.dispatch();

        verify(mapper).upsertKnowledgeProfile(anyLong(), eq(61L), eq(71L), eq(501L), eq(new BigDecimal("30.00000000")),
            eq(2), eq("medium"), eq("urgent"), eq(81L));
        verify(mapper).upsertSubjectProfile(anyLong(), eq(61L), eq(71L), eq(7L), eq(new BigDecimal("30.00000000")),
            eq(new BigDecimal("1.00000000")), eq(new BigDecimal("1.00000000")), eq(true), eq("medium"), eq("provisional"),
            eq(0), eq(false), eq(false), eq(81L));

        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertOverallProfile(anyLong(), eq(61L), eq(71L), eq(81L), eq(new BigDecimal("30.00000000")), snapshot.capture());
        JsonNode overall = json.readTree(snapshot.getValue());
        assertThat(overall.path("confidence").asText()).isEqualTo("medium");
        assertThat(overall.path("dataStatus").asText()).isEqualTo("INITIAL");

        ArgumentCaptor<String> fallbackSubjectScores = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertReport(anyLong(), eq(701L), eq(61L), eq(71L), eq(81L), eq(new BigDecimal("4")), eq(new BigDecimal("4")),
            fallbackSubjectScores.capture(), any());
        assertThat(json.readTree(fallbackSubjectScores.getValue()).path("schema_version").asText()).isEqualTo("diagnostic_report/1.0");
        verify(mapper).advanceJobStage(801L, "PROFILE_GENERATING");
        verify(mapper).requeueJob(801L, "PROFILE_GENERATING");
    }

    @Test
    void dispatchOrdersUrgentWeakAndLearningDirectionsFromFrozenEvidence() throws Exception {
        when(mapper.claimResultJob()).thenReturn(job(802L, "{\"sessionId\":\"702\",\"stage\":\"REPORT_GENERATING\"}"));
        transaction();
        when(mapper.lockSessionForWorker(702L)).thenReturn(session(702L, 62L, 72L, 82L));
        when(mapper.selectSubmittedItems(702L)).thenReturn(List.of(
            item(911L, 1011L, "{\"value\":\"B\"}", "{\"answer\":{\"value\":\"A\"}}", knowledge(510L, 8L, 3), 1),
            item(912L, 1012L, "{\"value\":\"B\"}", "{\"answer\":{\"value\":\"A\"}}", knowledge(510L, 8L, 3), 1),
            item(913L, 1021L, "{\"value\":\"B\"}", "{\"answer\":{\"value\":\"A\"}}", knowledge(511L, 8L, 2), 100),
            item(914L, 1022L, "{\"value\":\"B\"}", "{\"answer\":{\"value\":\"A\"}}", knowledge(511L, 8L, 2), 100),
            item(915L, 1031L, "{\"value\":\"A\"}", "{\"answer\":{\"value\":\"A\"}}", knowledge(512L, 8L, 1), 100),
            item(916L, 1032L, "{\"value\":\"A\"}", "{\"answer\":{\"value\":\"A\"}}", knowledge(512L, 8L, 1), 100)
        ));
        when(mapper.selectLeafCatalog(702L)).thenReturn(List.of(leaf(510L, 8L, 3), leaf(511L, 8L, 2), leaf(512L, 8L, 1)));
        DiagnosticReportRow report = new DiagnosticReportRow();
        report.setSubjectScores("{\"schema_version\":\"diagnostic_report/1.0\",\"subjects\":[]}");
        when(mapper.selectReport(62L, 702L)).thenReturn(report);

        dispatcher.dispatch();

        ArgumentCaptor<String> profileSummary = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertReport(anyLong(), eq(702L), eq(62L), eq(72L), eq(82L), any(), any(),
            eq(report.getSubjectScores()), profileSummary.capture());
        JsonNode directions = json.readTree(profileSummary.getValue()).path("priorityDirections");
        assertThat(directions).hasSize(3);
        assertThat(directions.get(0).path("knowledgePointId").asText()).isEqualTo("510");
        assertThat(directions.get(0).path("status").asText()).isEqualTo("urgent");
        assertThat(directions.get(1).path("knowledgePointId").asText()).isEqualTo("511");
        assertThat(directions.get(1).path("status").asText()).isEqualTo("urgent");
        assertThat(directions.get(2).path("knowledgePointId").asText()).isEqualTo("512");
        assertThat(directions.get(2).path("status").asText()).isEqualTo("urgent");
        verify(mapper).upsertKnowledgeProfile(anyLong(), eq(62L), eq(72L), eq(510L), eq(new BigDecimal("30.00000000")),
            eq(2), eq("medium"), eq("urgent"), eq(82L));
    }

    @Test
    void dispatchProducesPartialReportWhenAllAiGradingFailed() throws Exception {
        when(mapper.claimResultJob()).thenReturn(job(805L, "{\"sessionId\":\"705\",\"stage\":\"REPORT_GENERATING\"}"));
        transaction();
        when(mapper.lockSessionForWorker(705L)).thenReturn(session(705L, 65L, 75L, 85L));
        DiagnosticItemRow failed = item(921L, 1041L, "{\"value\":\"subjective answer\"}",
            "{\"answer\":{\"value\":\"reference\"}}", "{\"items\":[]}", 50);
        failed.setQuestionType("ESSAY");
        failed.setGradingStatus("failed");
        failed.setScore(BigDecimal.ZERO);
        when(mapper.selectSubmittedItems(705L)).thenReturn(List.of(failed));
        when(mapper.selectLeafCatalog(705L)).thenReturn(List.of());

        dispatcher.dispatch();

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertReport(anyLong(), eq(705L), eq(65L), eq(75L), eq(85L), eq(BigDecimal.ZERO),
            eq(new BigDecimal("2")), any(), summary.capture());
        assertThat(json.readTree(summary.getValue()).path("dataStatus").asText()).isEqualTo("PARTIAL");
        verify(mapper, never()).insertSettlement(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
            any(), anyInt(), anyLong(), any());
    }

    @Test
    void dispatchMarksJobsFailedWhenFrozenKnowledgeMappingIsMissingOrInvalid() {
        when(mapper.claimResultJob()).thenReturn(
            job(803L, "{\"sessionId\":\"703\",\"stage\":\"REPORT_GENERATING\"}"),
            job(804L, "{\"sessionId\":\"704\",\"stage\":\"REPORT_GENERATING\"}")
        );
        transaction();
        when(mapper.lockSessionForWorker(703L)).thenReturn(session(703L, 63L, 73L, 83L));
        when(mapper.lockSessionForWorker(704L)).thenReturn(session(704L, 64L, 74L, 84L));
        when(mapper.selectSubmittedItems(703L)).thenReturn(List.of(
            item(921L, 1041L, "{\"value\":\"A\"}", "{\"answer\":{\"value\":\"A\"}}", "{\"items\":[]}", 100)
        ));
        when(mapper.selectSubmittedItems(704L)).thenReturn(List.of(
            item(922L, 1042L, "{\"value\":\"A\"}", "{\"answer\":{\"value\":\"A\"}}",
                "{\"items\":[{\"knowledgePointId\":0,\"examSubjectId\":8,\"importance\":3}]}", 100)
        ));

        dispatcher.dispatch();
        dispatcher.dispatch();

        verifyFailureReceipt(803L);
        verifyFailureReceipt(804L);
        verify(mapper, never()).requeueJob(eq(803L), any());
        verify(mapper, never()).requeueJob(eq(804L), any());
    }

    @Test
    void dispatchMarksTheJobFailedWhenASubmittedAnswerSnapshotIsUnreadable() {
        when(mapper.claimResultJob()).thenReturn(job(805L, "{\"sessionId\":\"705\",\"stage\":\"SUBMITTED\"}"));
        transaction();
        when(mapper.lockSessionForWorker(705L)).thenReturn(session(705L, 65L, 75L, 85L));
        when(mapper.selectSubmittedItems(705L)).thenReturn(List.of(
            item(931L, 1051L, "not-json", "{\"answer\":{\"value\":\"A\"}}", knowledge(520L, 9L, 3), 100)
        ));

        dispatcher.dispatch();

        verifyFailureReceipt(805L);
        verify(mapper, never()).settleSession(805L);
        verify(mapper, never()).requeueJob(eq(805L), any());
    }

    @Test
    void dispatchTreatsUnknownAndTerminalStagesAsTheCompletionTransition() {
        when(mapper.claimResultJob()).thenReturn(
            job(806L, "{\"sessionId\":\"706\",\"stage\":\"UNKNOWN\"}"),
            job(807L, "{\"sessionId\":\"707\",\"stage\":\"COMPLETED\"}")
        );
        transaction();
        when(mapper.lockSessionForWorker(706L)).thenReturn(session(706L, 66L, 76L, 86L));
        when(mapper.lockSessionForWorker(707L)).thenReturn(session(707L, 67L, 77L, 87L));

        dispatcher.dispatch();
        dispatcher.dispatch();

        verify(mapper).completeSession(706L);
        verify(mapper).completeSession(707L);
        verify(mapper).advanceJobStage(806L, "COMPLETED");
        verify(mapper).advanceJobStage(807L, "COMPLETED");
        verify(mapper).succeedJob(806L);
        verify(mapper).succeedJob(807L);
    }

    private void transaction() {
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
    }

    private void verifyFailureReceipt(long jobId) {
        ArgumentCaptor<String> receipt = ArgumentCaptor.forClass(String.class);
        verify(mapper).failJob(eq(jobId), receipt.capture());
        assertThat(receipt.getValue()).matches("traceId=[0-9a-f-]{36};message=诊断结果生成失败");
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

    private static DiagnosticItemRow item(long answerId, long attemptId, String answer, String presentation, String knowledge, int elapsedSeconds) {
        DiagnosticItemRow item = new DiagnosticItemRow();
        item.setAnswerId(answerId);
        item.setAttemptId(attemptId);
        item.setExamSubjectId(8L);
        item.setReportScore(new BigDecimal("2"));
        item.setQuestionType("CHOICE");
        item.setAnswerData(answer);
        item.setGradingSnapshot(presentation);
        item.setScore(answer.contains("\"value\":\"A\"") ? new BigDecimal("2") : BigDecimal.ZERO);
        item.setKnowledgeSnapshot(knowledge);
        item.setEvidenceGroupKey("diagnostic-edge-case");
        item.setElapsedSeconds(elapsedSeconds);
        item.setTimerStatus("valid");
        item.setEstimatedSecondsSnapshot(100);
        return item;
    }

    private static String knowledge(long leafId, long subjectId, int importance) {
        return "{\"items\":[{\"knowledgePointId\":" + leafId + ",\"examSubjectId\":" + subjectId + ",\"importance\":" + importance + "}]}";
    }

    private static DiagnosticLeafRow leaf(long id, long subjectId, int importance) {
        DiagnosticLeafRow leaf = new DiagnosticLeafRow();
        leaf.setId(id);
        leaf.setExamSubjectId(subjectId);
        leaf.setImportance(importance);
        return leaf;
    }
}
