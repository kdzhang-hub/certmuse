package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Persisted START_KNOWLEDGE_PRACTICE idempotency state. */
@Data
public class KnowledgePracticeIdempotencyRow {
    private Long id;
    private String payloadHash;
    private String status;
    private Long resourceId;
    private String responseBody;
}
