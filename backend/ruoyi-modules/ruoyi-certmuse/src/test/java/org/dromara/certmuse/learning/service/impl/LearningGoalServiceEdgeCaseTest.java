package org.dromara.certmuse.learning.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.certmuse.learning.domain.LearningGoalCertificationRow;
import org.dromara.certmuse.learning.domain.LearningGoalIdempotencyRow;
import org.dromara.certmuse.learning.domain.LearningGoalRow;
import org.dromara.certmuse.learning.domain.bo.CreateLearningGoalBo;
import org.dromara.certmuse.learning.mapper.LearningGoalMapper;
import org.dromara.certmuse.learning.support.LearningGoalException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class LearningGoalServiceEdgeCaseTest {
    private static final Clock AUGUST = Clock.fixed(Instant.parse("2026-08-12T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final String REQUEST_ID = "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd";
    private static final String RESPONSE = "{\"schema_version\":\"learning_goal_response/1.0\",\"data\":{\"goal\":{\"id\":\"10\",\"certificationId\":\"9\",\"certificationName\":\"系统架构设计师\",\"syllabusVersionId\":\"8\",\"syllabusVersionName\":\"第二版\",\"targetExamYear\":2026,\"targetExamMonth\":11,\"dailyMinutes\":30,\"status\":\"ACTIVE\",\"version\":0},\"nextAction\":\"START_DIAGNOSTIC\"}}";

    @Test
    void optionsDescribeAnEmptyCatalogueWithoutInventingDefaults() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        when(mapper.selectCurrentGoalId(1L)).thenReturn(null);
        when(mapper.selectSelectableCertifications(any())).thenReturn(List.of());

        var result = service(mapper).options(1L);

        assertThat(result.certifications()).isEmpty();
        assertThat(result.defaults()).isNull();
        assertThat(result.dailyMinutes().min()).isEqualTo(15);
        assertThat(result.dailyMinutes().max()).isEqualTo(300);
        assertThat(result.dailyMinutes().defaultValue()).isEqualTo(30);
    }

    @Test
    void optionsSkipAnExpiredCurrentYearWhenChoosingDefaults() {
        Clock december = Clock.fixed(Instant.parse("2026-12-20T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        when(mapper.selectCurrentGoalId(1L)).thenReturn(null);
        when(mapper.selectSelectableCertifications(any())).thenReturn(List.of(certification()));

        var result = new LearningGoalServiceImpl(mapper, json(), december).options(1L);

        assertThat(result.examYears().getFirst().months()).isEmpty();
        assertThat(result.examYears().getFirst().selectable()).isFalse();
        assertThat(result.defaults().examYear()).isEqualTo(2027);
        assertThat(result.defaults().examMonth()).isEqualTo(5);
    }

    @Test
    void createRejectsMissingMalformedAndIncompleteCommandsBeforePersistence() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);

        assertFailure(() -> service(mapper).create(1L, REQUEST_ID, null), "GOAL_REQUEST_INVALID");
        CreateLearningGoalBo malformedId = command(2026, 11, 30);
        malformedId.setCertificationId("not-an-id");
        assertFailure(() -> service(mapper).create(1L, REQUEST_ID, malformedId), "GOAL_REQUEST_INVALID");
        CreateLearningGoalBo nonPositiveId = command(2026, 11, 30);
        nonPositiveId.setCertificationId("0");
        assertFailure(() -> service(mapper).create(1L, REQUEST_ID, nonPositiveId), "GOAL_REQUEST_INVALID");
        CreateLearningGoalBo missingYear = command(2026, 11, 30);
        missingYear.setTargetExamYear(null);
        assertFailure(() -> service(mapper).create(1L, REQUEST_ID, missingYear), "GOAL_REQUEST_INVALID");
        CreateLearningGoalBo missingMonth = command(2026, 11, 30);
        missingMonth.setTargetExamMonth(null);
        assertFailure(() -> service(mapper).create(1L, REQUEST_ID, missingMonth), "GOAL_REQUEST_INVALID");
        CreateLearningGoalBo missingMinutes = command(2026, 11, 30);
        missingMinutes.setDailyMinutes(null);
        assertFailure(() -> service(mapper).create(1L, REQUEST_ID, missingMinutes), "GOAL_REQUEST_INVALID");

        verify(mapper, never()).insertIdempotency(anyLong(), anyString(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void createRequiresAUuidRequestId() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);

        assertFailure(() -> service(mapper).create(1L, null, command(2026, 11, 30)), "GOAL_REQUEST_INVALID");
        assertFailure(() -> service(mapper).create(1L, "not-a-uuid", command(2026, 11, 30)), "GOAL_REQUEST_INVALID");
    }

    @Test
    void createRejectsEveryOutOfWindowExamBatch() {
        for (CreateLearningGoalBo invalid : List.of(
            command(2026, 6, 30),
            command(2025, 11, 30),
            command(2031, 5, 30),
            command(2026, 5, 30)
        )) {
            assertFailure(() -> service(mock(LearningGoalMapper.class)).create(1L, REQUEST_ID, invalid),
                "TARGET_EXAM_BATCH_INVALID");
        }
    }

    @Test
    void createAcceptsBoundaryStudyDurationsAndRejectsValuesOutsideThem() {
        assertFailure(() -> service(mock(LearningGoalMapper.class)).create(1L, REQUEST_ID, command(2026, 11, 14)),
            "DAILY_MINUTES_OUT_OF_RANGE");
        assertFailure(() -> service(mock(LearningGoalMapper.class)).create(1L, REQUEST_ID, command(2026, 11, 301)),
            "DAILY_MINUTES_OUT_OF_RANGE");

        for (int minutes : List.of(15, 300)) {
            LearningGoalMapper mapper = happyMapper();
            when(mapper.selectGoal(anyLong())).thenAnswer(invocation -> goal(invocation.getArgument(0), minutes));
            assertThat(service(mapper).create(1L, REQUEST_ID, command(2026, 11, minutes)).goal().dailyMinutes())
                .isEqualTo(minutes);
        }
    }

    @Test
    void createDistinguishesUnavailableQualificationsFromMissingSyllabuses() {
        LearningGoalMapper unavailable = mock(LearningGoalMapper.class);
        when(unavailable.selectCertificationStatus(9L)).thenReturn("1");
        LearningGoalException disabled = failure(() -> service(unavailable).create(1L, REQUEST_ID, command(2026, 11, 30)));
        assertThat(disabled.data().errorCode()).isEqualTo("CERTIFICATION_UNAVAILABLE");
        assertThat(disabled.data().fieldErrors()).extracting("field", "code")
            .containsExactly(org.assertj.core.groups.Tuple.tuple("certificationId", "UNSUPPORTED"));

        LearningGoalMapper missing = mock(LearningGoalMapper.class);
        when(missing.selectCertificationStatus(9L)).thenReturn(null);
        assertFailure(() -> service(missing).create(1L, REQUEST_ID, command(2026, 11, 30)),
            "CERTIFICATION_UNAVAILABLE");

        LearningGoalMapper noSyllabus = mock(LearningGoalMapper.class);
        when(noSyllabus.selectCertificationStatus(9L)).thenReturn("0");
        LearningGoalException syllabus = failure(() -> service(noSyllabus).create(1L, REQUEST_ID, command(2026, 11, 30)));
        assertThat(syllabus.data().errorCode()).isEqualTo("SYLLABUS_VERSION_NOT_READY");
        assertThat(syllabus.data().retryable()).isTrue();
    }

    @Test
    void createReplaysACompletedRequestAndAConcurrentWinnerForTheSameOwner() {
        String hash = payloadHash(30);
        LearningGoalIdempotencyRow completed = idem(hash, "succeeded", 10L, RESPONSE, 1L);
        LearningGoalMapper existing = mock(LearningGoalMapper.class);
        when(existing.selectIdempotency(anyString(), anyString())).thenReturn(completed);
        assertThat(service(existing).create(1L, REQUEST_ID, command(2026, 11, 30)).goal().id()).isEqualTo("10");
        verify(existing, never()).insertIdempotency(anyLong(), anyString(), anyString(), anyString(), anyLong(), any());

        LearningGoalMapper concurrent = mock(LearningGoalMapper.class);
        when(concurrent.selectIdempotency(anyString(), anyString())).thenReturn(null, completed);
        when(concurrent.selectEnabledCertificationWithSyllabus(anyLong(), any())).thenReturn(certification());
        when(concurrent.insertIdempotency(anyLong(), anyString(), anyString(), anyString(), anyLong(), any())).thenReturn(0);
        assertThat(service(concurrent).create(1L, REQUEST_ID, command(2026, 11, 30)).goal().id()).isEqualTo("10");
        verify(concurrent, never()).insertGoal(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyString(), any());
    }

    @Test
    void createRejectsEveryUnsafeIdempotencyReplayShape() {
        String hash = payloadHash(30);
        List<LearningGoalIdempotencyRow> unsafe = List.of(
            idem("different", "succeeded", 10L, RESPONSE, 1L),
            idem(hash, "processing", 10L, RESPONSE, 1L),
            idem(hash, "succeeded", null, RESPONSE, 1L),
            idem(hash, "succeeded", 10L, RESPONSE, null),
            idem(hash, "succeeded", 10L, RESPONSE, 2L)
        );
        for (LearningGoalIdempotencyRow record : unsafe) {
            LearningGoalMapper mapper = mock(LearningGoalMapper.class);
            when(mapper.selectIdempotency(anyString(), anyString())).thenReturn(record);
            assertFailure(() -> service(mapper).create(1L, REQUEST_ID, command(2026, 11, 30)),
                "GOAL_IDEMPOTENCY_CONFLICT");
        }
    }

    @Test
    void createRedactsCorruptReplayDataAndAConcurrentInsertWithoutAWinner() {
        LearningGoalMapper corrupt = mock(LearningGoalMapper.class);
        when(corrupt.selectIdempotency(anyString(), anyString())).thenReturn(
            idem(payloadHash(30), "succeeded", 10L, "not-json", 1L));
        assertFailure(() -> service(corrupt).create(1L, REQUEST_ID, command(2026, 11, 30)),
            "LEARNING_GOAL_CREATE_FAILED");

        LearningGoalMapper lost = mock(LearningGoalMapper.class);
        when(lost.selectIdempotency(anyString(), anyString())).thenReturn(null);
        when(lost.selectEnabledCertificationWithSyllabus(anyLong(), any())).thenReturn(certification());
        when(lost.insertIdempotency(anyLong(), anyString(), anyString(), anyString(), anyLong(), any())).thenReturn(0);
        assertFailure(() -> service(lost).create(1L, REQUEST_ID, command(2026, 11, 30)),
            "LEARNING_GOAL_CREATE_FAILED");
    }

    @Test
    void createRejectsAnExistingGoalBeforeWritingTheNewAggregate() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        when(mapper.selectEnabledCertificationWithSyllabus(anyLong(), any())).thenReturn(certification());
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString(), anyLong(), any())).thenReturn(1);
        when(mapper.lockCurrentGoalId(1L)).thenReturn(99L);

        assertFailure(() -> service(mapper).create(1L, REQUEST_ID, command(2026, 11, 30)), "GOAL_ALREADY_EXISTS");
        verify(mapper, never()).insertGoal(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyString(), any());
    }

    @Test
    void createReportsEveryFailedPersistenceBoundary() {
        LearningGoalMapper insertFailure = preparedMapper();
        when(insertFailure.insertGoal(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyString(), any())).thenReturn(0);
        assertFailure(() -> service(insertFailure).create(1L, REQUEST_ID, command(2026, 11, 30)), "LEARNING_GOAL_CREATE_FAILED");

        LearningGoalMapper duplicate = preparedMapper();
        when(duplicate.insertGoal(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyString(), any()))
            .thenThrow(new DataIntegrityViolationException("duplicate"));
        assertFailure(() -> service(duplicate).create(1L, REQUEST_ID, command(2026, 11, 30)), "GOAL_ALREADY_EXISTS");

        for (LearningGoalRow unreadable : java.util.Arrays.asList(
            null,
            new LearningGoalRow(10L, 9L, "资格", 8L, "版本", null, 11, 30, "active", 0L),
            new LearningGoalRow(10L, 9L, "资格", 8L, "版本", 2026, null, 30, "active", 0L)
        )) {
            LearningGoalMapper mapper = preparedMapper();
            when(mapper.insertGoal(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyString(), any())).thenReturn(1);
            when(mapper.selectGoal(anyLong())).thenReturn(unreadable);
            assertFailure(() -> service(mapper).create(1L, REQUEST_ID, command(2026, 11, 30)),
                "LEARNING_GOAL_CREATE_FAILED");
        }

        LearningGoalMapper changeFailure = preparedMapper();
        successfulGoalWrite(changeFailure);
        when(changeFailure.insertGoalChange(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong())).thenReturn(0);
        assertFailure(() -> service(changeFailure).create(1L, REQUEST_ID, command(2026, 11, 30)), "LEARNING_GOAL_CREATE_FAILED");

        LearningGoalMapper completionFailure = preparedMapper();
        successfulGoalWrite(completionFailure);
        when(completionFailure.insertGoalChange(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);
        when(completionFailure.completeIdempotency(anyLong(), anyString())).thenReturn(0);
        assertFailure(() -> service(completionFailure).create(1L, REQUEST_ID, command(2026, 11, 30)), "LEARNING_GOAL_CREATE_FAILED");
    }

    @Test
    void createRedactsJsonSerialisationFailures() throws Exception {
        JsonMapper broken = mock(JsonMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new IllegalStateException("codec detail"));

        assertFailure(() -> new LearningGoalServiceImpl(mock(LearningGoalMapper.class), broken, AUGUST)
            .create(1L, REQUEST_ID, command(2026, 11, 30)), "LEARNING_GOAL_CREATE_FAILED");
    }

    private static LearningGoalMapper happyMapper() {
        LearningGoalMapper mapper = preparedMapper();
        successfulGoalWrite(mapper);
        when(mapper.insertGoalChange(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);
        when(mapper.completeIdempotency(anyLong(), anyString())).thenReturn(1);
        return mapper;
    }

    private static LearningGoalMapper preparedMapper() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        when(mapper.selectEnabledCertificationWithSyllabus(anyLong(), any())).thenReturn(certification());
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString(), anyLong(), any())).thenReturn(1);
        when(mapper.lockCurrentGoalId(1L)).thenReturn(null);
        return mapper;
    }

    private static void successfulGoalWrite(LearningGoalMapper mapper) {
        when(mapper.insertGoal(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyString(), any())).thenReturn(1);
        when(mapper.selectGoal(anyLong())).thenAnswer(invocation -> goal(invocation.getArgument(0), 30));
    }

    private static LearningGoalCertificationRow certification() {
        return new LearningGoalCertificationRow(9L, "SYSTEM_ARCHITECT", "系统架构设计师", 8L, "第二版");
    }

    private static LearningGoalRow goal(long id, int minutes) {
        return new LearningGoalRow(id, 9L, "系统架构设计师", 8L, "第二版", 2026, 11, minutes, "active", 0L);
    }

    private static CreateLearningGoalBo command(int year, int month, int minutes) {
        CreateLearningGoalBo command = new CreateLearningGoalBo();
        command.setCertificationId("9");
        command.setTargetExamYear(year);
        command.setTargetExamMonth(month);
        command.setDailyMinutes(minutes);
        return command;
    }

    private static LearningGoalIdempotencyRow idem(String hash, String status, Long resourceId, String response, Long owner) {
        return new LearningGoalIdempotencyRow(hash, status, resourceId, response, owner);
    }

    private static String payloadHash(int minutes) {
        return DigestUtil.sha256Hex("{\"certificationId\":9,\"targetExamYear\":2026,\"targetExamMonth\":11,\"dailyMinutes\":" + minutes + "}");
    }

    private static LearningGoalServiceImpl service(LearningGoalMapper mapper) {
        return new LearningGoalServiceImpl(mapper, json(), AUGUST);
    }

    private static JsonMapper json() {
        return JsonMapper.builder().build();
    }

    private static void assertFailure(ThrowingCall call, String code) {
        assertThat(failure(call).data().errorCode()).isEqualTo(code);
    }

    private static LearningGoalException failure(ThrowingCall call) {
        return (LearningGoalException) org.assertj.core.api.Assertions.catchThrowable(call::run);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
