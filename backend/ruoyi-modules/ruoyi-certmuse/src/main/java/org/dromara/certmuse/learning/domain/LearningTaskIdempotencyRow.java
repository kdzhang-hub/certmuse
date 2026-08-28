package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Generic idempotency record projection for U10 actions. */
@Data
public class LearningTaskIdempotencyRow {
    private long id;
    private String payloadHash;
    private String status;
    private Long resourceId;
    private Integer responseStatus;
    private String responseBody;
}
