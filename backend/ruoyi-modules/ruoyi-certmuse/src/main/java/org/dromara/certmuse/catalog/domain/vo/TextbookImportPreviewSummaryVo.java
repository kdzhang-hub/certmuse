package org.dromara.certmuse.catalog.domain.vo;

/** Aggregate counts shown before a textbook import is confirmed. */
public record TextbookImportPreviewSummaryVo(
    long totalChunks,
    long validChunks,
    long mappedChunks,
    long unmappedChunks,
    long knowledgeRelationCount,
    long imageCount,
    long errorCount,
    long warningCount
) {
}
