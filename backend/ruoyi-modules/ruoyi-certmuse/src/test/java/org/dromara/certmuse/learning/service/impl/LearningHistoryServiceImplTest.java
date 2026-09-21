package org.dromara.certmuse.learning.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.dromara.certmuse.learning.domain.HistoryExamQuestionRow;
import org.dromara.certmuse.learning.domain.HistoryExamRow;
import org.dromara.certmuse.learning.domain.HistoryPracticeItemRow;
import org.dromara.certmuse.learning.domain.HistoryPracticeSessionRow;
import org.dromara.certmuse.learning.domain.bo.HistoryExamQueryBo;
import org.dromara.certmuse.learning.domain.bo.HistoryPracticeQueryBo;
import org.dromara.certmuse.learning.domain.bo.HistoryTaskQueryBo;
import org.dromara.certmuse.learning.mapper.LearningHistoryMapper;
import org.dromara.certmuse.learning.support.LearningHistoryException;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("dev")
class LearningHistoryServiceImplTest {
    @Test
    void practiceListProjectsCountsAndFrozenKnowledgeInStableOrder() {
        LearningHistoryMapper mapper = mock(LearningHistoryMapper.class);
        when(mapper.selectActiveGoal(10L)).thenReturn(20L);
        when(mapper.countPractices(eq(10L), eq(20L), eq("self_practice"), isNull(), isNull(), isNull()))
            .thenReturn(1L);
        HistoryPracticeSessionRow row = new HistoryPracticeSessionRow();
        row.setSessionId(30L); row.setSessionType("self_practice"); row.setTitle("知识点练习");
        row.setSubjectId(40L); row.setSubjectName("综合知识");
        row.setCompletedAt(OffsetDateTime.parse("2026-08-20T10:00:00+08:00"));
        row.setQuestionCount(3); row.setAnsweredCount(2); row.setCorrectCount(1); row.setIncorrectCount(1);
        row.setKnowledgeSnapshots("[{\"schema_version\":\"1.0\",\"items\":[{\"knowledgePointId\":\"2\",\"knowledgePointName\":\"架构\"}]},{\"schema_version\":\"1.0\",\"items\":[{\"knowledgePointId\":\"2\",\"knowledgePointName\":\"架构\"},{\"knowledgePointId\":\"3\",\"knowledgePointName\":\"设计\"}]}]");
        when(mapper.selectPractices(eq(10L), eq(20L), eq("self_practice"), isNull(), isNull(), isNull(), eq(0), eq(10)))
            .thenReturn(List.of(row));
        HistoryPracticeQueryBo query = new HistoryPracticeQueryBo();
        query.setPracticeType("KNOWLEDGE_PRACTICE");

        var result = service(mapper).practices(10L, query);

        assertThat(result.rows()).singleElement().satisfies(item -> {
            assertThat(item.unansweredCount()).isEqualTo(1);
            assertThat(item.knowledgePoints()).extracting(x -> x.id()).containsExactly("2", "3");
        });
    }

    @Test
    void practiceDateRangeUsesShanghaiHalfOpenBounds() {
        LearningHistoryMapper mapper = mock(LearningHistoryMapper.class);
        when(mapper.selectActiveGoal(10L)).thenReturn(20L);
        HistoryPracticeQueryBo query = new HistoryPracticeQueryBo();
        query.setCompletedFrom(LocalDate.of(2026, 8, 20));
        query.setCompletedTo(LocalDate.of(2026, 8, 20));

        service(mapper).practices(10L, query);

        verify(mapper).countPractices(10L, 20L, null, null,
            OffsetDateTime.parse("2026-08-20T00:00:00+08:00"),
            OffsetDateTime.parse("2026-08-21T00:00:00+08:00"));
    }

