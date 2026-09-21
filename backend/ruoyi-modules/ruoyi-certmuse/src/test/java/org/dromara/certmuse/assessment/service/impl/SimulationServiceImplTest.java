package org.dromara.certmuse.assessment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.dromara.certmuse.assessment.domain.SimulationGoalRow;
import org.dromara.certmuse.assessment.domain.SimulationOptionRow;
import org.dromara.certmuse.assessment.domain.SimulationPaperRow;
import org.dromara.certmuse.assessment.domain.SimulationPreviewQuestionRow;
import org.dromara.certmuse.assessment.domain.bo.SimulationQueryBo;
import org.dromara.certmuse.assessment.domain.bo.StartSimulationSessionBo;
import org.dromara.certmuse.assessment.mapper.SimulationMapper;
import org.dromara.certmuse.assessment.support.SimulationException;
import org.dromara.certmuse.assessment.service.FormalExamEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("dev")
class SimulationServiceImplTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-18T02:00:00Z"),
        ZoneId.of("Asia/Shanghai"));

    private final SimulationMapper mapper = mock(SimulationMapper.class);
    private final FormalExamEngine formalExamEngine = mock(FormalExamEngine.class);
    private final SimulationServiceImpl service = new SimulationServiceImpl(mapper, CLOCK, formalExamEngine);

    @Test
    void productionConstructorIsExplicitlySelectedForSpringInjection() {
        assertThat(Arrays.stream(SimulationServiceImpl.class.getDeclaredConstructors())
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class)))
            .singleElement()
            .satisfies(constructor -> assertThat(constructor.getParameterCount()).isEqualTo(3));
    }

    @Test
    void setupReturnsNoGoalAndOnlyMapperSuppliedPublishedOptions() {
        SimulationOptionRow first = option(9L, "系统架构设计师", 21L, "第二版");
        SimulationOptionRow second = option(9L, "系统架构设计师", 20L, "第一版");
        when(mapper.selectSetupOptions()).thenReturn(List.of(first, second));

        var result = service.setup(42L);

        assertThat(result.serverTime().toString()).isEqualTo("2026-08-18T10:00+08:00");
        assertThat(result.timezone()).isEqualTo("Asia/Shanghai");
        assertThat(result.currentGoal()).isNull();
        assertThat(result.certifications()).singleElement().satisfies(certification -> {
            assertThat(certification.id()).isEqualTo("9");
            assertThat(certification.syllabusVersions()).extracting("id")
                .containsExactly("21", "20");
        });
        verify(mapper).selectCurrentGoal(42L);
        verify(mapper).selectSetupOptions();
    }

    @Test
    void setupMapsTheActiveGoalWithoutChangingThePublishedOptionOrder() {
        SimulationGoalRow goal = new SimulationGoalRow();
        goal.setId(88L);
        goal.setCertificationId(9L);
        goal.setCertificationName("系统架构设计师");
        goal.setSyllabusVersionId(21L);
        goal.setSyllabusVersionName("第二版");
        goal.setRowVersion(3L);
        when(mapper.selectCurrentGoal(42L)).thenReturn(goal);
        when(mapper.selectSetupOptions()).thenReturn(List.of());

        var result = service.setup(42L);

        assertThat(result.currentGoal()).extracting("id", "certificationId", "syllabusVersionId", "version")
            .containsExactly("88", "9", "21", 3L);
    }

    @Test
    void listUsesActualItemAggregatesAndBlocksOnlyInvalidPublishedPapers() {
        SimulationPaperRow row = paper();
        row.setQuestionCount(999);
        row.setTotalReportScore(new BigDecimal("999.00"));
        row.setActualQuestionCount(75);
        row.setActualTotalReportScore(new BigDecimal("75.00"));
        row.setSubjectCount(2);
        row.setHasChoice(true);
        row.setHasCase(true);
        row.setHasEssay(true);
        when(mapper.countPublishedSimulations(9L, 21L, "模拟卷")).thenReturn(1L);
        when(mapper.selectPublishedSimulations(42L, 9L, 21L, "模拟卷", 10, 0L)).thenReturn(List.of(row));
        SimulationQueryBo query = query("9", "21", "  模拟卷  ", null, null);

        var result = service.list(42L, query);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRows()).singleElement().satisfies(item -> {
            assertThat(item.subject()).isNull();
            assertThat(item.questionTypes()).containsExactly("CHOICE", "CASE", "ESSAY");
            assertThat(item.questionCount()).isEqualTo(75);
            assertThat(item.totalReportScore()).isEqualByComparingTo("75.00");
            assertThat(item.access().action()).isEqualTo("BLOCKED");
            assertThat(item.access().canStart()).isFalse();
            assertThat(item.access().blockCode()).isEqualTo("SIMULATION_PAPER_UNAVAILABLE");
            assertThat(item.access().activeSessionId()).isNull();
            assertThat(item.access().activeAnswerPath()).isNull();
        });
        verify(mapper).countPublishedSimulations(9L, 21L, "模拟卷");
        verify(mapper).selectCurrentGoal(42L);
        verify(mapper).selectPublishedSimulations(42L, 9L, 21L, "模拟卷", 10, 0L);
    }

    @Test
    void listDoesNotLoadRowsForAnEmptyPageAndPassesStableOffset() {
        when(mapper.countPublishedSimulations(null, null, null)).thenReturn(0L);

        var empty = service.list(42L, query(null, null, "   ", 2, 50));

        assertThat(empty.getRows()).isEmpty();
        assertThat(empty.getTotal()).isZero();
        verify(mapper).countPublishedSimulations(null, null, null);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void detailUsesTheSameActualAggregatesAndNeverExposesQuestionContent() {
        SimulationPaperRow row = paper();
        row.setActualQuestionCount(1);
        row.setActualTotalReportScore(new BigDecimal("2.50"));
        row.setSubjectCount(1);
        row.setSubjectId(31L);
        row.setSubjectName("综合知识");
        row.setHasChoice(true);
        when(mapper.selectPublishedSimulation(42L, 11L)).thenReturn(row);
        when(mapper.selectCurrentGoal(42L)).thenReturn(goal());

        var result = service.detail(42L, "11");

        assertThat(result.subject()).extracting("id", "name").containsExactly("31", "综合知识");
        assertThat(result.questionTypes()).containsExactly("CHOICE");
        assertThat(result.questionCount()).isEqualTo(1);
        assertThat(result.totalReportScore()).isEqualByComparingTo("2.50");
        assertThat(result.examMode()).isEqualTo("standard");
        assertThat(result.rules()).hasSize(3);
        assertThat(result.access().action()).isEqualTo("START");
        assertThat(result.access().canStart()).isTrue();
        assertThat(result.access().blockCode()).isNull();
    }

    @Test
    void detailResumesTheCurrentGoalsActiveFormalSession() {
        SimulationPaperRow row = paper();
        row.setActualQuestionCount(75);
        row.setActualTotalReportScore(new BigDecimal("75"));
        row.setSubjectCount(1);
        row.setSubjectId(31L);
        row.setSubjectName("综合知识");
        row.setHasChoice(true);
        row.setActiveSessionId(501L);
        row.setActiveSessionStatus("settling");
        when(mapper.selectPublishedSimulation(42L, 11L)).thenReturn(row);
        when(mapper.selectCurrentGoal(42L)).thenReturn(goal());

        var result = service.detail(42L, "11");

        assertThat(result.access().action()).isEqualTo("RESUME");
        assertThat(result.access().activeSessionId()).isEqualTo("501");
        assertThat(result.access().activeAnswerPath()).isEqualTo(
            "/learning/question-bank/mock-exams/result?sessionId=501");
    }

    @Test
    void detailKeepsTheGoalScopeGate() {
        SimulationPaperRow row = paper();
        row.setActualQuestionCount(75);
        row.setActualTotalReportScore(new BigDecimal("75"));
        row.setSubjectCount(1);
        row.setSubjectId(31L);
        row.setSubjectName("综合知识");
        row.setHasChoice(true);
        SimulationGoalRow mismatched = goal();
        mismatched.setSyllabusVersionId(99L);
        when(mapper.selectPublishedSimulation(42L, 11L)).thenReturn(row);
        when(mapper.selectCurrentGoal(42L)).thenReturn(mismatched);

        var result = service.detail(42L, "11");

        assertThat(result.access().action()).isEqualTo("BLOCKED");
        assertThat(result.access().blockCode()).isEqualTo("SIMULATION_GOAL_SCOPE_MISMATCH");
    }

    @Test
    void previewReadsOnlyOrderedPublishedQuestionStemsFromTheCurrentPaper() {
        SimulationPreviewQuestionRow first = previewQuestion(1, "CHOICE", "第一题题干");
        SimulationPreviewQuestionRow second = previewQuestion(2, "CASE", "第二题题干");
        when(mapper.selectPublishedSimulation(42L, 11L)).thenReturn(paper());
        when(mapper.selectPublishedSimulationPreviewQuestions(11L)).thenReturn(List.of(first, second));

        var result = service.preview(42L, "11");

        assertThat(result).extracting("collectionId", "revisionId", "collectionName")
            .containsExactly("11", "12", "模拟卷");
        assertThat(result.questions()).extracting("questionOrder", "questionType", "stem")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "CHOICE", "第一题题干"),
                org.assertj.core.groups.Tuple.tuple(2, "CASE", "第二题题干")
            );
        verify(mapper).selectPublishedSimulation(42L, 11L);
        verify(mapper).selectPublishedSimulationPreviewQuestions(11L);
    }

    @Test
    void startDelegatesToTheFormalExamEngine() {
        StartSimulationSessionBo command = new StartSimulationSessionBo();
        command.setExpectedRevisionId("12");
        command.setExpectedGoalVersion(BigInteger.ZERO);
        when(formalExamEngine.start(42L, 11L, "SIMULATION", "simulation", 12L, 0L,
            "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")).thenReturn(new FormalExamEngine.StartResult(
                "31", "12", false, 1, "2026-08-18T10:00:00+08:00", 7200,
                75,
                "/learning/question-bank/mock-exams/exam?sessionId=31"));

        var result = service.start(42L, "11", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", command);

        assertThat(result.sessionId()).isEqualTo("31");
        assertThat(result.answerPath()).contains("mock-exams/exam");
        verifyNoInteractions(mapper);
    }

    @Test
    void malformedStartRequestReturns400BeforeTheGateAndDoesNotTouchTheMapper() {
        StartSimulationSessionBo command = new StartSimulationSessionBo();
        command.setExpectedRevisionId("12");
        command.setExpectedGoalVersion(BigInteger.ONE);

        SimulationException exception = catchThrowableOfType(() -> service.start(42L, "0", "not-a-uuid", command),
            SimulationException.class);

        assertThat(exception.status()).isEqualTo(400);
        assertThat(exception.errorCode()).isEqualTo("SIMULATION_REQUEST_INVALID");
        assertThat(exception.apiFieldErrors()).singleElement().satisfies(field -> {
            assertThat(field.field()).isEqualTo("collectionId");
            assertThat(field.code()).isEqualTo("OUT_OF_RANGE");
        });
        verifyNoInteractions(mapper);
    }

    @Test
    void unexpectedDisplayFailureIsRedactedAsSimulationSystemFailure() {
        when(mapper.selectPublishedSimulation(42L, 11L)).thenThrow(new IllegalStateException("database detail"));

        SimulationException exception = catchThrowableOfType(() -> service.detail(42L, "11"), SimulationException.class);

        assertThat(exception.status()).isEqualTo(500);
        assertThat(exception.errorCode()).isEqualTo("SIMULATION_SYSTEM_FAILURE");
        assertThat(exception.getCause()).hasMessage("database detail");
    }

    private static SimulationQueryBo query(String certificationId, String syllabusVersionId, String keyword,
                                           Integer pageNum, Integer pageSize) {
        SimulationQueryBo query = new SimulationQueryBo();
        query.setCertificationId(certificationId);
        query.setSyllabusVersionId(syllabusVersionId);
        query.setKeyword(keyword);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return query;
    }

    private static SimulationOptionRow option(long certificationId, String certificationName,
                                               long syllabusVersionId, String syllabusVersionName) {
        SimulationOptionRow row = new SimulationOptionRow();
        row.setCertificationId(certificationId);
        row.setCertificationName(certificationName);
        row.setSyllabusVersionId(syllabusVersionId);
        row.setSyllabusVersionName(syllabusVersionName);
        return row;
    }

    private static SimulationPaperRow paper() {
        SimulationPaperRow row = new SimulationPaperRow();
        row.setCollectionId(11L);
        row.setRevisionId(12L);
        row.setCollectionCode("SIM-001");
        row.setCollectionName("模拟卷");
        row.setCertificationId(9L);
        row.setCertificationName("系统架构设计师");
        row.setSyllabusVersionId(21L);
        row.setSyllabusVersionName("第二版");
        row.setDurationMinutes(150);
        return row;
    }

    private static SimulationGoalRow goal() {
        SimulationGoalRow row = new SimulationGoalRow();
        row.setId(88L);
        row.setCertificationId(9L);
        row.setSyllabusVersionId(21L);
        row.setRowVersion(3L);
        return row;
    }

    private static SimulationPreviewQuestionRow previewQuestion(int order, String type, String stem) {
        SimulationPreviewQuestionRow row = new SimulationPreviewQuestionRow();
        row.setQuestionOrder(order);
        row.setQuestionType(type);
        row.setStem(stem);
        return row;
    }
}
