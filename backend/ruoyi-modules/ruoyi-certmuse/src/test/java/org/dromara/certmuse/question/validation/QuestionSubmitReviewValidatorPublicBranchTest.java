package org.dromara.certmuse.question.validation;

import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.vo.QuestionErrorVo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class QuestionSubmitReviewValidatorPublicBranchTest {
    private final QuestionSubmitReviewValidator validator = new QuestionSubmitReviewValidator(JsonMapper.builder().build());

    @Test
    void reportsUnsupportedTypeAndNullStem() {
        assertThat(validator.validate(revision("MATCH", null, null), List.of(), List.of(), Map.of()))
            .extracting(QuestionErrorVo.BlockingIssueVo::code)
            .containsExactly("QUESTION_TYPE_INVALID", "QUESTION_STEM_REQUIRED");
    }

    @Test
    void reportsMalformedChoiceAnswer() {
        assertThat(validator.validate(revision("CHOICE", "题干", "not-json"),
            List.of(option("A", 1), option("B", 2)), List.of(), Map.of()))
            .extracting(QuestionErrorVo.BlockingIssueVo::code).contains("QUESTION_CHOICE_ANSWER_INVALID");
    }

    @Test
    void reportsEachSubjectiveReferenceAnswerFailure() {
        for (String answer : List.of(
            "{\"schema_version\":\"1.0\",\"answer_type\":\"option_keys\",\"value\":\"答案\"}",
            "{\"schema_version\":\"1.0\",\"answer_type\":\"reference_text\",\"value\":1}",
            "{\"schema_version\":\"1.0\",\"answer_type\":\"reference_text\",\"value\":\" \"}")) {
            assertThat(validator.validate(revision("ESSAY", "题干", answer), List.of(), List.of(), Map.of()))
                .extracting(QuestionErrorVo.BlockingIssueVo::code).contains("QUESTION_REFERENCE_ANSWER_REQUIRED");
        }
    }

    private static QuestionRows.Revision revision(String type, String stem, String answer) {
        QuestionRows.Revision row = new QuestionRows.Revision();
        row.setExamSubjectId(11L); row.setQuestionType(type); row.setStem(stem); row.setAnswer(answer); return row;
    }

    private static QuestionRows.Option option(String label, int order) {
        QuestionRows.Option row = new QuestionRows.Option(); row.setLabel(label); row.setSortOrder(order); return row;
    }
}
