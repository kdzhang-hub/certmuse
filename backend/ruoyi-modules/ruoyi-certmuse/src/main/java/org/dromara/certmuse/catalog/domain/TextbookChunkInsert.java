package org.dromara.certmuse.catalog.domain;

/** Row inserted into cm_document_chunk during confirmation. */
public record TextbookChunkInsert(
    long id,
    long documentId,
    int chunkOrder,
    String heading,
    String headingPath,
    String content,
    String sourceLocator,
    String contentHash,
    long createBy
) {
}
