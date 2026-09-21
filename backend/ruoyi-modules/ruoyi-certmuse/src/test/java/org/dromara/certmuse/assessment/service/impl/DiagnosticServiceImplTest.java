package org.dromara.certmuse.assessment.service.impl;

import org.dromara.certmuse.assessment.domain.DiagnosticCountRow;
import org.dromara.certmuse.assessment.domain.DiagnosticGoalRow;
import org.dromara.certmuse.assessment.domain.DiagnosticReportRow;
import org.dromara.certmuse.assessment.domain.DiagnosticRevisionRow;
import org.dromara.certmuse.assessment.domain.DiagnosticSessionRow;
import org.dromara.certmuse.assessment.domain.DiagnosticTimerLeaseRow;
import org.dromara.certmuse.assessment.domain.DiagnosticTimingRow;
import org.dromara.certmuse.assessment.domain.DiagnosticIdempotencyRow;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticFinishBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticTimerEventBo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticPreflightVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticStatusVo;
import org.dromara.certmuse.assessment.mapper.DiagnosticMapper;
import org.dromara.certmuse.assessment.support.DiagnosticException;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.dromara.certmuse.question.service.CollectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@Tag("dev")
class DiagnosticServiceImplTest {
    private final DiagnosticMapper mapper = Mockito.mock(DiagnosticMapper.class);
    private final CollectionService collectionService = Mockito.mock(CollectionService.class);
    private final DiagnosticServiceImpl service = new DiagnosticServiceImpl(mapper, JsonMapper.builder().build(), new QuestionImageUrlService(), collectionService);

