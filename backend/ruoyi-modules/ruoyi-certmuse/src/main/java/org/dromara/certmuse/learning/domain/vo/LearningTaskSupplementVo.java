package org.dromara.certmuse.learning.domain.vo;

/** Result of a manual or scheduled task-pool replenishment. */
public record LearningTaskSupplementVo(
    int beforeAvailableCount,
    int requestedCount,
    int createdCount,
    boolean refreshRequired
) {
}