    @Test
    void practiceDetailProjectsSubjectiveAnswerAndAiGrading() {
        LearningHistoryMapper mapper = mock(LearningHistoryMapper.class);
        when(mapper.selectActiveGoal(10L)).thenReturn(20L);
        HistoryPracticeSessionRow session = new HistoryPracticeSessionRow();
        session.setSessionId(30L); session.setSessionType("past_paper_practice"); session.setTitle("历年真题练习");
        session.setSubjectId(40L); session.setSubjectName("案例分析");
        session.setCompletedAt(OffsetDateTime.parse("2026-08-20T10:00:00+08:00"));
        session.setQuestionCount(1); session.setAnsweredCount(1); session.setCorrectCount(0); session.setIncorrectCount(1);
        when(mapper.selectPractice(10L, 20L, 30L, null, null, null)).thenReturn(session);
        HistoryPracticeItemRow question = new HistoryPracticeItemRow();
        question.setQuestionId(50L); question.setQuestionOrder(1); question.setDifficultySnapshot("hard");
        question.setPresentationSnapshot("{\"schema_version\":\"1.0\",\"questionType\":\"CASE\",\"stem\":\"案例题干\",\"images\":[]}");
        question.setGradingSnapshot("{\"schema_version\":\"1.0\",\"answer\":{\"value\":\"参考答案\"},\"analysis\":\"题目解析\"}");
        question.setKnowledgeSnapshot("{\"schema_version\":\"1.0\",\"items\":[]}");
        question.setAiRubricSnapshot("{\"items\":[{\"code\":\"P1\",\"description\":\"关键点\",\"weight\":\"1\"}]}");
        question.setAttemptId(60L); question.setSubmittedAt(OffsetDateTime.parse("2026-08-20T09:50:00+08:00"));
        question.setAnswerData("{\"schema_version\":\"subjective_answer/1.0\",\"value\":\"我的案例作答\"}"); question.setGradingStatus("graded");
        question.setGradingSource("AI"); question.setGradingRevisionNo(1);
        question.setGradingResult("{\"scoreRate\":\"0.6\",\"feedback\":\"关键点基本正确。\",\"itemResults\":[{\"code\":\"P1\",\"scoreRate\":\"0.6\"}]}");
        question.setScore(new BigDecimal("6")); question.setMaxScore(new BigDecimal("10"));
        question.setScoreRate(new BigDecimal("0.6"));
        when(mapper.selectPracticeItems(10L, 20L, 30L)).thenReturn(List.of(question));

        var detail = service(mapper).practice(10L, "30", null);

        assertThat(detail.questions()).singleElement().satisfies(item -> {
            assertThat(item.questionType()).isEqualTo("CASE");
            assertThat(item.submission().textAnswer()).isEqualTo("我的案例作答");
            assertThat(item.result().referenceAnswer()).isEqualTo("参考答案");
            assertThat(item.result().aiFeedback()).isEqualTo("关键点基本正确。");
            assertThat(item.result().gradingItems()).singleElement().satisfies(scoreItem ->
                assertThat(scoreItem.description()).isEqualTo("关键点"));
        });
    }

