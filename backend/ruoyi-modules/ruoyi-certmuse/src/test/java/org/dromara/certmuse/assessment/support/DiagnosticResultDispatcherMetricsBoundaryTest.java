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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Durable result-worker scenarios with sparse frozen evidence. These protect explicit zero-denominator semantics.
 */
@Tag("dev")
class DiagnosticResultDispatcherMetricsBoundaryTest {
    private final DiagnosticMapper mapper = mock(DiagnosticMapper.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final DiagnosticResultDispatcher dispatcher = new DiagnosticResultDispatcher(mapper, json, transactions,
        mock(org.dromara.certmuse.learning.service.MistakeFactRecorder.class));

    @Test
    void profileUsesFrozenEvidenceWithZeroCoverageWhenTheCatalogHasNoLeaves() throws Exception {
        when(mapper.claimResultJob()).thenReturn(job(1001L, 2001L));
        transaction();
        when(mapper.lockSessionForWorker(2001L)).thenReturn(session(2001L, 31L, 41L, 51L));
        when(mapper.selectSubmittedItems(2001L)).thenReturn(List.of(
            item(3001L, 4001L, "{\"value\":\"A\"}", "{\"answer\":{\"value\":\"A\"}}", knowledge(501L, 61L, 2))
        ));
        when(mapper.selectLeafCatalog(2001L)).thenReturn(List.of());
        when(mapper.selectReport(31L, 2001L)).thenReturn(null);

        dispatcher.dispatch();

        verify(mapper).upsertSubjectProfile(anyLong(), eq(31L), eq(41L), eq(61L), eq(new BigDecimal("37.50000000")),
            eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), eq(true), eq("low"), eq("provisional"), eq(0), eq(false), eq(false), eq(51L));
        ArgumentCaptor<String> overallSnapshot = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertOverallProfile(anyLong(), eq(31L), eq(41L), eq(51L), eq(new BigDecimal("37.50000000")),
            overallSnapshot.capture());
        JsonNode overall = json.readTree(overallSnapshot.getValue());
        assertThat(overall.path("dataStatus").asText()).isEqualTo("PARTIAL");
        assertThat(overall.path("confidence").asText()).isEqualTo("low");
        verify(mapper).advanceJobStage(1001L, "PROFILE_GENERATING");
        verify(mapper).requeueJob(1001L, "PROFILE_GENERATING");
    }

    @Test
    void profileUsesFrozenEvidenceImportanceInsteadOfTheCurrentCatalogImportance() throws Exception {
        when(mapper.claimResultJob()).thenReturn(job(1002L, 2002L));
        transaction();
        when(mapper.lockSessionForWorker(2002L)).thenReturn(session(2002L, 32L, 42L, 52L));
        when(mapper.selectSubmittedItems(2002L)).thenReturn(List.of(
            item(3002L, 4002L, "{\"value\":\"B\"}", "{\"answer\":{\"value\":\"A\"}}", knowledge(502L, 62L, 1))
        ));
        when(mapper.selectLeafCatalog(2002L)).thenReturn(List.of(leaf(502L, 62L, 0)));
        DiagnosticReportRow report = new DiagnosticReportRow();
        report.setSubjectScores("{\"schema_version\":\"diagnostic_report/1.0\"}");
        when(mapper.selectReport(32L, 2002L)).thenReturn(report);

        dispatcher.dispatch();

        verify(mapper).upsertSubjectProfile(anyLong(), eq(32L), eq(42L), eq(62L), eq(new BigDecimal("37.50000000")),
            eq(new BigDecimal("1.00000000")), argThat(value -> value != null && value.compareTo(BigDecimal.ZERO) == 0), eq(true), eq("low"), eq("provisional"),
            eq(0), eq(false), eq(false), eq(52L));
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertReport(anyLong(), eq(2002L), eq(32L), eq(42L), eq(52L), eq(BigDecimal.ZERO), eq(new BigDecimal("2")),
            eq(report.getSubjectScores()), summary.capture());
        assertThat(json.readTree(summary.getValue()).path("knowledgePoints")).singleElement()
            .satisfies(point -> assertThat(point.path("ability").decimalValue()).isEqualByComparingTo("37.50000000"));
        verify(mapper).advanceJobStage(1002L, "PROFILE_GENERATING");
    }

    private void transaction() {
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
    }

    private static DiagnosticJobRow job(long id, long sessionId) {
        DiagnosticJobRow job = new DiagnosticJobRow();
        job.setId(id);
        job.setPayload("{\"sessionId\":\"" + sessionId + "\",\"stage\":\"REPORT_GENERATING\"}");
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

    private static DiagnosticItemRow item(long answerId, long attemptId, String answer, String presentation, String knowledge) {
        DiagnosticItemRow item = new DiagnosticItemRow();
        item.setAnswerId(answerId);
        item.setAttemptId(attemptId);
        item.setExamSubjectId(61L);
        item.setReportScore(new BigDecimal("2"));
        item.setQuestionType("CHOICE");
        item.setAnswerData(answer);
        item.setGradingSnapshot(presentation);
        item.setScore(answer.contains("\"value\":\"A\"") ? new BigDecimal("2") : BigDecimal.ZERO);
        item.setKnowledgeSnapshot(knowledge);
        item.setEvidenceGroupKey("diagnostic-metrics-boundary");
        item.setElapsedSeconds(100);
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
