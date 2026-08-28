package org.dromara.certmuse.question.support;

import org.dromara.certmuse.shared.schema.JsonSchemaVersion;

/**
 * Versioned JSON contracts owned by the question domain.
 */
public enum QuestionJsonSchema implements JsonSchemaVersion {
    COLLECTION_SNAPSHOT("1.0"),
    OPERATION_RESPONSE("1.0"),
    DELETION_AUDIT_STATE("1.0"),
    QUESTION_REVIEW_AUDIT("question_review_audit/1.0"),
    COLLECTION_REVIEW_AUDIT("collection_review_audit/1.0");

    private final String version;

    QuestionJsonSchema(String version) {
        this.version = version;
    }

    @Override
    public String version() {
        return version;
    }
}
