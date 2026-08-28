package org.dromara.certmuse.assessment.support;

import org.dromara.certmuse.shared.schema.JsonSchemaVersion;

/**
 * Versioned JSON contracts owned by the assessment domain.
 */
public enum AssessmentJsonSchema implements JsonSchemaVersion {
    DIAGNOSTIC_PIPELINE("diagnostic_pipeline/1.0"),
    DIAGNOSTIC_IDEMPOTENCY("diagnostic_idempotency/1.0"),
    DIAGNOSTIC_REPORT("diagnostic_report/1.0"),
    PROFILE_CHANGE("profile_change/1.0"),
    PROFILE_OVERALL("profile_overall/1.0"),
    KNOWLEDGE_PRACTICE_SUBMIT("knowledge_practice_submit/1.0"),
    KNOWLEDGE_PRACTICE_CHOICE_ANSWER("knowledge_practice_choice_answer/1.0"),
    KNOWLEDGE_PRACTICE_COMPLETE("knowledge_practice_complete/1.0"),
    KNOWLEDGE_PRACTICE_PRESENTATION("knowledge_practice_presentation/1.0"),
    KNOWLEDGE_PRACTICE_GRADING("knowledge_practice_grading/1.0"),
    KNOWLEDGE_PRACTICE_KNOWLEDGE("knowledge_practice_knowledge/1.0"),
    KNOWLEDGE_PRACTICE_START("knowledge_practice_start/1.0"),
    KNOWLEDGE_PRACTICE_IDEMPOTENCY("knowledge_practice_idempotency/1.0"),
    KNOWLEDGE_PRACTICE_ACTION_IDEMPOTENCY("knowledge_practice_action_idempotency/1.0"),
    DAILY_TASK_ACTION_IDEMPOTENCY("daily_task_action_idempotency/1.0"),
    PRACTICE_AI_CREATE_RESPONSE("practice_ai_create_response/1.0"),
    PRACTICE_AI_CANCEL_RESPONSE("practice_ai_cancel_response/1.0"),
    PAST_PAPER_RESULT("past_paper_result/1.0"),
    PAST_PAPER_SUBJECT_SCORES("past_paper_subject_scores/1.0"),
    PAST_PAPER_PROFILE_SUMMARY("past_paper_profile_summary/1.0"),
    PAST_PAPER_PRESENTATION("past_paper_presentation/1.0"),
    PAST_PAPER_GRADING("past_paper_grading/1.0"),
    PAST_PAPER_KNOWLEDGE("past_paper_knowledge/1.0"),
    PAST_PAPER_IDEMPOTENCY("past_paper_idempotency/1.0"),
    PAST_PAPER_CHOICE_ANSWER("past_paper_choice_answer/1.0"),
    SUBJECTIVE_ANSWER("subjective_answer/1.0"),
    AI_RUBRIC("ai_rubric/1.0"),
    SUBJECTIVE_GRADING_RESULT("subjective_grading_result/1.0");

    private final String version;

    AssessmentJsonSchema(String version) {
        this.version = version;
    }

    @Override
    public String version() {
        return version;
    }
}
