package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

/** Paginated knowledge import differences with batch-level impact counts. */
public record KnowledgeImportDiffPageVo(
    List<KnowledgeImportDiffVo> rows,
    long total,
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
