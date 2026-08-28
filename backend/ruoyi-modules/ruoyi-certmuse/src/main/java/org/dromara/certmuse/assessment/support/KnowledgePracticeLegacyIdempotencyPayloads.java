package org.dromara.certmuse.assessment.support;

import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** Reproduces pre-factory idempotency payload bytes for replay compatibility. */
public final class KnowledgePracticeLegacyIdempotencyPayloads {

    private KnowledgePracticeLegacyIdempotencyPayloads() {
    }

    public static String start(JsonMapper mapper, long userId, long knowledgePointId, long version) {
        return write(mapper, Map.of("schema_version", AssessmentJsonSchema.KNOWLEDGE_PRACTICE_START.version(),
            "userId", String.valueOf(userId), "knowledgePointId", String.valueOf(knowledgePointId),
            "expectedGoalVersion", version));
    }

    public static String submit(JsonMapper mapper, long userId, long sessionId, int questionOrder, String selected) {
        return write(mapper, Map.of("schemaVersion", AssessmentJsonSchema.KNOWLEDGE_PRACTICE_SUBMIT.version(),
            "userId", String.valueOf(userId), "sessionId", String.valueOf(sessionId),
            "questionOrder", questionOrder, "value", java.util.List.of(selected)));
    }

    public static String complete(JsonMapper mapper, long userId, long sessionId) {
        return write(mapper, Map.of("schemaVersion", AssessmentJsonSchema.KNOWLEDGE_PRACTICE_COMPLETE.version(),
            "userId", String.valueOf(userId), "sessionId", String.valueOf(sessionId)));
    }

    private static String write(JsonMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("legacy knowledge-practice JSON serialization failed", exception);
        }
    }
}
