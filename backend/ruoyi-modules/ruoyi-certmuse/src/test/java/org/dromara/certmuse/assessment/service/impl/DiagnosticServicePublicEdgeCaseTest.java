package org.dromara.certmuse.assessment.service.impl;

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
import org.dromara.certmuse.assessment.mapper.DiagnosticMapper;
import org.dromara.certmuse.assessment.support.DiagnosticException;
import org.dromara.certmuse.question.service.CollectionService;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Public edge cases for the learner-facing first-diagnostic lifecycle. */
@Tag("dev")
class DiagnosticServicePublicEdgeCaseTest {
    private static final long USER_ID = 71L;
    private static final long GOAL_ID = 81L;
    private static final long REVISION_ID = 91L;
    private static final long SESSION_ID = 101L;
    private static final String DRAFT_A_HASH = "5ea394296c0dadbc98c22169041a6200ee3bfdad50b0de866614519a8aa1fb7d";
    private static final String VERSION_THREE_HASH = "bb352f840be2060216924d6f1f04994eb50075c2f98ec9b0be85e780089aa53a";

    private final DiagnosticMapper mapper = mock(DiagnosticMapper.class);
    private final CollectionService collections = mock(CollectionService.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final DiagnosticServiceImpl service = new DiagnosticServiceImpl(
        mapper, json, mock(QuestionImageUrlService.class), collections
    );

    @Test
    void freshStartCreatesTheFrozenQuestionSetAndKeepsAnswerSchemaFallbacksSafe() {
        DiagnosticGoalRow goal = goal(3L);
        DiagnosticRevisionRow revision = revision();
        List<DiagnosticItemRow> items = IntStream.rangeClosed(1, 50)
            .mapToObj(order -> revisionItem(order, order == 1 ? "{\"selection_mode\":\"multiple\"}" : order == 2 ? "not-json" : "{}"))
            .toList();
        when(mapper.selectDiagnosticIdempotency("fresh-start")).thenReturn(null, idempotency(501L));
        when(mapper.selectActiveGoal(USER_ID)).thenReturn(goal);
        when(mapper.selectLatestSession(USER_ID, GOAL_ID)).thenReturn(null);
        when(mapper.selectAvailableRevision(goal.getCertificationId(), goal.getSyllabusVersionId())).thenReturn(revision);
        when(collections.isFirstDiagnosticReady(REVISION_ID)).thenReturn(true);
        when(mapper.selectRevisionItems(REVISION_ID)).thenReturn(items);
        when(mapper.lockSession(eq(USER_ID), anyLong())).thenAnswer(invocation -> session(
            invocation.getArgument(1), "in_progress", 3L, 1
        ));
        when(mapper.countAnswers(eq(USER_ID), anyLong())).thenReturn(count(0, 50, 1));

        var result = service.start(USER_ID, "fresh-start", start(3L));

        assertThat(result.resumed()).isFalse();
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(result.totalCount()).isEqualTo(50);
        assertThat(result.resumeQuestionOrder()).isEqualTo(1);
        assertThat(result.sessionVersion()).isEqualTo(3L);

        ArgumentCaptor<String> answers = ArgumentCaptor.forClass(String.class);
        verify(mapper, times(50)).insertAnswer(anyLong(), anyLong(), answers.capture());
        assertThat(answers.getAllValues()).anySatisfy(answer -> assertThat(answer)
            .contains("\"selection_mode\":\"multiple\"", "\"value\":null"));
        assertThat(answers.getAllValues()).anySatisfy(answer -> assertThat(answer)
            .contains("\"selection_mode\":\"single\"", "\"value\":null"));
        verify(mapper).completeIdempotency(eq(501L), anyString());
    }

    @Test
    void idempotentDraftReplayReturnsTheSavedAcknowledgementWithoutWritingAgain() {
        DiagnosticSessionRow active = session(SESSION_ID, "in_progress", 3L, 1);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(active);
        when(mapper.selectDiagnosticIdempotency("draft-replay")).thenReturn(idempotency(
            502L, DRAFT_A_HASH, "succeeded",
            "{\"schema_version\":\"diagnostic_idempotency/1.0\",\"response\":{\"savedAt\":\"2026-08-13T09:00:00+08:00\",\"answered\":true,\"answeredCount\":3,\"sessionVersion\":4}}"
        ));

        var replay = service.saveDraft(USER_ID, SESSION_ID, 1, "draft-replay", draft(3L, "A"));

        assertThat(replay.savedAt()).isEqualTo("2026-08-13T09:00:00+08:00");
        assertThat(replay.answered()).isTrue();
        assertThat(replay.answeredCount()).isEqualTo(3);
        assertThat(replay.sessionVersion()).isEqualTo(4L);
        verify(mapper, never()).updateAnswer(anyLong(), anyString());
        verify(mapper, never()).insertIdempotency(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void idempotencyConflictsAndUnreadableReplaysHaveStableLearnerErrors() {
        DiagnosticSessionRow active = session(SESSION_ID, "in_progress", 3L, 1);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(active);
        when(mapper.selectDiagnosticIdempotency("draft-pending")).thenReturn(idempotency(503L, DRAFT_A_HASH, "processing", null));
        when(mapper.selectDiagnosticIdempotency("draft-corrupt")).thenReturn(idempotency(504L, DRAFT_A_HASH, "succeeded", "not-json"));

        assertDiagnosticError(() -> service.saveDraft(USER_ID, SESSION_ID, 1, "draft-pending", draft(3L, "A")),
            409, "DIAGNOSTIC_IDEMPOTENCY_CONFLICT");
        assertDiagnosticError(() -> service.saveDraft(USER_ID, SESSION_ID, 1, "draft-corrupt", draft(3L, "A")),
            500, "DIAGNOSTIC_SYSTEM_FAILURE");
    }

    @Test
    void preflightExplainsMissingGoalsAndUnavailableFrozenCollections() {
        when(mapper.selectActiveGoal(USER_ID)).thenReturn(null);
        assertDiagnosticError(() -> service.preflight(USER_ID), 422, "FIRST_DIAGNOSTIC_NOT_READY");

        DiagnosticGoalRow goal = goal(3L);
        when(mapper.selectActiveGoal(USER_ID)).thenReturn(goal);
        when(mapper.selectLatestSession(USER_ID, GOAL_ID)).thenReturn(null);
        when(mapper.selectAvailableRevision(goal.getCertificationId(), goal.getSyllabusVersionId())).thenReturn(null);
        assertDiagnosticError(() -> service.preflight(USER_ID), 422, "FIRST_DIAGNOSTIC_NOT_READY");
    }

    @Test
    void finishCheckAndInProgressFlagExposeTheCurrentLearnerSessionState() {
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session(SESSION_ID, "in_progress", 4L, 6));
        when(mapper.countAnswers(USER_ID, SESSION_ID)).thenReturn(count(4, 50, 6));
        when(mapper.selectLatestSession(USER_ID, GOAL_ID)).thenReturn(
            session(SESSION_ID, "in_progress", 4L, 6), session(SESSION_ID, "submitted", 4L, 6)
        );

        var finishCheck = service.finishCheck(USER_ID, SESSION_ID);

        assertThat(finishCheck.answeredCount()).isEqualTo(4);
        assertThat(finishCheck.unansweredCount()).isEqualTo(46);
        assertThat(finishCheck.firstUnansweredQuestionOrder()).isEqualTo(6);
        assertThat(service.hasInProgressDiagnostic(USER_ID, GOAL_ID)).isTrue();
        assertThat(service.hasInProgressDiagnostic(USER_ID, GOAL_ID)).isFalse();
    }

    @Test
    void heartbeatPersistsServerTimingAndPauseClosesTheActiveTimer() {
        DiagnosticSessionRow active = session(SESSION_ID, "in_progress", 3L, 2);
        DiagnosticTimerLeaseRow heartbeatLease = lease("lease-current", 2, 611L, OffsetDateTime.now().minusSeconds(3));
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(active);
        when(mapper.selectDiagnosticIdempotency("heartbeat")).thenReturn(null, idempotency(505L));
        when(mapper.lockTimerLease(SESSION_ID)).thenReturn(heartbeatLease);
        when(mapper.selectTiming(USER_ID, SESSION_ID)).thenReturn(timing(3_000, 87L));

        var heartbeat = service.timerEvent(USER_ID, SESSION_ID, "heartbeat", timer("heartbeat", 2, "lease-current"));

        assertThat(heartbeat.eventAccepted()).isTrue();
        assertThat(heartbeat.timerState()).isEqualTo("RUNNING");
        assertThat(heartbeat.leaseId()).isEqualTo("lease-current");
        assertThat(heartbeat.effectiveElapsedSeconds()).isEqualTo(87L);
        ArgumentCaptor<Integer> heartbeatSeconds = ArgumentCaptor.forClass(Integer.class);
        verify(mapper).accumulateTimerInterval(eq(611L), heartbeatSeconds.capture());
        assertThat(heartbeatSeconds.getValue()).isPositive();
        verify(mapper).insertTimerLease(SESSION_ID, 2, 611L, "lease-current");

        DiagnosticTimerLeaseRow pauseLease = lease("lease-pause", 2, 612L, OffsetDateTime.now().minusSeconds(3));
        when(mapper.selectDiagnosticIdempotency("pause-lease")).thenReturn(null, idempotency(506L));
        when(mapper.lockTimerLease(SESSION_ID)).thenReturn(pauseLease);
        when(mapper.updateSessionPosition(SESSION_ID, 3L, 3)).thenReturn(1);
        when(mapper.countAnswers(USER_ID, SESSION_ID)).thenReturn(count(5, 50, 6));
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 3)).thenReturn(revisionItem(3, "{}"));
        when(mapper.selectSessionItems(USER_ID, SESSION_ID)).thenReturn(List.of());

        var paused = service.pause(USER_ID, SESSION_ID, "pause-lease", pause(3L, 3));

        assertThat(paused.nextAction()).isEqualTo("CONTINUE_DIAGNOSTIC");
        assertThat(paused.answeredCount()).isEqualTo(5);
        verify(mapper).accumulateTimerInterval(eq(612L), org.mockito.ArgumentMatchers.intThat(seconds -> seconds > 0));
        verify(mapper, times(2)).deleteTimerLease(SESSION_ID);
    }

