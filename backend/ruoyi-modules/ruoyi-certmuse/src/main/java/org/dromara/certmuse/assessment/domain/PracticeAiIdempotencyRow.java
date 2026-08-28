package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** AI chat action idempotency projection. */
@Data
public class PracticeAiIdempotencyRow {
    private Long id;
    private String payloadHash;
    private String status;
    private Long resourceId;
    private String responseBody;
}
