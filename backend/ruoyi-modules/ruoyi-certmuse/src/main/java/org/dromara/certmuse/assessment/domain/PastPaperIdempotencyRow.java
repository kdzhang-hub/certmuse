package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Stored successful U15 mutation response. */
@Data
public class PastPaperIdempotencyRow {
    private Long id;
    private String payloadHash;
    private String status;
    private Long resourceId;
    private String responseBody;
}