    @Test
    void statusAndReportExposeIncompleteArtifactsAndARecoverableWorkerFailure() {
        DiagnosticSessionRow completed = session(SESSION_ID, "completed", 5L, 50);
        DiagnosticReportRow available = new DiagnosticReportRow();
        available.setStatus("available");
        available.setSubjectScores("{\"subjects\":[]}");
        available.setProfileSummary("{\"dataStatus\":\"PROFILE_PENDING\"}");
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(completed);
        when(mapper.selectJob("DIAGNOSTIC:" + SESSION_ID)).thenReturn(null);
        when(mapper.selectReport(USER_ID, SESSION_ID)).thenReturn(available);
        when(mapper.countSessionProfileEvidence(SESSION_ID)).thenReturn(0);

        var incomplete = service.status(USER_ID, SESSION_ID);
        var incompleteReport = service.report(USER_ID, SESSION_ID);

        assertThat(incomplete.diagnosticStatus()).isEqualTo("FAILED");
        assertThat(incomplete.nextAction()).isEqualTo("RETRY_DIAGNOSTIC_RESULT");
        assertThat(incomplete.stages()).first().satisfies(stage -> assertThat(stage.state()).isEqualTo("FAILED"));
        assertThat(incompleteReport.profileStatus()).isEqualTo("FAILED");
        assertThat(incompleteReport.nextAction()).isEqualTo("RETRY_DIAGNOSTIC_RESULT");

        DiagnosticJobRow failed = new DiagnosticJobRow();
        failed.setStatus("failed");
        when(mapper.selectReport(USER_ID, SESSION_ID)).thenReturn(null);
        when(mapper.selectJob("DIAGNOSTIC:" + SESSION_ID)).thenReturn(failed);
        assertDiagnosticError(() -> service.report(USER_ID, SESSION_ID), 409, "DIAGNOSTIC_RESULT_FAILED");
    }

