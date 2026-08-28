package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Persistent idempotency result for one mistake-review action. */
@Data
public class MistakeIdempotencyRow {
    private Long id;
    private String payloadHash;
    private String status;
    private Long resourceId;
    private String responseBody;
}
