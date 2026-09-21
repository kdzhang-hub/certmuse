package org.dromara.certmuse.shared.schema;

import org.dromara.certmuse.assessment.support.AssessmentJsonSchema;
import org.dromara.certmuse.catalog.support.CatalogJsonSchema;
import org.dromara.certmuse.catalog.support.ImportJsonSchema;
import org.dromara.certmuse.learning.support.LearningJsonSchema;
import org.dromara.certmuse.question.support.QuestionJsonSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class JsonSchemaVersionCompatibilityTest {

    @Test
    void preservesSharedAndDomainSchemaVersions() {
        assertThat(SharedJsonSchema.QUESTION_ANSWER.version()).isEqualTo("1.0");
        assertThat(CatalogJsonSchema.values()).extracting(JsonSchemaVersion::version).containsExactly(
            "syllabus_published_date_response/1.0",
            "qualification_version_response/1.0",
            "exam_guidance_idempotency/1.0"
        );
        assertThat(LearningJsonSchema.values()).extracting(JsonSchemaVersion::version).containsExactly(
            "user_goal_snapshot/1.0",
            "learning_goal_response/1.0",
            "learning_task_replenishment_input/1.0",
            "learning_task_display/1.0",
            "learning_task_profile/1.0",
            "daily_task_learning_content/1.0",
            "learning_task_question_item/1.0",
            "learning_task_replenishment_result/1.0",
            "learning_task_idempotency_response/1.0",
            "mistake-review/1.0",
            "mistake_correction_choice_answer/1.0"
        );
        assertThat(QuestionJsonSchema.values()).extracting(JsonSchemaVersion::version).containsExactly(
            "1.0",
            "1.0",
            "1.0",
            "question_review_audit/1.0",
            "collection_review_audit/1.0"
        );
    }

    @Test
    void preservesAssessmentSchemaVersions() {
        assertThat(AssessmentJsonSchema.values()).extracting(JsonSchemaVersion::version).containsExactly(
            "diagnostic_pipeline/1.0",
            "diagnostic_idempotency/1.0",
            "diagnostic_report/1.0",
            "profile_change/1.0",
            "profile_overall/1.0",
            "knowledge_practice_submit/1.0",
            "knowledge_practice_choice_answer/1.0",
            "knowledge_practice_complete/1.0",
            "knowledge_practice_presentation/1.0",
            "knowledge_practice_grading/1.0",
            "knowledge_practice_knowledge/1.0",
            "knowledge_practice_start/1.0",
            "knowledge_practice_idempotency/1.0",
            "knowledge_practice_action_idempotency/1.0",
            "daily_task_action_idempotency/1.0",
            "practice_ai_create_response/1.0",
            "practice_ai_cancel_response/1.0",
            "past_paper_result/1.0",
            "past_paper_subject_scores/1.0",
            "past_paper_profile_summary/1.0",
            "past_paper_presentation/1.0",
            "past_paper_grading/1.0",
            "past_paper_knowledge/1.0",
            "past_paper_idempotency/1.0",
            "past_paper_choice_answer/1.0",
            "subjective_answer/1.0",
            "ai_rubric/1.0",
            "subjective_grading_result/1.0"
        );
    }

    @Test
    void preservesImportSchemaVersions() {
        assertThat(ImportJsonSchema.values()).extracting(JsonSchemaVersion::version).containsExactly(
            "import_parse_config/1.0",
            "question_zip/2.0",
            "knowledge_point_raw/1.0",
            "question_raw/1.0",
            "question_import_result/1.0",
            "import_result/1.0",
            "1.0",
            "1.0",
            "1.0",
            "knowledge_import_match_evidence/1.0",
            "knowledge_import_changed_fields/1.0",
            "knowledge_import_diff_resolution_response/1.0",
            "document_chunk_raw_record/1.0",
            "question_precheck_job/1.0",
            "paper_precheck_job/1.0",
            "document_chunk_precheck_job/1.0",
            "knowledge_point_precheck_job/1.0",
            "knowledge_point_persist_job/1.0",
            "question_persist_job/1.0",
            "paper_persist_job/1.0",
            "document_chunk_persist_job/1.0",
            "import_object_cleanup/1.0",
            "knowledge_point_precheck_create/1.0",
            "question_precheck_create/1.0",
            "paper_precheck_create/1.0",
            "document_chunk_precheck_create/1.0",
            "question_import_confirm/1.0",
            "paper_import_confirm/1.0",
            "document_chunk_import_confirm/1.0",
            "knowledge_point_import_confirm/1.0"
        );
    }
}
