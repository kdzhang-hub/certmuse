package org.dromara.certmuse.assessment.service.impl;

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
import org.dromara.certmuse.assessment.mapper.DiagnosticMapper;
import org.dromara.certmuse.assessment.support.DiagnosticException;
import org.dromara.certmuse.question.service.CollectionService;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Public diagnostic use cases that protect previously persisted learner work and server-authoritative timing.
 */
@Tag("dev")
class DiagnosticServiceRemainingWorkflowTest {
    private static final long USER_ID = 17L;
    private static final long GOAL_ID = 27L;
    private static final long REVISION_ID = 37L;
    private static final long SESSION_ID = 47L;

    private final DiagnosticMapper mapper = mock(DiagnosticMapper.class);
    private final CollectionService collections = mock(CollectionService.class);
    private final QuestionImageUrlService imageUrls = mock(QuestionImageUrlService.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final DiagnosticServiceImpl service = new DiagnosticServiceImpl(mapper, json, imageUrls, collections);

    @Test
    void startKeepsAnExistingTerminalSessionInsteadOfCreatingAnotherOne() {
        DiagnosticGoalRow goal = goal(4L);
        when(mapper.selectActiveGoal(USER_ID)).thenReturn(goal);
        when(mapper.selectLatestSession(USER_ID, GOAL_ID)).thenReturn(session("submitted", 4L, 8));

        assertDiagnosticError(() -> service.start(USER_ID, "terminal-session", start(4L)), 409,
            "DIAGNOSTIC_STATE_CONFLICT");

        verify(mapper, never()).insertSession(anyLong(), anyLong(), any(), any(), anyLong(), anyString());
    }

    @Test
    void startRejectsEveryIncompleteFrozenQuestionFactBeforeCreatingASession() {
        DiagnosticGoalRow goal = goal(4L);
        DiagnosticRevisionRow revision = revision();
        when(mapper.selectActiveGoal(USER_ID)).thenReturn(goal);
        when(mapper.selectLatestSession(USER_ID, GOAL_ID)).thenReturn(null);
        when(mapper.selectAvailableRevision(goal.getCertificationId(), goal.getSyllabusVersionId())).thenReturn(revision);
        when(collections.isFirstDiagnosticReady(REVISION_ID)).thenReturn(true);
        when(mapper.selectRevisionItems(REVISION_ID)).thenReturn(
            Collections.nCopies(50, revisionItem(null, 1L)),
            Collections.nCopies(50, revisionItem(0, 1L)),
            Collections.nCopies(50, revisionItem(60, null))
        );

        assertDiagnosticError(() -> service.start(USER_ID, "missing-duration", start(4L)), 422,
            "FIRST_DIAGNOSTIC_NOT_READY");
        assertDiagnosticError(() -> service.start(USER_ID, "zero-duration", start(4L)), 422,
            "FIRST_DIAGNOSTIC_NOT_READY");
        assertDiagnosticError(() -> service.start(USER_ID, "missing-knowledge", start(4L)), 422,
            "FIRST_DIAGNOSTIC_NOT_READY");

        verify(mapper, never()).insertSession(anyLong(), anyLong(), any(), any(), anyLong(), anyString());
    }

    @Test
    void itemReturnsFrozenImageMetadataThroughTheLearnerServiceBoundary() {
        DiagnosticItemRow item = item();
        item.setPresentationSnapshot("""
            {"questionType":"CHOICE","stem":"带图题干","difficulty":"MEDIUM","options":[],
             "images":[{"sourceUrl":"https://cdn.example.test/diagram.png","alt":"架构图","sortOrder":2}]}
            """);
        item.setAnswerData("{\"value\":null}");
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session("in_progress", 3L, 1));
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 1)).thenReturn(item);
        when(imageUrls.accessUrl("https://cdn.example.test/diagram.png", null))
            .thenReturn("https://cdn.example.test/diagram.png");

        var response = service.item(USER_ID, SESSION_ID, 1);

        assertThat(response.images()).singleElement().satisfies(image -> {
            assertThat(image.url()).isEqualTo("https://cdn.example.test/diagram.png");
            assertThat(image.alt()).isEqualTo("架构图");
            assertThat(image.sortOrder()).isEqualTo(2);
        });
        verify(imageUrls).accessUrl("https://cdn.example.test/diagram.png", null);
    }

    @Test
    void saveAndPauseExposeAnOptimisticLockConflictAfterTheLearnerHasProvidedValidInput() throws Exception {
        DiagnosticSessionRow active = session("in_progress", 3L, 1);
        DiagnosticItemRow item = item();
        item.setAnswerId(601L);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(active);
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 1)).thenReturn(item);
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 2)).thenReturn(item());
        when(mapper.updateSessionPosition(SESSION_ID, 3L, 1)).thenReturn(0);
        when(mapper.updateSessionPosition(SESSION_ID, 3L, 2)).thenReturn(0);

        assertDiagnosticError(() -> service.saveDraft(USER_ID, SESSION_ID, 1, "draft-write-race", draft(3L, 1, "A")),
            409, "DIAGNOSTIC_SESSION_VERSION_CONFLICT");
        assertDiagnosticError(() -> service.pause(USER_ID, SESSION_ID, "pause-write-race", pause(3L, 2)),
            409, "DIAGNOSTIC_SESSION_VERSION_CONFLICT");
    }

    @Test
    void timerRejectsMissingEventTypeBeforeLoadingAnyLearnerSession() {
        assertDiagnosticError(() -> service.timerEvent(USER_ID, SESSION_ID, "missing-event", timer(null, 1, null)),
            400, "DIAGNOSTIC_REQUEST_INVALID");
        verify(mapper, never()).lockSession(anyLong(), anyLong());
    }

    @Test
    void timerEnterClosesAnExpiredLeaseBeforeReplacingItForTheNewWindow() {
        DiagnosticTimerLeaseRow expired = lease("old-lease", 1, 701L, OffsetDateTime.now().minusSeconds(21));
        DiagnosticItemRow item = item();
        item.setAttemptId(702L);
        when(mapper.selectDiagnosticIdempotency("enter-replacement")).thenReturn(null, idempotency(801L));
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session("in_progress", 3L, 1));
        when(mapper.lockTimerLease(SESSION_ID)).thenReturn(expired);
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 1)).thenReturn(item);
        when(mapper.selectTiming(USER_ID, SESSION_ID)).thenReturn(timing());

        var response = service.timerEvent(USER_ID, SESSION_ID, "enter-replacement", timer(" enter ", 1, null));

        assertThat(response.eventAccepted()).isTrue();
        assertThat(response.timerState()).isEqualTo("RUNNING");
        verify(mapper).markTimerInvalid(701L, "新的窗口或设备进入前心跳已超时");
        verify(mapper).deleteTimerLease(SESSION_ID);
        verify(mapper).insertTimerLease(eq(SESSION_ID), eq(1), eq(702L), anyString());
    }

    @Test
    void timerTreatsTheSameLeaseOnAnotherQuestionAsReplaced() {
        when(mapper.selectDiagnosticIdempotency("other-question")).thenReturn(null, idempotency(802L));
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session("in_progress", 3L, 1));
        when(mapper.lockTimerLease(SESSION_ID)).thenReturn(lease("current-lease", 1, 711L, OffsetDateTime.now()));
        when(mapper.selectTiming(USER_ID, SESSION_ID)).thenReturn(timing());

        var response = service.timerEvent(USER_ID, SESSION_ID, "other-question", timer("HEARTBEAT", 2, "current-lease"));

        assertThat(response.eventAccepted()).isFalse();
        assertThat(response.timerState()).isEqualTo("REPLACED");
        verify(mapper, never()).accumulateTimerInterval(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void timerLeaveStopsAValidLeaseUsingTheLeaveReason() {
        when(mapper.selectDiagnosticIdempotency("leave-question")).thenReturn(null, idempotency(803L));
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session("in_progress", 3L, 1));
        when(mapper.lockTimerLease(SESSION_ID)).thenReturn(lease("leave-lease", 1, 721L, OffsetDateTime.now().minusSeconds(3)));
        when(mapper.selectTiming(USER_ID, SESSION_ID)).thenReturn(timing());

        var response = service.timerEvent(USER_ID, SESSION_ID, "leave-question", timer("LEAVE", 1, "leave-lease"));

        assertThat(response.eventAccepted()).isTrue();
        assertThat(response.leaseId()).isNull();
        assertThat(response.timerState()).isEqualTo("STOPPED");
        verify(mapper).accumulateTimerInterval(eq(721L), org.mockito.ArgumentMatchers.intThat(seconds -> seconds > 0));
        verify(mapper, never()).markTimerInvalid(eq(721L), anyString());
    }

    @Test
    void reportTreatsAnExistingButUnavailableArtifactAsAWorkerFailure() {
        DiagnosticReportRow unavailable = new DiagnosticReportRow();
        unavailable.setStatus("writing");
        DiagnosticJobRow failed = new DiagnosticJobRow();
        failed.setStatus("failed");
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session("submitted", 3L, 1));
        when(mapper.selectReport(USER_ID, SESSION_ID)).thenReturn(unavailable);
        when(mapper.selectJob("DIAGNOSTIC:" + SESSION_ID)).thenReturn(failed);

        assertDiagnosticError(() -> service.report(USER_ID, SESSION_ID), 409, "DIAGNOSTIC_RESULT_FAILED");
    }

    @Test
    void finishReplaysLegacyBareResponsesWithoutResubmittingTheDiagnostic() {
        DiagnosticIdempotencyRow replay = idempotency(901L);
        replay.setPayloadHash("4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce");
        replay.setStatus("succeeded");
        replay.setResponseBody("""
            {"sessionId":"47","diagnosticStatus":"PROCESSING","nextAction":"WAIT_PROCESSING",
             "submittedAt":"2026-08-13T12:00:00+08:00"}
            """);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session("in_progress", 3L, 1));
        when(mapper.selectDiagnosticIdempotency("legacy-finish")).thenReturn(replay);

        var response = service.finish(USER_ID, SESSION_ID, "legacy-finish", finish(3L));

        assertThat(response.diagnosticStatus()).isEqualTo("PROCESSING");
        assertThat(response.submittedAt()).isEqualTo("2026-08-13T12:00:00+08:00");
        verify(mapper, never()).submitSession(anyLong(), anyLong());
        verify(mapper, never()).insertJob(anyLong(), anyString(), anyString());
    }

    @Test
    void hasInProgressDiagnosticReturnsFalseWhenTheGoalHasNoSession() {
        when(mapper.selectLatestSession(USER_ID, GOAL_ID)).thenReturn(null);

        assertThat(service.hasInProgressDiagnostic(USER_ID, GOAL_ID)).isFalse();
    }

    private static DiagnosticGoalRow goal(long version) {
        DiagnosticGoalRow goal = new DiagnosticGoalRow();
        goal.setId(GOAL_ID);
        goal.setCertificationId(31L);
        goal.setCertificationName("系统架构设计师");
        goal.setSyllabusVersionId(41L);
        goal.setSyllabusVersionName("2025版");
        goal.setRowVersion(version);
        return goal;
    }

    private static DiagnosticRevisionRow revision() {
        DiagnosticRevisionRow revision = new DiagnosticRevisionRow();
        revision.setId(REVISION_ID);
        revision.setQuestionCount(50);
        revision.setDurationMinutes(50);
        return revision;
    }

    private static DiagnosticSessionRow session(String status, long version, int order) {
        DiagnosticSessionRow session = new DiagnosticSessionRow();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setGoalId(GOAL_ID);
        session.setStatus(status);
        session.setRowVersion(version);
        session.setLastQuestionOrder(order);
        return session;
    }

    private static DiagnosticItemRow revisionItem(Integer estimatedSeconds, Long knowledgePointId) {
        DiagnosticItemRow item = new DiagnosticItemRow();
        item.setQuestionType("CHOICE");
        item.setEstimatedSecondsSnapshot(estimatedSeconds);
        item.setKnowledgePointId(knowledgePointId);
        return item;
    }

    private static DiagnosticItemRow item() {
        DiagnosticItemRow item = new DiagnosticItemRow();
        item.setQuestionOrder(1);
        item.setAnswerData("{\"value\":null}");
        item.setPresentationSnapshot("{\"questionType\":\"CHOICE\"}");
        return item;
    }

    private static DiagnosticTimerLeaseRow lease(String leaseId, int order, long attemptId, OffsetDateTime heartbeat) {
        DiagnosticTimerLeaseRow lease = new DiagnosticTimerLeaseRow();
        lease.setSessionId(SESSION_ID);
        lease.setLeaseId(leaseId);
        lease.setQuestionOrder(order);
        lease.setAttemptId(attemptId);
        lease.setLastHeartbeatAt(heartbeat);
        return lease;
    }

    private static DiagnosticTimingRow timing() {
        DiagnosticTimingRow timing = new DiagnosticTimingRow();
        timing.setEstimatedDurationSeconds(3_000);
        timing.setEffectiveElapsedSeconds(88L);
        timing.setServerTime("2026-08-13T12:00:00+08:00");
        return timing;
    }

    private static DiagnosticIdempotencyRow idempotency(long id) {
        DiagnosticIdempotencyRow record = new DiagnosticIdempotencyRow();
        record.setId(id);
        return record;
    }

    private static DiagnosticStartBo start(long goalVersion) {
        DiagnosticStartBo command = new DiagnosticStartBo();
        command.setDiagnosticRevisionId(String.valueOf(REVISION_ID));
        command.setGoalVersion(goalVersion);
        return command;
    }

    private DiagnosticDraftBo draft(long expectedVersion, int order, String answer) throws Exception {
        DiagnosticDraftBo command = new DiagnosticDraftBo();
        command.setExpectedSessionVersion(expectedVersion);
        command.setCurrentQuestionOrder(order);
        command.setAnswer(json.readTree("{\"schema_version\":\"1.0\",\"answer_type\":\"CHOICE\",\"value\":\"" + answer + "\"}"));
        return command;
    }

    private static DiagnosticPauseBo pause(long expectedVersion, int order) {
        DiagnosticPauseBo command = new DiagnosticPauseBo();
        command.setExpectedSessionVersion(expectedVersion);
        command.setCurrentQuestionOrder(order);
        return command;
    }

    private static DiagnosticFinishBo finish(long expectedVersion) {
        DiagnosticFinishBo command = new DiagnosticFinishBo();
        command.setExpectedSessionVersion(expectedVersion);
        return command;
    }

    private static DiagnosticTimerEventBo timer(String type, int order, String leaseId) {
        DiagnosticTimerEventBo command = new DiagnosticTimerEventBo();
        command.setEventType(type);
        command.setQuestionOrder(order);
        command.setLeaseId(leaseId);
        return command;
    }

    private static void assertDiagnosticError(ThrowingCall call, int status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(DiagnosticException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(status);
            assertThat(error.getData().errorCode()).isEqualTo(code);
        });
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
