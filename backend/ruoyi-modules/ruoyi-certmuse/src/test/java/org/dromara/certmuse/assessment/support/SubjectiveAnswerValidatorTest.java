package org.dromara.certmuse.assessment.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Regression coverage for the stable plain-text answer and score-rate boundary. */
@Tag("dev")
class SubjectiveAnswerValidatorTest {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void acceptsFrozenCaseTextAndKeepsFortyPercentBoundary() throws Exception {
        var answer = jsonMapper.readTree("{\"schemaVersion\":\"subjective_answer/1.0\",\"questionType\":\"CASE\",\"value\":\"分析结论\"}");
        assertThat(SubjectiveAnswerValidator.validate(answer, "CASE").valid()).isTrue();
        assertThat(SubjectiveAnswerValidator.scoreRate(jsonMapper.readTree("{\"scoreRate\":\"0.40\"}"))).isEqualByComparingTo(new BigDecimal("0.40000000"));
        assertThat(SubjectiveAnswerValidator.scoreRate(jsonMapper.readTree("{\"scoreRate\":\"0.39999999\"}"))).isLessThan(new BigDecimal("0.40"));
    }

    @Test
    void rejectsWrongQuestionTypeAndOutOfRangeScore() throws Exception {
        var answer = jsonMapper.readTree("{\"schemaVersion\":\"subjective_answer/1.0\",\"questionType\":\"ESSAY\",\"value\":\"正文\"}");
        assertThat(SubjectiveAnswerValidator.validate(answer, "CASE").valid()).isFalse();
        assertThat(SubjectiveAnswerValidator.scoreRate(jsonMapper.readTree("{\"scoreRate\":\"1.01\"}"))).isNull();
    }

    @Test
    void recomputesWeightedResultEvenWhenProviderRoundsTheDisplayScore() throws Exception {
        var rubric = jsonMapper.readTree("""
            {"items":[{"code":"concept","description":"概念准确","weight":"0.4"},
              {"code":"reasoning","description":"论证完整","weight":"0.6"}]}
            """);
        var valid = jsonMapper.readTree("""
            {"scoreRate":"0.7","feedback":"概念正确，但论证仍需完善。","itemResults":[
              {"code":"concept","scoreRate":"1"},{"code":"reasoning","scoreRate":"0.5"}]}
            """);
        var roundedDisplayScore = jsonMapper.readTree("""
            {"scoreRate":"1","feedback":"回答需要改进。","itemResults":[
              {"code":"concept","scoreRate":"1"},{"code":"reasoning","scoreRate":"0.5"}]}
            """);

        assertThat(SubjectiveAnswerValidator.gradingResult(rubric, valid).scoreRate()).isEqualByComparingTo("0.70000000");
        assertThat(SubjectiveAnswerValidator.gradingResult(rubric, roundedDisplayScore).scoreRate())
            .isEqualByComparingTo("0.70000000");
    }

    @Test
    void rejectsMissingItemsAndNonChineseFeedback() throws Exception {
        var rubric = jsonMapper.readTree("{\"items\":[{\"code\":\"a\",\"description\":\"评分项\",\"weight\":\"1\"}]}");
        var result = jsonMapper.readTree("{\"scoreRate\":\"1\",\"feedback\":\"perfect\",\"itemResults\":[]}");
        assertThat(SubjectiveAnswerValidator.gradingResult(rubric, result)).isNull();
        assertThat(SubjectiveAnswerValidator.gradingErrorCode(rubric, result)).isEqualTo("AI_OUTPUT_FEEDBACK_INVALID");
    }

    @Test
    void acceptsLegacyEnglishRubricDescriptionsWithChineseFeedback() throws Exception {
        var rubric = jsonMapper.readTree("""
            {"items":[{"code":"P1","description":"Explain the design decision","weight":"0.4"},
              {"code":"P2","description":"Compare the alternatives","weight":"0.6"}]}
            """);
        var result = jsonMapper.readTree("""
            {"scoreRate":"0.7","feedback":"设计依据基本正确，但对替代方案的比较不足。","itemResults":[
              {"code":"P1","scoreRate":"1"},{"code":"P2","scoreRate":"0.5"}]}
            """);

        assertThat(SubjectiveAnswerValidator.gradingResult(rubric, result).scoreRate())
            .isEqualByComparingTo("0.70000000");
    }
}
