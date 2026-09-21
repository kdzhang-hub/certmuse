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
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Public learner workflows through the diagnostic service boundary. */
@Tag("dev")
class DiagnosticServicePublicWorkflowTest {
    private static final long USER_ID = 7L;
    private static final long SESSION_ID = 101L;

    private final DiagnosticMapper mapper = mock(DiagnosticMapper.class);
    private final CollectionService collections = mock(CollectionService.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final DiagnosticServiceImpl service = new DiagnosticServiceImpl(
        mapper, json, new QuestionImageUrlService(), collections
    );

    @Test
    void startResumesAnInProgressDiagnosticInsteadOfCreatingAnotherSession() {
        DiagnosticGoalRow goal = goal(4L);
        DiagnosticSessionRow session = session("in_progress", 3L, 12);
        when(mapper.selectDiagnosticIdempotency("start-resume")).thenReturn(null);
        when(mapper.selectActiveGoal(USER_ID)).thenReturn(goal);
        when(mapper.selectLatestSession(USER_ID, goal.getId())).thenReturn(session);
        when(mapper.countAnswers(USER_ID, SESSION_ID)).thenReturn(count(11, 19));
        when(mapper.selectDiagnosticIdempotency("start-resume")).thenReturn(
            null, idempotency(1L)
        );

        var result = service.start(USER_ID, "start-resume", start("50", 4L));

        assertThat(result.resumed()).isTrue();
        assertThat(result.sessionId()).isEqualTo(String.valueOf(SESSION_ID));
        assertThat(result.answeredCount()).isEqualTo(11);
        assertThat(result.totalCount()).isEqualTo(19);
        verify(mapper, never()).insertSession(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyLong(), anyString());
    }

    @Test
    void startRejectsAStaleGoalAndARevisionThatIsNotReady() {
        DiagnosticGoalRow goal = goal(5L);
        when(mapper.selectDiagnosticIdempotency("stale-goal")).thenReturn(null);
        when(mapper.selectActiveGoal(USER_ID)).thenReturn(goal);
        when(mapper.selectLatestSession(USER_ID, goal.getId())).thenReturn(null);

        assertDiagnosticError(() -> service.start(USER_ID, "stale-goal", start("50", 4L)), 409,
            "DIAGNOSTIC_STATE_CONFLICT");

        when(mapper.selectDiagnosticIdempotency("wrong-revision")).thenReturn(null);
        DiagnosticRevisionRow revision = revision(51L);
        when(mapper.selectAvailableRevision(goal.getCertificationId(), goal.getSyllabusVersionId())).thenReturn(revision);
        when(collections.isFirstDiagnosticReady(revision.getId())).thenReturn(true);

        assertDiagnosticError(() -> service.start(USER_ID, "wrong-revision", start("50", 5L)), 422,
            "FIRST_DIAGNOSTIC_NOT_READY");
    }

    @Test
    void sessionReportsCurrentAnsweredAndUnansweredNavigationWithServerTiming() {
        DiagnosticSessionRow session = session("in_progress", 6L, 2);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session);
        when(mapper.countAnswers(USER_ID, SESSION_ID)).thenReturn(count(1, 3));
        when(mapper.selectSessionItems(USER_ID, SESSION_ID)).thenReturn(List.of(
            item(1, "{\"value\":\"A\"}"), item(2, "{\"value\":null}"), item(3, "not-json")
        ));
        DiagnosticTimingRow timing = new DiagnosticTimingRow();
        timing.setEstimatedDurationSeconds(3_000);
        timing.setEffectiveElapsedSeconds(77L);
        timing.setServerTime("2026-08-13T10:00:00+08:00");
        when(mapper.selectTiming(USER_ID, SESSION_ID)).thenReturn(timing);

        var result = service.session(USER_ID, SESSION_ID);

        assertThat(result.nextAction()).isEqualTo("CONTINUE_DIAGNOSTIC");
        assertThat(result.navigation()).extracting(n -> n.state())
            .containsExactly("ANSWERED", "CURRENT", "UNANSWERED");
        assertThat(result.effectiveElapsedSeconds()).isEqualTo(77L);
        assertThat(result.serverTime()).isEqualTo("2026-08-13T10:00:00+08:00");
    }

