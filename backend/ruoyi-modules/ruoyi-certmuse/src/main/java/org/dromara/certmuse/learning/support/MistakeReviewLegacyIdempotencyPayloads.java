package org.dromara.certmuse.learning.support;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Reproduces pre-enum mistake-review payloads for persisted idempotency hash compatibility. */
public final class MistakeReviewLegacyIdempotencyPayloads {
    private MistakeReviewLegacyIdempotencyPayloads() {
    }

    public static String create(JsonMapper mapper, long userId, List<Long> questionIds) {
        return envelope(mapper, Map.of(
            "schemaVersion", "mistake-review/1.0",
            "userId", userId,
            "questionIds", questionIds));
    }

    public static String submit(JsonMapper mapper, long userId, long sessionId, int questionOrder,
                                List<String> answer) {
        return envelope(mapper, Map.of(
            "schemaVersion", "mistake-review/1.0",
            "userId", userId,
            "sessionId", sessionId,
            "questionOrder", questionOrder,
            "answer", answer));
    }

    public static String complete(JsonMapper mapper, long userId, long sessionId) {
        return envelope(mapper, Map.of(
            "schemaVersion", "mistake-review/1.0",
            "userId", userId,
            "sessionId", sessionId));
    }

    private static String envelope(JsonMapper mapper, Map<String, ?> data) {
        try {
            return mapper.writeValueAsString(Map.of(
                "schema_version", "mistake-review/1.0",
                "data", data));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize legacy mistake-review idempotency payload", exception);
        }
    }
}
