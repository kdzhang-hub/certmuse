package org.dromara.certmuse.learning.domain;

/** Stored state of a learning-goal creation request. */
public record LearningGoalIdempotencyRow(
    String payloadHash,
    String status,
    Long resourceId,
    String responseBody,
    Long goalOwnerId
) {
}
