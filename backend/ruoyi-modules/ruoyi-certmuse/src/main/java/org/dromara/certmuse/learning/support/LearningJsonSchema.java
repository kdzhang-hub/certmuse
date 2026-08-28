package org.dromara.certmuse.learning.support;

import org.dromara.certmuse.shared.schema.JsonSchemaVersion;

/**
 * Versioned JSON contracts owned by the learning domain.
 */
public enum LearningJsonSchema implements JsonSchemaVersion {
    USER_GOAL_SNAPSHOT("user_goal_snapshot/1.0"),
    LEARNING_GOAL_RESPONSE("learning_goal_response/1.0"),
    TASK_REPLENISHMENT_INPUT("learning_task_replenishment_input/1.0"),
    TASK_DISPLAY("learning_task_display/1.0"),
    TASK_PROFILE("learning_task_profile/1.0"),
    DAILY_TASK_LEARNING_CONTENT("daily_task_learning_content/1.0"),
    TASK_QUESTION_ITEM("learning_task_question_item/1.0"),
    TASK_REPLENISHMENT_RESULT("learning_task_replenishment_result/1.0"),
    TASK_IDEMPOTENCY_RESPONSE("learning_task_idempotency_response/1.0"),
    MISTAKE_REVIEW("mistake-review/1.0"),
    MISTAKE_CORRECTION_CHOICE_ANSWER("mistake_correction_choice_answer/1.0");

    private final String version;

    LearningJsonSchema(String version) {
        this.version = version;
    }

    @Override
    public String version() {
        return version;
    }
}
