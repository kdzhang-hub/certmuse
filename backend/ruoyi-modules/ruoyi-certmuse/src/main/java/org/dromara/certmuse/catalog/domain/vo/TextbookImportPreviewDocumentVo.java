package org.dromara.certmuse.catalog.domain.vo;

/** Textbook metadata shown in import preview. */
public record TextbookImportPreviewDocumentVo(
    String id,
    String title,
    String edition,
    String syllabusVersionName
) {
}
