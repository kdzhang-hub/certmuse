package org.dromara.certmuse.catalog.domain;

/** Row inserted into cm_chunk_knowledge during confirmation. */
public record TextbookChunkKnowledgeInsert(long id, long chunkId, long knowledgePointId) {
}
