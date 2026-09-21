package org.dromara.certmuse.learning.service.impl;

import org.dromara.certmuse.learning.domain.OnboardingRow;
import org.dromara.certmuse.learning.domain.vo.OnboardingStatusVo;
import org.dromara.certmuse.learning.mapper.OnboardingMapper;
import org.dromara.certmuse.learning.support.OnboardingException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class OnboardingServiceImplTest {

    @Test
    void startsGoalSetupWhenTheLearnerHasNoOnboardingRowOrGoal() {
        OnboardingMapper missingRow = mock(OnboardingMapper.class);
        when(missingRow.selectStatus(1L)).thenReturn(null);

        OnboardingStatusVo withoutRow = new OnboardingServiceImpl(missingRow).status(1L);

        assertEquals("NONE", withoutRow.getGoalStatus());
        assertEquals("NOT_STARTED", withoutRow.getDiagnosticStatus());
        assertEquals("SET_GOAL", withoutRow.getNextAction());

        OnboardingMapper missingGoal = mock(OnboardingMapper.class);
        OnboardingRow row = new OnboardingRow();
        row.setCertificationId(20L);
        when(missingGoal.selectStatus(2L)).thenReturn(row);

        OnboardingStatusVo withoutGoal = new OnboardingServiceImpl(missingGoal).status(2L);

        assertEquals("NONE", withoutGoal.getGoalStatus());
        assertEquals("SET_GOAL", withoutGoal.getNextAction());
    }

    @Test
    void routesAPausedGoalToSupportWithoutStartingADiagnostic() {
        OnboardingMapper mapper = mock(OnboardingMapper.class);
        OnboardingRow row = baseRow();
        row.setGoalStatus("paused");
        when(mapper.selectStatus(1L)).thenReturn(row);

        OnboardingStatusVo result = new OnboardingServiceImpl(mapper).status(1L);

        assertEquals("PAUSED", result.getGoalStatus());
        assertEquals("NOT_STARTED", result.getDiagnosticStatus());
        assertEquals("CONTACT_SUPPORT", result.getNextAction());
        assertEquals("20", result.getCurrentCertificationId());
    }

    @Test
    void routesAnActiveGoalWithoutASessionToTheFirstDiagnostic() {
        OnboardingMapper mapper = mock(OnboardingMapper.class);
        OnboardingRow row = baseRow();
        when(mapper.selectStatus(1L)).thenReturn(row);

        OnboardingStatusVo result = new OnboardingServiceImpl(mapper).status(1L);

        assertEquals("ACTIVE", result.getGoalStatus());
        assertEquals("NOT_STARTED", result.getDiagnosticStatus());
        assertEquals("START_DIAGNOSTIC", result.getNextAction());
        assertNull(result.getActiveSessionId());
    }

    @ParameterizedTest
    @CsvSource({
        "created,IN_PROGRESS,CONTINUE_DIAGNOSTIC",
        "in_progress,IN_PROGRESS,CONTINUE_DIAGNOSTIC",
        "submitted,PROCESSING,WAIT_PROCESSING",
        "settling,PROCESSING,WAIT_PROCESSING",
        "invalid,FAILED,RETRY_DIAGNOSTIC",
        "cancelled,FAILED,RETRY_DIAGNOSTIC"
    })
    void mapsEachRecoverableDiagnosticSessionState(String sessionStatus, String diagnosticStatus, String nextAction) {
        OnboardingMapper mapper = mock(OnboardingMapper.class);
        OnboardingRow row = baseRow();
        row.setSessionId(30L);
        row.setSessionStatus(sessionStatus);
        when(mapper.selectStatus(1L)).thenReturn(row);

        OnboardingStatusVo result = new OnboardingServiceImpl(mapper).status(1L);

        assertEquals(diagnosticStatus, result.getDiagnosticStatus());
        assertEquals(nextAction, result.getNextAction());
        assertEquals("30", result.getActiveSessionId());
    }

    @Test
    void exposesTheReportWhenTheCompletedDiagnosticHasAnInitialProfile() {
        OnboardingMapper mapper = mock(OnboardingMapper.class);
        when(mapper.selectStatus(1L)).thenReturn(completedRow("available", 1));

        OnboardingStatusVo result = new OnboardingServiceImpl(mapper).status(1L);

        assertEquals("COMPLETED", result.getDiagnosticStatus());
        assertEquals("VIEW_DIAGNOSTIC_REPORT", result.getNextAction());
    }

    @Test
    void rejectsUnknownGoalAndDiagnosticSessionStates() {
        OnboardingMapper unknownGoal = mock(OnboardingMapper.class);
        OnboardingRow paused = baseRow();
        paused.setGoalStatus("archived");
        when(unknownGoal.selectStatus(1L)).thenReturn(paused);

        assertThrows(OnboardingException.class, () -> new OnboardingServiceImpl(unknownGoal).status(1L));

        OnboardingMapper unknownSession = mock(OnboardingMapper.class);
        OnboardingRow session = baseRow();
        session.setSessionId(30L);
        session.setSessionStatus("expired");
        when(unknownSession.selectStatus(2L)).thenReturn(session);

        assertThrows(OnboardingException.class, () -> new OnboardingServiceImpl(unknownSession).status(2L));
    }

    @Test
    void returnsProcessingOnlyWhileReportIsGenerating() {
        OnboardingMapper mapper = mock(OnboardingMapper.class);
        when(mapper.selectStatus(1L)).thenReturn(completedRow("generating", 0));

        OnboardingStatusVo result = new OnboardingServiceImpl(mapper).status(1L);

        assertEquals("PROCESSING", result.getDiagnosticStatus());
        assertEquals("WAIT_PROCESSING", result.getNextAction());
    }

    @Test
    void exposesResultRetryWhenReportExistsButInitialProfileIsMissing() {
        OnboardingMapper mapper = mock(OnboardingMapper.class);
        when(mapper.selectStatus(1L)).thenReturn(completedRow("available", 0));

        OnboardingStatusVo result = new OnboardingServiceImpl(mapper).status(1L);

        assertEquals("FAILED", result.getDiagnosticStatus());
        assertEquals("RETRY_DIAGNOSTIC_RESULT", result.getNextAction());
    }

    @Test
    void rejectsCompletedSessionWithoutReport() {
        OnboardingMapper mapper = mock(OnboardingMapper.class);
        when(mapper.selectStatus(1L)).thenReturn(completedRow(null, 1));

        OnboardingException exception = assertThrows(
            OnboardingException.class,
            () -> new OnboardingServiceImpl(mapper).status(1L)
        );

        assertEquals(500, exception.status());
        assertEquals("ONBOARDING_STATE_INVALID", exception.errorCode());
        assertFalse(exception.retryable());
        assertNull(exception.traceId());
    }

    private OnboardingRow completedRow(String reportStatus, int profileCount) {
        OnboardingRow row = baseRow();
        row.setSessionId(30L);
        row.setSessionStatus("completed");
        row.setReportStatus(reportStatus);
        row.setProfileCount(profileCount);
        return row;
    }

    private OnboardingRow baseRow() {
        OnboardingRow row = new OnboardingRow();
        row.setGoalId(10L);
        row.setCertificationId(20L);
        row.setGoalStatus("active");
        return row;
    }
}