    @Test
    void itemReturnsPresentationAndTurnsMalformedSnapshotsIntoAStableSystemFailure() {
        DiagnosticSessionRow session = session("in_progress", 2L, 1);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session);
        DiagnosticItemRow valid = item(1, "{\"schema_version\":\"1.0\",\"answer_type\":\"CHOICE\",\"value\":\"A\"}");
        valid.setPresentationSnapshot("""
            {"questionType":"CHOICE","stem":"题干","difficulty":"easy",
             "options":[{"label":"A","content":"选项","sortOrder":1}],"images":[]}
            """);
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 1)).thenReturn(valid);

        var result = service.item(USER_ID, SESSION_ID, 1);

        assertThat(result.stem()).isEqualTo("题干");
        assertThat(result.options()).singleElement().satisfies(option -> assertThat(option.label()).isEqualTo("A"));

        DiagnosticItemRow broken = item(2, "{}");
        broken.setPresentationSnapshot("not-json");
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 2)).thenReturn(broken);
        assertDiagnosticError(() -> service.item(USER_ID, SESSION_ID, 2), 500, "DIAGNOSTIC_SYSTEM_FAILURE");
    }

    @Test
    void savingADraftRejectsWrongVersionAndPersistsAValidChoice() {
        DiagnosticSessionRow session = session("in_progress", 3L, 1);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session);
        when(mapper.selectDiagnosticIdempotency("draft-version")).thenReturn(null);

        assertDiagnosticError(() -> service.saveDraft(USER_ID, SESSION_ID, 1, "draft-version", draft(2L, "A")), 409,
            "DIAGNOSTIC_SESSION_VERSION_CONFLICT");

        when(mapper.selectDiagnosticIdempotency("draft-ok")).thenReturn(null, idempotency(2L));
        DiagnosticItemRow item = item(1, "{}");
        item.setAnswerId(400L);
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 1)).thenReturn(item);
        when(mapper.updateSessionPosition(SESSION_ID, 3L, 1)).thenReturn(1);
        when(mapper.countAnswers(USER_ID, SESSION_ID)).thenReturn(count(2, null));

        var result = service.saveDraft(USER_ID, SESSION_ID, 1, "draft-ok", draft(3L, "A"));

        assertThat(result.answered()).isTrue();
        assertThat(result.sessionVersion()).isEqualTo(4L);
        verify(mapper).updateAnswer(400L, "{\"schema_version\":\"1.0\",\"answer_type\":\"CHOICE\",\"value\":\"A\"}");
    }

    @Test
    void timerEventsAcceptAValidEnterAndReplaceAnObsoleteLease() {
        DiagnosticSessionRow session = session("in_progress", 3L, 1);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session);
        when(mapper.selectDiagnosticIdempotency("timer-enter")).thenReturn(null, idempotency(3L));
        DiagnosticItemRow item = item(1, "{}");
        item.setAttemptId(501L);
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 1)).thenReturn(item);
        when(mapper.selectTiming(USER_ID, SESSION_ID)).thenReturn(null);

        var entered = service.timerEvent(USER_ID, SESSION_ID, "timer-enter", timer("enter", 1, null));

        assertThat(entered.eventAccepted()).isTrue();
        assertThat(entered.timerState()).isEqualTo("RUNNING");
        verify(mapper).activateTimer(501L);

        when(mapper.selectDiagnosticIdempotency("timer-old")).thenReturn(null, idempotency(4L));
        DiagnosticTimerLeaseRow active = lease("different", 2, 502L, OffsetDateTime.now());
        when(mapper.lockTimerLease(SESSION_ID)).thenReturn(active);
        var replaced = service.timerEvent(USER_ID, SESSION_ID, "timer-old", timer("heartbeat", 1, "old"));

        assertThat(replaced.eventAccepted()).isFalse();
        assertThat(replaced.timerState()).isEqualTo("REPLACED");
    }

    @Test
    void pauseFinishAndRegenerateExposeStateConflictsAndSuccessfulTransitions() {
        DiagnosticSessionRow session = session("in_progress", 3L, 2);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session);
        when(mapper.selectSessionItem(USER_ID, SESSION_ID, 2)).thenReturn(item(2, "{\"value\":null}"));
        when(mapper.selectDiagnosticIdempotency("pause-ok")).thenReturn(null, idempotency(5L));
        when(mapper.updateSessionPosition(SESSION_ID, 3L, 2)).thenReturn(1);
        when(mapper.countAnswers(USER_ID, SESSION_ID)).thenReturn(count(4, 5));
        when(mapper.selectSessionItems(USER_ID, SESSION_ID)).thenReturn(List.of());
        when(mapper.selectTiming(USER_ID, SESSION_ID)).thenReturn(null);

        var paused = service.pause(USER_ID, SESSION_ID, "pause-ok", pause(3L, 2));
        assertThat(paused.answeredCount()).isEqualTo(4);

        when(mapper.selectDiagnosticIdempotency("finish-ok")).thenReturn(null, idempotency(6L));
        when(mapper.submitSession(SESSION_ID, 3L)).thenReturn(1);
        var finished = service.finish(USER_ID, SESSION_ID, "finish-ok", finish(3L));
        assertThat(finished.nextAction()).isEqualTo("WAIT_PROCESSING");
        verify(mapper).submitAttempts(SESSION_ID);

        DiagnosticJobRow failed = new DiagnosticJobRow();
        failed.setStatus("failed");
        failed.setPayload("{\"stage\":\"SCORING\"}");
        when(mapper.selectJob("DIAGNOSTIC:" + SESSION_ID)).thenReturn(failed);
        when(mapper.selectDiagnosticIdempotency("retry-ok")).thenReturn(null, idempotency(7L));
        var retried = service.regenerate(USER_ID, SESSION_ID, "retry-ok");
        assertThat(retried.diagnosticStatus()).isEqualTo("IN_PROGRESS");
        verify(mapper).retryJob("DIAGNOSTIC:" + SESSION_ID);
    }

    @Test
    void timerHeartbeatStopsExpiredLeasesAndHiddenEventsCloseValidLeases() {
        DiagnosticSessionRow session = session("in_progress", 3L, 1);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session);
        when(mapper.selectTiming(USER_ID, SESSION_ID)).thenReturn(null);
        when(mapper.selectDiagnosticIdempotency("timer-expired")).thenReturn(null, idempotency(8L));
        when(mapper.lockTimerLease(SESSION_ID)).thenReturn(
            lease("lease-1", 1, 501L, OffsetDateTime.now().minusSeconds(21))
        );

        var expired = service.timerEvent(USER_ID, SESSION_ID, "timer-expired", timer("heartbeat", 1, "lease-1"));

        assertThat(expired.timerState()).isEqualTo("STOPPED");
        verify(mapper).markTimerInvalid(501L, "计时心跳超过20秒未确认");
        verify(mapper).accumulateTimerInterval(501L, 0);

        when(mapper.selectDiagnosticIdempotency("timer-hidden")).thenReturn(null, idempotency(9L));
        when(mapper.lockTimerLease(SESSION_ID)).thenReturn(
            lease("lease-2", 1, 502L, OffsetDateTime.now())
        );
        var hidden = service.timerEvent(USER_ID, SESSION_ID, "timer-hidden", timer("hidden", 1, "lease-2"));

        assertThat(hidden.timerState()).isEqualTo("STOPPED");
        verify(mapper).accumulateTimerInterval(502L, 0);
        verify(mapper, org.mockito.Mockito.times(2)).deleteTimerLease(SESSION_ID);
    }

    @Test
    void finishAndRegenerateRejectInvalidStateAndIdempotencyConflicts() {
        DiagnosticSessionRow submitted = session("submitted", 3L, 2);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(submitted);
        when(mapper.selectDiagnosticIdempotency("finish-submitted")).thenReturn(null);
        assertDiagnosticError(() -> service.finish(USER_ID, SESSION_ID, "finish-submitted", finish(3L)), 409,
            "DIAGNOSTIC_STATE_CONFLICT");

        DiagnosticSessionRow running = session("in_progress", 3L, 2);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(running);
        DiagnosticIdempotencyRow conflict = new DiagnosticIdempotencyRow();
        conflict.setPayloadHash("different-payload");
        conflict.setStatus("succeeded");
        when(mapper.selectDiagnosticIdempotency("retry-conflict")).thenReturn(conflict);
        DiagnosticJobRow failed = new DiagnosticJobRow();
        failed.setStatus("failed");
        when(mapper.selectJob("DIAGNOSTIC:" + SESSION_ID)).thenReturn(failed);
        assertDiagnosticError(() -> service.regenerate(USER_ID, SESSION_ID, "retry-conflict"), 409,
            "DIAGNOSTIC_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void startRejectsInvalidQuestionSetsAndMalformedRequestIdentifiers() {
        DiagnosticGoalRow goal = goal(3L);
        DiagnosticRevisionRow revision = revision(50L);
        when(mapper.selectActiveGoal(USER_ID)).thenReturn(goal);
        when(mapper.selectLatestSession(USER_ID, goal.getId())).thenReturn(null);
        when(mapper.selectAvailableRevision(goal.getCertificationId(), goal.getSyllabusVersionId())).thenReturn(revision);
        when(collections.isFirstDiagnosticReady(revision.getId())).thenReturn(true);
        when(mapper.selectDiagnosticIdempotency("bad-items")).thenReturn(null);
        DiagnosticItemRow invalid = item(1, "{}");
        invalid.setQuestionType("UNSUPPORTED");
        invalid.setEstimatedSecondsSnapshot(60);
        invalid.setKnowledgePointId(1L);
        when(mapper.selectRevisionItems(revision.getId())).thenReturn(java.util.Collections.nCopies(50, invalid));

        assertDiagnosticError(() -> service.start(USER_ID, "bad-items", start("50", 3L)), 422,
            "FIRST_DIAGNOSTIC_NOT_READY");
        assertDiagnosticError(() -> service.start(USER_ID, " ", start("50", 3L)), 400,
            "DIAGNOSTIC_REQUEST_INVALID");
    }

    @Test
    void statusMapsCreatedAndFailedJobsToTheLearnerFacingStateMachine() {
        DiagnosticSessionRow created = session("created", 1L, 1);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(created);
        when(mapper.selectJob("DIAGNOSTIC:" + SESSION_ID)).thenReturn(null);

        assertThat(service.status(USER_ID, SESSION_ID).diagnosticStatus()).isEqualTo("IN_PROGRESS");

        DiagnosticSessionRow submitted = session("submitted", 1L, 1);
        DiagnosticJobRow failed = new DiagnosticJobRow();
        failed.setStatus("failed");
        failed.setPayload("{\"stage\":\"SCORING\"}");
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(submitted);
        when(mapper.selectJob("DIAGNOSTIC:" + SESSION_ID)).thenReturn(failed);

        var result = service.status(USER_ID, SESSION_ID);
        assertThat(result.diagnosticStatus()).isEqualTo("FAILED");
        assertThat(result.failure()).isNotNull();
        assertThat(result.nextAction()).isEqualTo("RETRY_DIAGNOSTIC_RESULT");
    }

    @Test
    void reportAndStatusExposeProcessingFailureAndCompletedArtifacts() {
        DiagnosticSessionRow session = session("submitted", 3L, 50);
        when(mapper.lockSession(USER_ID, SESSION_ID)).thenReturn(session);
        DiagnosticJobRow processing = new DiagnosticJobRow();
        processing.setStatus("queued");
        processing.setPayload("broken");
        when(mapper.selectJob("DIAGNOSTIC:" + SESSION_ID)).thenReturn(processing);
        when(mapper.selectReport(USER_ID, SESSION_ID)).thenReturn(null);
        when(mapper.countSessionProfileEvidence(SESSION_ID)).thenReturn(0);

        assertDiagnosticError(() -> service.report(USER_ID, SESSION_ID), 409, "DIAGNOSTIC_RESULT_PROCESSING");
        assertThat(service.status(USER_ID, SESSION_ID).stages()).first()
            .satisfies(stage -> assertThat(stage.state()).isEqualTo("RUNNING"));

        DiagnosticReportRow report = new DiagnosticReportRow();
        report.setStatus("available");
        report.setSubjectScores("{\"subjects\":[]}");
        report.setProfileSummary("{\"dataStatus\":\"INITIAL_PROFILE\"}");
        when(mapper.selectReport(USER_ID, SESSION_ID)).thenReturn(report);
        when(mapper.countSessionProfileEvidence(SESSION_ID)).thenReturn(1);

        var completed = service.report(USER_ID, SESSION_ID);
        assertThat(completed.nextAction()).isEqualTo("VIEW_DIAGNOSTIC_REPORT");
        assertThat(completed.report().path("profileSummary").path("dataStatus").asText()).isEqualTo("INITIAL_PROFILE");
    }

    private static DiagnosticGoalRow goal(long version) {
        DiagnosticGoalRow goal = new DiagnosticGoalRow();
        goal.setId(20L);
        goal.setCertificationId(30L);
        goal.setCertificationName("系统架构设计师");
        goal.setSyllabusVersionId(40L);
        goal.setSyllabusVersionName("2025版");
        goal.setRowVersion(version);
        return goal;
    }

    private static DiagnosticRevisionRow revision(long id) {
        DiagnosticRevisionRow revision = new DiagnosticRevisionRow();
        revision.setId(id);
        revision.setQuestionCount(50);
        revision.setDurationMinutes(50);
        return revision;
    }

    private static DiagnosticSessionRow session(String status, long version, int currentOrder) {
        DiagnosticSessionRow session = new DiagnosticSessionRow();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setGoalId(20L);
        session.setStatus(status);
        session.setRowVersion(version);
        session.setLastQuestionOrder(currentOrder);
        return session;
    }

    private static DiagnosticItemRow item(int order, String answer) {
        DiagnosticItemRow item = new DiagnosticItemRow();
        item.setQuestionOrder(order);
        item.setAnswerData(answer);
        item.setPresentationSnapshot("{\"questionType\":\"CHOICE\"}");
        return item;
    }

    private static DiagnosticCountRow count(int answered, Integer totalCount) {
        DiagnosticCountRow count = new DiagnosticCountRow();
        count.setAnsweredCount(answered);
        count.setTotalCount(totalCount == null ? answered : totalCount);
        return count;
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

    private static DiagnosticIdempotencyRow idempotency(long id) {
        DiagnosticIdempotencyRow record = new DiagnosticIdempotencyRow();
        record.setId(id);
        return record;
    }

    private static DiagnosticStartBo start(String revisionId, long version) {
        DiagnosticStartBo command = new DiagnosticStartBo();
        command.setDiagnosticRevisionId(revisionId);
        command.setGoalVersion(version);
        return command;
    }

    private DiagnosticDraftBo draft(long version, String value) {
        DiagnosticDraftBo command = new DiagnosticDraftBo();
        command.setExpectedSessionVersion(version);
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

    private static DiagnosticPauseBo pause(long version, int order) {
        DiagnosticPauseBo command = new DiagnosticPauseBo();
        command.setExpectedSessionVersion(version);
        command.setCurrentQuestionOrder(order);
        return command;
    }

    private static DiagnosticFinishBo finish(long version) {
        DiagnosticFinishBo command = new DiagnosticFinishBo();
        command.setExpectedSessionVersion(version);
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
