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
class QuestionSubmitReviewValidatorTest {
    private final QuestionSubmitReviewValidator validator = new QuestionSubmitReviewValidator(JsonMapper.builder().build());

    @Test
    void acceptsCompleteChoiceDraft() {
        QuestionRows.Revision revision = revision("CHOICE", "题干",
            "{\"schema_version\":\"1.0\",\"answer_type\":\"option_keys\",\"selection_mode\":\"single\",\"value\":[\"B\"]}");
        assertThat(validator.validate(revision, List.of(option("A", 1), option("B", 2)),
            List.of(knowledge(10L, "primary", 0)), Map.of(10L, metadata(10L, true, 20L, 11L)))).isEmpty();
    }

    @Test
    void acceptsSubjectiveDraftWithoutScoringPoints() {
        QuestionRows.Revision revision = revision("CASE", "题干",
            "{\"schema_version\":\"1.0\",\"answer_type\":\"reference_text\",\"value\":\"参考答案\"}");
        assertThat(validator.validate(revision, List.of(), List.of(knowledge(10L, "primary", 0)),
            Map.of(10L, metadata(10L, true, 20L, 11L)))).isEmpty();
    }

    @Test
    void rejectsSubjectiveWithoutReferenceAnswer() {
        QuestionRows.Revision revision = revision("ESSAY", "题干",
            "{\"schema_version\":\"1.0\",\"answer_type\":\"reference_text\",\"value\":\" \"}");
        assertThat(validator.validate(revision, List.of(), List.of(), Map.of()))
            .extracting(QuestionErrorVo.BlockingIssueVo::code).containsExactly("QUESTION_REFERENCE_ANSWER_REQUIRED");
    }

    @Test
    void reportsSubjectiveContentAndKnowledgeIssuesWithoutScoringPointGate() {
        List<QuestionErrorVo.BlockingIssueVo> issues = validator.validate(revision("CASE", " ", "{}"),
            List.of(option("A", 1)), List.of(knowledge(10L, "primary", 0)),
            Map.of(10L, metadata(10L, false, 20L, 11L)));
        assertThat(issues).extracting(QuestionErrorVo.BlockingIssueVo::code).containsExactly(
            "QUESTION_STEM_REQUIRED", "QUESTION_SUBJECTIVE_OPTIONS_INVALID",
            "QUESTION_REFERENCE_ANSWER_REQUIRED", "QUESTION_KNOWLEDGE_NOT_LEAF");
    }

    private static QuestionRows.Revision revision(String type, String stem, String answer) {
        QuestionRows.Revision row = new QuestionRows.Revision();
        row.setExamSubjectId(11L); row.setQuestionType(type); row.setStem(stem); row.setAnswer(answer);
        return row;
    }

    private static QuestionRows.Option option(String label, int order) {
        QuestionRows.Option row = new QuestionRows.Option(); row.setLabel(label); row.setSortOrder(order); return row;
    }

    private static QuestionRows.Knowledge knowledge(long id, String role, int order) {
        QuestionRows.Knowledge row = new QuestionRows.Knowledge();
        row.setKnowledgePointId(id); row.setRelationRole(role); row.setSortOrder(order); return row;
    }

    private static QuestionRows.Knowledge metadata(long id, boolean leaf, long syllabusId, long subjectId) {
        QuestionRows.Knowledge row = knowledge(id, "primary", 0);
        row.setLeaf(leaf); row.setSyllabusVersionId(syllabusId); row.setExamSubjectId(subjectId); return row;
    }
}
