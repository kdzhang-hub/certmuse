package org.dromara.certmuse.learning.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.dromara.certmuse.learning.domain.LearningGoalBatchRow;
import org.dromara.certmuse.learning.domain.LearningGoalCertificationRow;
import org.dromara.certmuse.learning.domain.LearningGoalIdempotencyRow;
import org.dromara.certmuse.learning.domain.LearningGoalRow;
import org.dromara.certmuse.learning.domain.bo.CreateLearningGoalBo;
import org.dromara.certmuse.learning.domain.bo.SwitchLearningGoalBo;
import org.dromara.certmuse.learning.domain.vo.LearningGoalOptionsVo;
import org.dromara.certmuse.learning.mapper.LearningGoalMapper;
import org.dromara.certmuse.learning.support.LearningGoalException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class LearningGoalServiceImplTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void optionsUsesOnlyLatestEffectiveSyllabusRowsFromMapper() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        when(mapper.selectCurrentGoalId(1L)).thenReturn(null);
        when(mapper.selectSelectableCertifications(any())).thenReturn(List.of(certification()));

        LearningGoalOptionsVo options = service(mapper).options(1L);

        assertEquals("Asia/Shanghai", options.timezone());
        assertEquals(List.of(11), options.examYears().getFirst().months());
        assertEquals("900000000000000001", options.defaults().certificationId());
        verify(mapper).selectSelectableCertifications(java.time.LocalDate.of(2026, 8, 12));
    }

    @Test
    void optionsRejectsLearnerWithCurrentGoal() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        when(mapper.selectCurrentGoalId(1L)).thenReturn(10L);

        LearningGoalException exception = assertThrows(LearningGoalException.class, () -> service(mapper).options(1L));

        assertEquals("GOAL_ALREADY_EXISTS", exception.data().errorCode());
        verify(mapper, never()).selectSelectableCertifications(any());
    }

    @Test
    void createRejectsPastBatchBeforeWritingIdempotencyRecord() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        when(mapper.selectCurrentGoalId(1L)).thenReturn(null);
        CreateLearningGoalBo command = command(2026, 5, 30);

        LearningGoalException exception = assertThrows(LearningGoalException.class,
            () -> service(mapper).create(1L, "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", command));

        assertEquals("TARGET_EXAM_BATCH_INVALID", exception.data().errorCode());
        verify(mapper, never()).insertIdempotency(anyLong(), anyString(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void createReturnsSyllabusNotReadyWithoutGoalWhenQualificationHasNoEffectiveVersion() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        when(mapper.selectCurrentGoalId(1L)).thenReturn(null);
        when(mapper.selectEnabledCertificationWithSyllabus(9L, java.time.LocalDate.of(2026, 8, 12))).thenReturn(null);
        when(mapper.selectCertificationStatus(9L)).thenReturn("0");

        LearningGoalException exception = assertThrows(LearningGoalException.class,
            () -> service(mapper).create(1L, "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", command(2026, 11, 30)));

        assertEquals("SYLLABUS_VERSION_NOT_READY", exception.data().errorCode());
        verify(mapper, never()).insertGoal(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyString(), any());
    }

    @Test
    void createsGoalChangeAndIdempotencyResultWithoutCreatingDiagnosticSession() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        when(mapper.selectIdempotency(anyString(), anyString())).thenReturn(null);
        when(mapper.selectEnabledCertificationWithSyllabus(9L, java.time.LocalDate.of(2026, 8, 12))).thenReturn(certification());
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString(), anyLong(), any())).thenReturn(1);
        when(mapper.lockCurrentGoalId(1L)).thenReturn(null);
        when(mapper.insertGoal(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyString(), any())).thenReturn(1);
        when(mapper.selectGoal(anyLong())).thenReturn(new LearningGoalRow(
            10L, 9L, "系统架构设计师", 8L, "第二版", 2026, 11, 30, "active", 0L
        ));
        when(mapper.insertGoalChange(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);
        when(mapper.completeIdempotency(anyLong(), anyString())).thenReturn(1);

        var result = service(mapper).create(1L, "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", command(2026, 11, 30));

        assertEquals("START_DIAGNOSTIC", result.nextAction());
        assertEquals("10", result.goal().id());
        assertEquals("ACTIVE", result.goal().status());
        verify(mapper).insertGoalChange(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong());
        verify(mapper).completeIdempotency(anyLong(), anyString());
    }

    @Test
    void switchStoresVersionedIdempotencyResponse() throws Exception {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        LocalDate today = LocalDate.of(2026, 8, 12);
        LearningGoalRow oldGoal = goal(10L, 1L, "系统架构设计师", "active");
        LearningGoalRow pausedGoal = goal(10L, 1L, "系统架构设计师", "paused");
        LearningGoalRow newGoal = goal(11L, 2L, "系统分析师", "active");
        when(mapper.selectIdempotency("GOAL_SWITCH", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")).thenReturn(null);
        when(mapper.lockActiveGoal(1L)).thenReturn(oldGoal);
        when(mapper.selectCertificationStatus(1L)).thenReturn("0");
        when(mapper.selectEnabledCertificationWithSyllabus(2L, today)).thenReturn(
            new LearningGoalCertificationRow(2L, "SYSTEM_ANALYST", "系统分析师", 9L, "第一版"));
        when(mapper.selectBatchSnapshot(2L, 2026, 11, today)).thenReturn(new LearningGoalBatchRow("estimated", null));
        when(mapper.lockPendingSessions(10L)).thenReturn(List.of());
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString(), anyLong(), any())).thenReturn(1);
        when(mapper.cancelPendingSessions(10L, 1L)).thenReturn(0);
        when(mapper.pauseGoal(10L, 0L)).thenReturn(1);
        when(mapper.selectGoal(anyLong())).thenReturn(pausedGoal, newGoal);
        when(mapper.insertGoalSwitchChange(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any(), anyLong()))
            .thenReturn(1);
        when(mapper.insertGoal(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyString(), any())).thenReturn(1);
        org.mockito.ArgumentCaptor<String> response = org.mockito.ArgumentCaptor.forClass(String.class);
        when(mapper.completeIdempotency(anyLong(), response.capture())).thenReturn(1);

        service(mapper).switchGoal(1L, "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", switchCommand());

        assertEquals("learning_goal_response/1.0",
            JsonMapper.builder().build().readTree(response.getValue()).path("schema_version").asText());
    }

    @Test
    void replaysSucceededRequestForTheSameOwner() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        when(mapper.selectCurrentGoalId(1L)).thenReturn(null);
        String response = "{\"schema_version\":\"learning_goal_response/1.0\",\"data\":{\"goal\":{\"id\":\"10\",\"certificationId\":\"9\",\"certificationName\":\"系统架构设计师\",\"syllabusVersionId\":\"8\",\"syllabusVersionName\":\"第二版\",\"targetExamYear\":2026,\"targetExamMonth\":11,\"dailyMinutes\":30,\"status\":\"ACTIVE\",\"version\":0},\"nextAction\":\"START_DIAGNOSTIC\"}}";
        when(mapper.selectIdempotency(anyString(), anyString())).thenReturn(
            new LearningGoalIdempotencyRow(
                "d0b27b881ea875ea3ef34e4d50e02851520570033da9df2d79d6c3c13a0c0c16", "succeeded", 10L, response, 1L
            )
        );

        // The exact payload hash is intentionally generated by the service; a mismatched value must be rejected.
        LearningGoalException exception = assertThrows(LearningGoalException.class,
            () -> service(mapper).create(1L, "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", command(2026, 11, 30)));

        assertEquals("GOAL_IDEMPOTENCY_CONFLICT", exception.data().errorCode());
        verify(mapper, never()).insertGoal(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyString(), any());
    }

    @Test
    void preservesCauseWhenStoredIdempotencyResponseIsInvalid() {
        LearningGoalMapper mapper = mock(LearningGoalMapper.class);
        CreateLearningGoalBo command = command(2026, 11, 30);
        LearningGoalServiceImpl service = service(mapper);
        String requestId = "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd";

        // Capture a valid payload hash from the first attempted insert, then replay a damaged response.
        org.mockito.ArgumentCaptor<String> hash = org.mockito.ArgumentCaptor.forClass(String.class);
        when(mapper.selectIdempotency(anyString(), anyString())).thenReturn(null);
        when(mapper.selectEnabledCertificationWithSyllabus(anyLong(), any())).thenReturn(certification());
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), hash.capture(), anyLong(), any()))
            .thenReturn(0);
        assertThrows(LearningGoalException.class, () -> service.create(1L, requestId, command));

        LearningGoalIdempotencyRow damaged = new LearningGoalIdempotencyRow(
            hash.getValue(), "succeeded", 10L, "{", 1L);
        when(mapper.selectIdempotency(anyString(), anyString())).thenReturn(damaged);

        LearningGoalException exception = assertThrows(
            LearningGoalException.class, () -> service.create(1L, requestId, command));

        assertEquals("LEARNING_GOAL_CREATE_FAILED", exception.data().errorCode());
        assertEquals(500, exception.status());
        org.junit.jupiter.api.Assertions.assertNotNull(exception.getCause());
    }

    private LearningGoalServiceImpl service(LearningGoalMapper mapper) {
        return new LearningGoalServiceImpl(mapper, JsonMapper.builder().build(), CLOCK);
    }

    private LearningGoalCertificationRow certification() {
        return new LearningGoalCertificationRow(900000000000000001L, "SYSTEM_ARCHITECT", "系统架构设计师", 8L, "第二版");
    }

    private CreateLearningGoalBo command(int year, int month, int minutes) {
        CreateLearningGoalBo command = new CreateLearningGoalBo();
        command.setCertificationId("9");
        command.setTargetExamYear(year);
        command.setTargetExamMonth(month);
        command.setDailyMinutes(minutes);
        return command;
    }

    private LearningGoalRow goal(long id, long certificationId, String certificationName, String status) {
        return new LearningGoalRow(id, certificationId, certificationName, 8L, "第一版", 2026, 11, 30, status, 0L,
            "estimated", null);
    }

    private SwitchLearningGoalBo switchCommand() {
        SwitchLearningGoalBo command = new SwitchLearningGoalBo();
        command.setCertificationId("2");
        command.setTargetExamYear(2026);
        command.setTargetExamMonth(11);
        command.setExpectedCurrentGoalVersion(0L);
        command.setConfirmAbandonInProgress(true);
        return command;
    }
}
