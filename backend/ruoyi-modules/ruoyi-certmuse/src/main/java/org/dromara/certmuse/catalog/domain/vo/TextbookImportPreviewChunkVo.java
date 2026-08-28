package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

/** One staged textbook chunk shown in preview. */
public record TextbookImportPreviewChunkVo(
    int lineNo,
    String sourceKey,
    int chunkOrder,
    String heading,
    List<String> headingPath,
    String contentPreview,
    Integer pageStart,
    Integer pageEnd,
    List<TextbookPreviewKnowledgePointVo> knowledgePoints,
    int issueCount
) {
}
