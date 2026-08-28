package org.dromara.certmuse.catalog.domain;

/**
 * Idempotency record persistence projection.
 */
public record CmIdempotencyRecord(
    String payloadHash,
    String status,
    Long resourceId,
    Integer responseStatus,
    String responseBody
) {
}
