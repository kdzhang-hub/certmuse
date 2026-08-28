package org.dromara.certmuse.catalog.domain.vo;

import org.dromara.common.core.domain.PageResult;

import java.util.List;

/** Preview assembled from validated textbook staging records. */
public record TextbookImportPreviewVo(
    TextbookImportPreviewDocumentVo document,
    TextbookImportPreviewSummaryVo summary,
    List<Object> directory,
    PageResult<TextbookImportPreviewChunkVo> chunks
) {
}
