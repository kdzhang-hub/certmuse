package org.dromara.certmuse.assessment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.dromara.certmuse.assessment.domain.PastPaperGoalRow;
import org.dromara.certmuse.assessment.domain.PastPaperPaperRow;
import org.dromara.certmuse.assessment.domain.bo.PastPaperAnswerBo;
import org.dromara.certmuse.assessment.mapper.PastPaperMapper;
import org.dromara.certmuse.assessment.service.FormalExamEngine;
import org.dromara.certmuse.assessment.support.PastPaperException;
import org.dromara.certmuse.assessment.support.SubjectiveGradingTasks;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("dev")
class PastPaperServiceImplTest {
    private final PastPaperMapper mapper = mock(PastPaperMapper.class);
    private final PastPaperServiceImpl service = new PastPaperServiceImpl(
        mapper, JsonMapper.builder().build(), mock(QuestionImageUrlService.class), mock(FormalExamEngine.class),
        mock(SubjectiveGradingTasks.class));

    @Test
    void setupReturnsActiveGoalCertificationNameWhenPublishedOptionsDoNotContainIt() {
        PastPaperGoalRow goal = new PastPaperGoalRow();
        goal.setId(88L);
        goal.setCertificationId(2089968287713980416L);
        goal.setCertificationName("系统架构设计师");
        goal.setSyllabusVersionId(21L);
        goal.setRowVersion(3L);
        when(mapper.selectActiveGoal(42L)).thenReturn(goal);

        var result = service.setup(42L);

        assertThat(result.certifications()).isEmpty();
        assertThat(result.goal()).extracting("certificationId", "certificationName")
            .containsExactly("2089968287713980416", "系统架构设计师");
    }

    @Test
    void practiceSubmissionRejectsMoreThanOneOptionBeforePersistence() {
        PastPaperAnswerBo command = new PastPaperAnswerBo();
        PastPaperAnswerBo.AnswerValueBo answer = new PastPaperAnswerBo.AnswerValueBo();
        answer.setValue(java.util.List.of("A", "B"));
        command.setAnswer(answer);

        assertThatThrownBy(() -> service.submitPractice(42L, 88L, 1,
            "b66f2bd5-0933-4f41-a7ea-91a9f8bb5eb0", command))
            .isInstanceOfSatisfying(PastPaperException.class, exception -> {
                assertThat(exception.status()).isEqualTo(400);
                assertThat(exception.errorCode()).isEqualTo("PAST_PAPER_PRACTICE_REQUEST_INVALID");
            });
    }

    @Test
    void listMarksAnActiveFormalPastPaperSessionAsResumable() {
        PastPaperPaperRow paper = new PastPaperPaperRow();
        paper.setCollectionId(88L);
        paper.setRevisionId(89L);
        paper.setCollectionName("2025年真题");
        paper.setCertificationName("系统架构设计师");
        paper.setQuestionCount(1);
        paper.setTotalReportScore("1");
        paper.setDurationMinutes(60);
        paper.setQuestionTypesJson("[\"CHOICE\"]");
        paper.setSubjectsJson("[]");
        paper.setActiveExamSessionId(90L);
        when(mapper.selectPapers(42L, null, null, null, 20, 0)).thenReturn(List.of(paper));

        var result = service.list(42L, null, null, null, 1, 20);

        assertThat(result.getRows()).singleElement().extracting(item -> item.examAccess().action()).isEqualTo("RESUME");
    }
}