    @Test
    void rejectsNonPositiveQuestionOrderBeforeLookup() {
        assertThatThrownBy(() -> service.item(1L, 2L, 0)).isInstanceOf(DiagnosticException.class)
            .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((DiagnosticException) error).getData().errorCode()).isEqualTo("DIAGNOSTIC_REQUEST_INVALID"));
    }

    @Test
    void hidesForeignOrMissingSessionAsNotFound() {
        when(mapper.lockSession(1L, 2L)).thenReturn(null);
        assertThatThrownBy(() -> service.session(1L, 2L)).isInstanceOf(DiagnosticException.class)
            .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((DiagnosticException) error).getStatus()).isEqualTo(404));
    }

    @Test
    void preflightExposesActiveGoalVersionAsJsonNumberBeforeDiagnosticStarts() throws Exception {
        DiagnosticGoalRow goal = goal(7L);
        DiagnosticRevisionRow revision = revision();
        when(mapper.selectActiveGoal(1L)).thenReturn(goal);
        when(mapper.selectLatestSession(1L, goal.getId())).thenReturn(null);
        when(mapper.selectAvailableRevision(goal.getCertificationId(), goal.getSyllabusVersionId())).thenReturn(revision);
        when(collectionService.isFirstDiagnosticReady(revision.getId())).thenReturn(true);

        DiagnosticPreflightVo result = service.preflight(1L);
        JsonNode json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(result));

        assertThat(result.goal().version()).isEqualTo(7L);
        assertThat(json.path("goal").path("version").isIntegralNumber()).isTrue();
        assertThat(json.path("goal").path("version").longValue()).isEqualTo(7L);
    }

    @Test
    void preflightExposesActiveGoalVersionForExistingSessions() {
        DiagnosticGoalRow goal = goal(8L);
        DiagnosticSessionRow session = new DiagnosticSessionRow();
        session.setId(20L);
        session.setStatus("in_progress");
        session.setRowVersion(3L);
        session.setLastQuestionOrder(4);
        DiagnosticCountRow count = new DiagnosticCountRow();
        count.setAnsweredCount(2);
        count.setTotalCount(10);
        when(mapper.selectActiveGoal(1L)).thenReturn(goal);
        when(mapper.selectLatestSession(1L, goal.getId())).thenReturn(session);
        when(mapper.countAnswers(1L, session.getId())).thenReturn(count);

        assertThat(service.preflight(1L).goal().version()).isEqualTo(8L);
    }

    @Test
    void completedSessionReturnsReportActionOnlyWhenBothArtifactsExist() {
        DiagnosticGoalRow goal = goal(8L);
        DiagnosticSessionRow session = new DiagnosticSessionRow();
        session.setId(20L);
        session.setUserId(1L);
        session.setGoalId(goal.getId());
        session.setStatus("completed");
        session.setRowVersion(3L);
        session.setLastQuestionOrder(50);
        DiagnosticCountRow count = new DiagnosticCountRow();
        count.setAnsweredCount(50);
        count.setTotalCount(50);
        DiagnosticReportRow report = new DiagnosticReportRow();
        report.setStatus("available");
        when(mapper.selectActiveGoal(1L)).thenReturn(goal);
        when(mapper.selectLatestSession(1L, goal.getId())).thenReturn(session);
        when(mapper.countAnswers(1L, session.getId())).thenReturn(count);
        when(mapper.selectReport(1L, session.getId())).thenReturn(report);

        when(mapper.countSessionProfileEvidence(session.getId())).thenReturn(1);
        assertThat(service.preflight(1L).diagnosticStatus()).isEqualTo("COMPLETED");
        assertThat(service.preflight(1L).nextAction()).isEqualTo("VIEW_DIAGNOSTIC_REPORT");

        when(mapper.countSessionProfileEvidence(session.getId())).thenReturn(0);
        assertThat(service.preflight(1L).diagnosticStatus()).isEqualTo("FAILED");
        assertThat(service.preflight(1L).nextAction()).isEqualTo("RETRY_DIAGNOSTIC_RESULT");
    }

    @Test
    void statusReturnsCompletedForACompletedSessionWithBothArtifacts() {
        DiagnosticSessionRow session = new DiagnosticSessionRow();
        session.setId(20L);
        session.setUserId(1L);
        session.setGoalId(10L);
        session.setStatus("completed");
        DiagnosticReportRow report = new DiagnosticReportRow();
        report.setStatus("available");
        when(mapper.lockSession(1L, 20L)).thenReturn(session);
        when(mapper.selectJob("DIAGNOSTIC:20")).thenReturn(null);
        when(mapper.selectReport(1L, 20L)).thenReturn(report);
        when(mapper.countSessionProfileEvidence(20L)).thenReturn(1);

        DiagnosticStatusVo result = service.status(1L, 20L);

        assertThat(result.diagnosticStatus()).isEqualTo("COMPLETED");
        assertThat(result.nextAction()).isEqualTo("VIEW_DIAGNOSTIC_REPORT");
    }

    @Test
    void reportReturnsPartialWhenAiGradingFailedEvenWithoutProfileEvidence() {
        DiagnosticSessionRow session = new DiagnosticSessionRow();
        session.setId(20L);
        session.setUserId(1L);
        session.setStatus("completed");
        DiagnosticReportRow report = new DiagnosticReportRow();
        report.setStatus("available");
        report.setSubjectScores("{\"schema_version\":\"diagnostic_report/1.0\",\"subjects\":[]}");
        report.setProfileSummary("{\"schema_version\":\"diagnostic_report/1.0\",\"dataStatus\":\"PARTIAL\"}");
        when(mapper.lockSession(1L, 20L)).thenReturn(session);
        when(mapper.selectReport(1L, 20L)).thenReturn(report);
        when(mapper.countSessionProfileEvidence(20L)).thenReturn(0);

        var result = service.report(1L, 20L);

        assertThat(result.diagnosticStatus()).isEqualTo("PARTIAL");
        assertThat(result.profileStatus()).isEqualTo("PARTIAL");
        assertThat(result.nextAction()).isEqualTo("VIEW_DIAGNOSTIC_REPORT");
    }

    @Test
    void statusReturnsPartialWhenCompletedReportContainsAiFailures() {
        DiagnosticSessionRow session = new DiagnosticSessionRow();
        session.setId(20L);
        session.setUserId(1L);
        session.setStatus("completed");
        DiagnosticReportRow report = new DiagnosticReportRow();
        report.setStatus("available");
        report.setProfileSummary("{\"dataStatus\":\"PARTIAL\"}");
        when(mapper.lockSession(1L, 20L)).thenReturn(session);
        when(mapper.selectReport(1L, 20L)).thenReturn(report);
        when(mapper.countSessionProfileEvidence(20L)).thenReturn(0);

        DiagnosticStatusVo result = service.status(1L, 20L);

        assertThat(result.diagnosticStatus()).isEqualTo("PARTIAL");
        assertThat(result.nextAction()).isEqualTo("VIEW_DIAGNOSTIC_REPORT");
    }

    @Test
    void heartbeatAcceptsPostgresTimestampLeaseWithoutTextParsing() {
        DiagnosticSessionRow session = inProgressSession(20L, 3L);
        DiagnosticTimerLeaseRow lease = timerLease(20L, 1, 100L, "lease-1", OffsetDateTime.now().minusSeconds(5));
        when(mapper.selectDiagnosticIdempotency("timer-heartbeat")).thenReturn(null, idempotency());
        when(mapper.lockSession(1L, 20L)).thenReturn(session);
        when(mapper.lockTimerLease(20L)).thenReturn(lease);
        when(mapper.selectTiming(1L, 20L)).thenReturn(timing());

        DiagnosticTimerEventBo command = new DiagnosticTimerEventBo();
        command.setEventType("HEARTBEAT");
        command.setQuestionOrder(1);
        command.setLeaseId("lease-1");

        assertThat(service.timerEvent(1L, 20L, "timer-heartbeat", command).timerState()).isEqualTo("RUNNING");

        verify(mapper).accumulateTimerInterval(eq(100L), anyInt());
        verify(mapper).deleteTimerLease(20L);
        verify(mapper).insertTimerLease(20L, 1, 100L, "lease-1");
    }

    @Test
    void finishClosesPostgresTimestampLeaseBeforeSubmittingSession() {
        DiagnosticSessionRow session = inProgressSession(20L, 3L);
        DiagnosticTimerLeaseRow lease = timerLease(20L, 1, 100L, "lease-1", OffsetDateTime.now().minusSeconds(5));
        when(mapper.selectDiagnosticIdempotency("finish-lease")).thenReturn(null, idempotency());
        when(mapper.lockSession(1L, 20L)).thenReturn(session);
        when(mapper.lockTimerLease(20L)).thenReturn(lease);
        when(mapper.submitSession(20L, 3L)).thenReturn(1);

        DiagnosticFinishBo command = new DiagnosticFinishBo();
        command.setExpectedSessionVersion(3L);

        assertThat(service.finish(1L, 20L, "finish-lease", command).diagnosticStatus()).isEqualTo("PROCESSING");

        verify(mapper).accumulateTimerInterval(eq(100L), anyInt());
        verify(mapper).deleteTimerLease(20L);
        verify(mapper).submitSession(20L, 3L);
        verify(mapper).submitAttempts(20L);
    }

    private static DiagnosticGoalRow goal(long rowVersion) {
        DiagnosticGoalRow goal = new DiagnosticGoalRow();
        goal.setId(10L);
        goal.setCertificationId(11L);
        goal.setCertificationName("系统架构设计师");
        goal.setSyllabusVersionId(12L);
        goal.setSyllabusVersionName("2025版考试大纲");
        goal.setRowVersion(rowVersion);
        return goal;
    }

    private static DiagnosticRevisionRow revision() {
        DiagnosticRevisionRow revision = new DiagnosticRevisionRow();
        revision.setId(30L);
        revision.setQuestionCount(50);
        revision.setDurationMinutes(50);
        return revision;
    }

    private static DiagnosticSessionRow inProgressSession(long id, long version) {
        DiagnosticSessionRow session = new DiagnosticSessionRow();
        session.setId(id);
        session.setUserId(1L);
        session.setGoalId(10L);
        session.setStatus("in_progress");
        session.setRowVersion(version);
        session.setLastQuestionOrder(1);
        return session;
    }

    private static DiagnosticTimerLeaseRow timerLease(long sessionId, int questionOrder, long attemptId, String leaseId, OffsetDateTime heartbeat) {
        DiagnosticTimerLeaseRow lease = new DiagnosticTimerLeaseRow();
        lease.setSessionId(sessionId);
        lease.setQuestionOrder(questionOrder);
        lease.setAttemptId(attemptId);
        lease.setLeaseId(leaseId);
        lease.setLastHeartbeatAt(heartbeat);
        return lease;
    }

    private static DiagnosticTimingRow timing() {
        DiagnosticTimingRow timing = new DiagnosticTimingRow();
        timing.setEstimatedDurationSeconds(3000);
        timing.setEffectiveElapsedSeconds(5L);
        timing.setServerTime(OffsetDateTime.now().toString());
        return timing;
    }

    private static DiagnosticIdempotencyRow idempotency() {
        DiagnosticIdempotencyRow record = new DiagnosticIdempotencyRow();
        record.setId(1L);
        return record;
    }

    @Test
    void mapsOnlyDatabaseIntegrityFailureToIdempotencyConflict() {
        when(mapper.selectActiveGoal(1L)).thenReturn(null);
        doThrow(new DataIntegrityViolationException("duplicate"))
            .when(mapper).insertIdempotency(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        assertThatThrownBy(() -> invokeBegin("request-1"))
            .isInstanceOf(DiagnosticException.class)
            .satisfies(error -> {
                DiagnosticException exception = (DiagnosticException) error;
                org.assertj.core.api.Assertions.assertThat(exception.getStatus()).isEqualTo(409);
                org.assertj.core.api.Assertions.assertThat(exception.getData().errorCode())
                    .isEqualTo("DIAGNOSTIC_IDEMPOTENCY_CONFLICT");
                org.assertj.core.api.Assertions.assertThat(exception.getCause())
                    .isInstanceOf(DataIntegrityViolationException.class);
            });
    }

    @Test
    void letsUnexpectedDatabaseFailureEscapeForSafeModuleHandling() {
        doThrow(new IllegalStateException("database unavailable"))
            .when(mapper).insertIdempotency(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        assertThatThrownBy(() -> invokeBegin("request-2"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("database unavailable");
    }

    private void invokeBegin(String requestId) throws Exception {
        var method = DiagnosticServiceImpl.class.getDeclaredMethod(
            "begin", String.class, String.class, String.class, Class.class);
        method.setAccessible(true);
        try {
            method.invoke(service, requestId, "DIAGNOSTIC_START", "hash", String.class);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        }
    }
}