    @Test
    void examListProjectsCompletedSession() {
        LearningHistoryMapper mapper = mock(LearningHistoryMapper.class);
        when(mapper.selectActiveGoal(10L)).thenReturn(20L);
        when(mapper.countExams(eq(10L), eq(20L), isNull(), isNull(), isNull())).thenReturn(1L);
        HistoryExamRow row = new HistoryExamRow();
        row.setSessionId(30L); row.setSessionStatus("completed"); row.setExamType("SIMULATION"); row.setTitle("模拟卷");
        row.setSubjectId(40L); row.setSubjectName("系统设计");
        row.setCompletedAt(OffsetDateTime.parse("2026-08-19T10:30:00+08:00"));
        row.setReportStatus("AVAILABLE"); row.setScore(new BigDecimal("25")); row.setMaxScore(new BigDecimal("50"));
        row.setScoreRate(new BigDecimal("0.5")); row.setDurationSeconds(6420L); row.setDurationLimitSeconds(7200L);
        row.setDurationStatus("AVAILABLE");
        when(mapper.selectExams(eq(10L), eq(20L), isNull(), isNull(), isNull(), eq(0), eq(10)))
            .thenReturn(List.of(row));
        LearningHistoryServiceImpl service = service(mapper);

        var result = service.exams(10L, new HistoryExamQueryBo());

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.rows()).singleElement().satisfies(item -> {
            assertThat(item.sessionId()).isEqualTo("30");
            assertThat(item.result().reportStatus()).isEqualTo("AVAILABLE");
            assertThat(item.result().score()).isEqualTo("25");
        });
        verify(mapper).countInconsistentExams(10L, 20L);
    }

    @Test
    void examDetailUsesRequestedCompletedSessionAndFrozenQuestions() {
        LearningHistoryMapper mapper = mock(LearningHistoryMapper.class);
        when(mapper.selectActiveGoal(10L)).thenReturn(20L);
        HistoryExamRow exam = new HistoryExamRow();
        exam.setSessionId(32L); exam.setSessionStatus("completed"); exam.setExamType("PAST_PAPER"); exam.setTitle("2025年真题");
        exam.setCompletedAt(OffsetDateTime.parse("2026-08-19T10:30:00+08:00")); exam.setReportStatus("AVAILABLE");
        exam.setScore(new BigDecimal("1")); exam.setMaxScore(new BigDecimal("2")); exam.setScoreRate(new BigDecimal("0.5"));
        exam.setDurationStatus("UNAVAILABLE");
        when(mapper.selectExam(10L, 20L, 32L)).thenReturn(exam);
        HistoryExamQuestionRow question = new HistoryExamQuestionRow();
        question.setQuestionId(33L); question.setQuestionOrder(1); question.setDifficultySnapshot("medium");
        question.setPresentationSnapshot("{\"schema_version\":\"1.0\",\"questionType\":\"CHOICE\",\"stem\":\"题干\",\"options\":[],\"images\":[]}");
        question.setGradingSnapshot("{\"schema_version\":\"1.0\",\"answer\":{\"value\":[]}}");
        question.setKnowledgeSnapshot("{\"schema_version\":\"1.0\",\"items\":[]}"); question.setSkipped(true);
        when(mapper.selectExamQuestions(10L, 20L, 32L)).thenReturn(List.of(question));
        LearningHistoryServiceImpl service = service(mapper);

        var detail = service.exam(10L, "32", null);

        assertThat(detail.sessionId()).isEqualTo("32");
        assertThat(detail.result().score()).isEqualTo("1");
        assertThat(detail.questions()).singleElement().satisfies(item -> {
            assertThat(item.submission().unanswered()).isTrue();
        });
    }

    @Test
    void examDetailKeepsSavedAiFeedbackWhenLegacyRubricIsMissing() {
        LearningHistoryMapper mapper = mock(LearningHistoryMapper.class);
        when(mapper.selectActiveGoal(10L)).thenReturn(20L);
        HistoryExamRow exam = new HistoryExamRow();
        exam.setSessionId(32L); exam.setSessionStatus("completed"); exam.setExamType("SIMULATION");
        exam.setTitle("案例模拟卷"); exam.setCompletedAt(OffsetDateTime.parse("2026-08-21T10:30:00+08:00"));
        exam.setReportStatus("AVAILABLE"); exam.setScore(BigDecimal.ZERO); exam.setMaxScore(BigDecimal.TEN);
        exam.setScoreRate(BigDecimal.ZERO); exam.setDurationStatus("UNAVAILABLE");
        when(mapper.selectExam(10L, 20L, 32L)).thenReturn(exam);
        HistoryExamQuestionRow question = new HistoryExamQuestionRow();
        question.setQuestionId(33L); question.setQuestionOrder(1); question.setDifficultySnapshot("medium");
        question.setPresentationSnapshot("{\"schema_version\":\"1.0\",\"questionType\":\"CASE\",\"stem\":\"题干\",\"images\":[]}");
        question.setGradingSnapshot("{\"schema_version\":\"1.0\",\"answer\":{\"value\":\"参考答案\"}}");
        question.setKnowledgeSnapshot("{\"schema_version\":\"1.0\",\"items\":[]}");
        question.setAnswerData("{\"schema_version\":\"subjective_answer/1.0\",\"value\":\"我的作答\"}");
        question.setSubmittedAt(OffsetDateTime.parse("2026-08-21T10:20:00+08:00"));
        question.setGradingStatus("graded"); question.setGradingSource("AI");
        question.setScore(BigDecimal.ZERO); question.setMaxScore(BigDecimal.TEN); question.setScoreRate(BigDecimal.ZERO);
        question.setGradingResult("{\"scoreRate\":0,\"feedback\":\"已经保存的AI评价。\",\"itemResults\":[]}");
        when(mapper.selectExamQuestions(10L, 20L, 32L)).thenReturn(List.of(question));

        var detail = service(mapper).exam(10L, "32", null);

        assertThat(detail.questions()).singleElement().satisfies(item -> {
            assertThat(item.result().aiFeedback()).isEqualTo("已经保存的AI评价。");
            assertThat(item.result().gradingItems()).isEmpty();
        });
    }

    @Test
    void missingActiveGoalRequiresExplicitHistoryGoal() {
        LearningHistoryMapper mapper = mock(LearningHistoryMapper.class);
        when(mapper.selectActiveGoal(10L)).thenReturn(null);
        LearningHistoryServiceImpl service = service(mapper);

        LearningHistoryException exception = assertThrows(LearningHistoryException.class,
            () -> service.tasks(10L, new HistoryTaskQueryBo()));

        assertThat(exception.status()).isEqualTo(422);
        assertThat(exception.errorCode()).isEqualTo("HISTORY_GOAL_REQUIRED");
    }

    @Test
    void rejectsOutOfRangePaginationBeforePersistenceQuery() {
        LearningHistoryMapper mapper = mock(LearningHistoryMapper.class);
        LearningHistoryServiceImpl service = service(mapper);
        HistoryTaskQueryBo query = new HistoryTaskQueryBo();
        query.setPageSize(101);

        LearningHistoryException exception = assertThrows(LearningHistoryException.class,
            () -> service.tasks(10L, query));

        assertThat(exception.errorCode()).isEqualTo("HISTORY_REQUEST_INVALID");
        assertThat(exception.apiFieldErrors()).singleElement().satisfies(x -> {
            assertThat(x.field()).isEqualTo("pageSize");
            assertThat(x.code()).isEqualTo("OUT_OF_RANGE");
        });
        verifyNoInteractions(mapper);
    }

    private LearningHistoryServiceImpl service(LearningHistoryMapper mapper) {
        return new LearningHistoryServiceImpl(mapper, JsonMapper.builder().build(), mock(QuestionImageUrlService.class));
    }
}
