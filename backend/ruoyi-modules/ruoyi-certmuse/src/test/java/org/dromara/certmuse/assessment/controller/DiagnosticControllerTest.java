package org.dromara.certmuse.assessment.controller;

import org.dromara.certmuse.assessment.domain.bo.DiagnosticDraftBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticFinishBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticPauseBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticStartBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticTimerEventBo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticDraftVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticFinishCheckVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticFinishVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticItemVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticPreflightVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticReportVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticSessionVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticStartVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticStatusVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticTimerEventVo;
import org.dromara.certmuse.assessment.service.DiagnosticService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class DiagnosticControllerTest {
    private final DiagnosticService service = mock(DiagnosticService.class);
    private final DiagnosticController controller = new DiagnosticController(service);

    @Test
    void everyLearnerEndpointDelegatesWithTheAuthenticatedUserAndReturnsItsPayload() {
        long userId = 42L;
        long sessionId = 20L;
        DiagnosticStartBo startCommand = new DiagnosticStartBo();
        DiagnosticDraftBo draftCommand = new DiagnosticDraftBo();
        DiagnosticPauseBo pauseCommand = new DiagnosticPauseBo();
        DiagnosticFinishBo finishCommand = new DiagnosticFinishBo();
        DiagnosticTimerEventBo timerEventCommand = new DiagnosticTimerEventBo();
        DiagnosticPreflightVo preflight = new DiagnosticPreflightVo(null, "NOT_STARTED", "START_DIAGNOSTIC", null, null, List.of());
        DiagnosticStartVo start = new DiagnosticStartVo("20", "IN_PROGRESS", false, 0, 50, 1, 1);
        DiagnosticSessionVo session = new DiagnosticSessionVo(
            "20", "IN_PROGRESS", "CONTINUE_DIAGNOSTIC", 1, 1, 0, 50, List.of(), 3_000, 120, "now"
        );
        DiagnosticItemVo item = new DiagnosticItemVo(1, "CHOICE", "题干", "easy", List.of(), List.of(), null, "SAVED", 1);
        DiagnosticTimerEventVo timerEvent = new DiagnosticTimerEventVo(true, "lease-1", 1, "ACTIVE", 3_000, 120, "now");
        DiagnosticDraftVo draft = new DiagnosticDraftVo("now", true, 1, 2);
        DiagnosticFinishCheckVo finishCheck = new DiagnosticFinishCheckVo(50, 0, 0, 2, null);
        DiagnosticFinishVo finish = new DiagnosticFinishVo("20", "PROCESSING", "WAIT_PROCESSING", "now");
        DiagnosticStatusVo status = new DiagnosticStatusVo("20", "PROCESSING", "WAIT_PROCESSING", List.of(), null);
        DiagnosticReportVo report = new DiagnosticReportVo("COMPLETED", "AVAILABLE", "VIEW_DIAGNOSTIC_REPORT", null);
        when(service.preflight(userId)).thenReturn(preflight);
        when(service.start(userId, "start-request", startCommand)).thenReturn(start);
        when(service.session(userId, sessionId)).thenReturn(session);
        when(service.item(userId, sessionId, 1)).thenReturn(item);
        when(service.timerEvent(userId, sessionId, "timer-request", timerEventCommand)).thenReturn(timerEvent);
        when(service.saveDraft(userId, sessionId, 1, "draft-request", draftCommand)).thenReturn(draft);
        when(service.pause(userId, sessionId, "pause-request", pauseCommand)).thenReturn(session);
        when(service.finishCheck(userId, sessionId)).thenReturn(finishCheck);
        when(service.finish(userId, sessionId, "finish-request", finishCommand)).thenReturn(finish);
        when(service.status(userId, sessionId)).thenReturn(status);
        when(service.regenerate(userId, sessionId, "retry-request")).thenReturn(status);
        when(service.report(userId, sessionId)).thenReturn(report);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(userId);

            assertThat(controller.preflight().getData()).isSameAs(preflight);
            assertThat(controller.start("start-request", startCommand).getData()).isSameAs(start);
            assertThat(controller.session(sessionId).getData()).isSameAs(session);
            assertThat(controller.item(sessionId, 1).getData()).isSameAs(item);
            assertThat(controller.timerEvent(sessionId, "timer-request", timerEventCommand).getData()).isSameAs(timerEvent);
            assertThat(controller.draft(sessionId, 1, "draft-request", draftCommand).getData()).isSameAs(draft);
            assertThat(controller.pause(sessionId, "pause-request", pauseCommand).getData()).isSameAs(session);
            assertThat(controller.finishCheck(sessionId).getData()).isSameAs(finishCheck);
            assertThat(controller.finish(sessionId, "finish-request", finishCommand).getData()).isSameAs(finish);
            assertThat(controller.status(sessionId).getData()).isSameAs(status);
            assertThat(controller.regenerate(sessionId, "retry-request").getData()).isSameAs(status);
            assertThat(controller.report(sessionId).getData()).isSameAs(report);
        }

        verify(service).preflight(userId);
        verify(service).start(userId, "start-request", startCommand);
        verify(service).session(userId, sessionId);
        verify(service).item(userId, sessionId, 1);
        verify(service).timerEvent(userId, sessionId, "timer-request", timerEventCommand);
        verify(service).saveDraft(userId, sessionId, 1, "draft-request", draftCommand);
        verify(service).pause(userId, sessionId, "pause-request", pauseCommand);
        verify(service).finishCheck(userId, sessionId);
        verify(service).finish(userId, sessionId, "finish-request", finishCommand);
        verify(service).status(userId, sessionId);
        verify(service).regenerate(userId, sessionId, "retry-request");
        verify(service).report(userId, sessionId);
    }
}
