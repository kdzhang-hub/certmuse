package org.dromara.certmuse.catalog.domain;

/** Aggregate counts for a knowledge import difference set. */
public record KnowledgeImportDiffCounts(
    long unchangedCount,
    long updateCount,
    long moveCount,
    long addCount,
    long deleteCount,
    long pendingCount,
    long affectedQuestionCount,
    long offlineQuestionCount
) {
}
