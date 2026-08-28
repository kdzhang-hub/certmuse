package org.dromara.certmuse.catalog.domain.vo;

/** Result of one or more manual knowledge import decisions. */
public record KnowledgeImportDiffResolutionVo(
    String batchId,
    int resolvedCount,
    long pendingCount,
    String resolutionHash
) {
}