    @Test
    void finishReportsAVersionConflictWhenAnotherDeviceAlreadyChangedTheSession() {
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session(SESSION_ID, "in_progress", 3L, 2));
        when(mapper.selectDiagnosticIdempotency("finish-race")).thenReturn(null);
        when(mapper.lockTimerLease(SESSION_ID)).thenReturn(null);
        when(mapper.submitSession(SESSION_ID, 3L)).thenReturn(0);

        assertDiagnosticError(() -> service.finish(USER_ID, SESSION_ID, "finish-race", finish(3L)),
            409, "DIAGNOSTIC_SESSION_VERSION_CONFLICT");
    }

    @Test
    void pauseReplayReturnsThePersistedPublicResponseWithoutRepeatingMutations() {
        DiagnosticSessionRow active = session(SESSION_ID, "in_progress", 3L, 2);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(active);
        when(mapper.selectDiagnosticIdempotency("pause-replay")).thenReturn(idempotency(
            507L, VERSION_THREE_HASH, "succeeded",
            "{\"response\":{\"sessionId\":\"101\",\"status\":\"IN_PROGRESS\",\"nextAction\":\"CONTINUE_DIAGNOSTIC\",\"sessionVersion\":4,\"currentQuestionOrder\":3,\"answeredCount\":2,\"unansweredCount\":48,\"navigation\":[],\"estimatedDurationSeconds\":0,\"effectiveElapsedSeconds\":0,\"serverTime\":\"now\"}}"
        ));

        var paused = service.pause(USER_ID, SESSION_ID, "pause-replay", pause(3L, 3));

        assertThat(paused.currentQuestionOrder()).isEqualTo(3);
        assertThat(paused.answeredCount()).isEqualTo(2);
        verify(mapper, never()).updateSessionPosition(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void finishReplayUsesTheActualRecordComponentNamesStoredByTheService() {
        DiagnosticSessionRow active = session(SESSION_ID, "in_progress", 3L, 2);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(active);
        when(mapper.selectDiagnosticIdempotency("finish-replay")).thenReturn(idempotency(
            508L, "4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce", "succeeded",
            "{\"response\":{\"sessionId\":\"101\",\"diagnosticStatus\":\"PROCESSING\",\"nextAction\":\"WAIT_PROCESSING\",\"submittedAt\":\"2026-08-13T10:00:00+08:00\"}}"
        ));

        var replay = service.finish(USER_ID, SESSION_ID, "finish-replay", finish(3L));

        assertThat(replay.diagnosticStatus()).isEqualTo("PROCESSING");
        assertThat(replay.nextAction()).isEqualTo("WAIT_PROCESSING");
        assertThat(replay.submittedAt()).isEqualTo("2026-08-13T10:00:00+08:00");
        verify(mapper, never()).submitSession(anyLong(), anyLong());
        verify(mapper, never()).insertJob(anyLong(), anyString(), anyString());
    }

    @Test
    void publicEndpointsRejectInvalidOrdersAndAnswerDocumentsBeforePersistingChanges() {
        assertDiagnosticError(() -> service.item(USER_ID, SESSION_ID, 0), 400, "DIAGNOSTIC_REQUEST_INVALID");
        assertDiagnosticError(() -> service.timerEvent(USER_ID, SESSION_ID, "invalid-event", timer("unknown", 1, null)),
            400, "DIAGNOSTIC_REQUEST_INVALID");

        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session(SESSION_ID, "in_progress", 3L, 1));
        when(mapper.selectDiagnosticIdempotency("invalid-answer")).thenReturn(null);
        DiagnosticItemRow existingItem = new DiagnosticItemRow();
        existingItem.setAnswerId(601L);
        existingItem.setPresentationSnapshot("{\"questionType\":\"CHOICE\"}");
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 1)).thenReturn(existingItem);
        DiagnosticDraftBo invalid = new DiagnosticDraftBo();
        invalid.setExpectedSessionVersion(3L);
        invalid.setCurrentQuestionOrder(1);
        invalid.setAnswer(json.readTree("{\"schema_version\":\"2.0\",\"answer_type\":\"TEXT\",\"value\":\"A\"}"));

        assertDiagnosticError(() -> service.saveDraft(USER_ID, SESSION_ID, 1, "invalid-answer", invalid),
            422, "DIAGNOSTIC_ANSWER_INVALID");
        verify(mapper, never()).updateAnswer(anyLong(), anyString());
    }

    @Test
    void publicEndpointsRejectMissingItemsAndSessionsAndNonEditableStates() {
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(null);
        assertDiagnosticError(() -> service.finishCheck(USER_ID, SESSION_ID), 404, "DIAGNOSTIC_SESSION_NOT_FOUND");

        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session(SESSION_ID, "submitted", 3L, 1));
        assertDiagnosticError(() -> service.pause(USER_ID, SESSION_ID, "pause-submitted", pause(3L, 1)),
            409, "DIAGNOSTIC_STATE_CONFLICT");

        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session(SESSION_ID, "in_progress", 3L, 1));
        when(mapper.selectDiagnosticIdempotency("missing-item")).thenReturn(null);
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 1)).thenReturn(null);
        assertDiagnosticError(() -> service.saveDraft(USER_ID, SESSION_ID, 1, "missing-item", draft(3L, "A")),
            404, "DIAGNOSTIC_SESSION_NOT_FOUND");
    }

    @Test
    void regenerateExposesBothUnavailableRetryReasons() {
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session(SESSION_ID, "submitted", 3L, 1));
        when(mapper.selectDiagnosticIdempotency("retry-no-job")).thenReturn(null);
        when(mapper.selectJob("DIAGNOSTIC:" + SESSION_ID)).thenReturn(null);
        assertDiagnosticError(() -> service.regenerate(USER_ID, SESSION_ID, "retry-no-job"), 409, "DIAGNOSTIC_STATE_CONFLICT");

        DiagnosticJobRow running = new DiagnosticJobRow();
        running.setStatus("queued");
        when(mapper.selectDiagnosticIdempotency("retry-pending")).thenReturn(null);
        when(mapper.selectJob("DIAGNOSTIC:" + SESSION_ID)).thenReturn(running);
        assertDiagnosticError(() -> service.regenerate(USER_ID, SESSION_ID, "retry-pending"), 409, "DIAGNOSTIC_STATE_CONFLICT");
        verify(mapper, never()).retryJob(anyString());
    }

    private static DiagnosticGoalRow goal(long version) {
        DiagnosticGoalRow goal = new DiagnosticGoalRow();
        goal.setId(GOAL_ID);
        goal.setCertificationId(41L);
        goal.setCertificationName("系统架构设计师");
        goal.setSyllabusVersionId(51L);
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

    private static DiagnosticItemRow revisionItem(int order, String schema) {
        DiagnosticItemRow item = new DiagnosticItemRow();
        item.setQuestionOrder(order);
        item.setQuestionType("CHOICE");
        item.setEstimatedSecondsSnapshot(60);
        item.setKnowledgePointId((long) order);
        item.setAnswerSchema(schema);
        return item;
    }

    private static DiagnosticSessionRow session(long id, String status, long version, int order) {
        DiagnosticSessionRow session = new DiagnosticSessionRow();
        session.setId(id);
        session.setUserId(USER_ID);
        session.setGoalId(GOAL_ID);
        session.setStatus(status);
        session.setRowVersion(version);
        session.setLastQuestionOrder(order);
        return session;
    }

    private static DiagnosticCountRow count(int answered, int total, Integer firstUnanswered) {
        DiagnosticCountRow count = new DiagnosticCountRow();
        count.setAnsweredCount(answered);
        count.setTotalCount(total);
        count.setFirstUnanswered(firstUnanswered);
        return count;
    }

    private static DiagnosticTimingRow timing(int estimatedSeconds, long elapsedSeconds) {
        DiagnosticTimingRow timing = new DiagnosticTimingRow();
        timing.setEstimatedDurationSeconds(estimatedSeconds);
        timing.setEffectiveElapsedSeconds(elapsedSeconds);
        timing.setServerTime("2026-08-13T10:00:00+08:00");
        return timing;
    }

    private static DiagnosticTimerLeaseRow lease(String id, int order, long attemptId, OffsetDateTime heartbeat) {
        DiagnosticTimerLeaseRow lease = new DiagnosticTimerLeaseRow();
        lease.setSessionId(SESSION_ID);
        lease.setLeaseId(id);
        lease.setQuestionOrder(order);
        lease.setAttemptId(attemptId);
        lease.setLastHeartbeatAt(heartbeat);
        return lease;
    }

    private static DiagnosticIdempotencyRow idempotency(long id) {
        DiagnosticIdempotencyRow record = new DiagnosticIdempotencyRow();
        record.setId(id);
        return record;
    }

    private static DiagnosticIdempotencyRow idempotency(long id, String hash, String status, String response) {
        DiagnosticIdempotencyRow record = idempotency(id);
        record.setPayloadHash(hash);
        record.setStatus(status);
        record.setResponseBody(response);
        return record;
    }

    private static DiagnosticStartBo start(long goalVersion) {
        DiagnosticStartBo command = new DiagnosticStartBo();
        command.setDiagnosticRevisionId(String.valueOf(REVISION_ID));
        command.setGoalVersion(goalVersion);
        return command;
    }

    private DiagnosticDraftBo draft(long expectedVersion, String value) {
        DiagnosticDraftBo command = new DiagnosticDraftBo();
        command.setExpectedSessionVersion(expectedVersion);
        command.setCurrentQuestionOrder(1);
        command.setAnswer(json.readTree("{\"schema_version\":\"1.0\",\"answer_type\":\"CHOICE\",\"value\":\"" + value + "\"}"));
        return command;
    }

    private static DiagnosticTimerEventBo timer(String type, int order, String leaseId) {
        DiagnosticTimerEventBo command = new DiagnosticTimerEventBo();
        command.setEventType(type);
        command.setQuestionOrder(order);
        command.setLeaseId(leaseId);
        return command;
    }

    private static DiagnosticPauseBo pause(long expectedVersion, int currentOrder) {
        DiagnosticPauseBo command = new DiagnosticPauseBo();
        command.setExpectedSessionVersion(expectedVersion);
        command.setCurrentQuestionOrder(currentOrder);
        return command;
    }

    private static DiagnosticFinishBo finish(long expectedVersion) {
        DiagnosticFinishBo command = new DiagnosticFinishBo();
        command.setExpectedSessionVersion(expectedVersion);
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
