package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Diagnostic idempotency-record projection. */
@Data
public class DiagnosticIdempotencyRow {
    private Long id; private String payloadHash; private String status; private String responseBody;
}
