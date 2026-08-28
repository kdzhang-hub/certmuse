package org.dromara.certmuse.catalog.domain;

import lombok.Data;

/** Claimed durable textbook embedding task. */
@Data
public class TextbookEmbeddingTaskRow {
    private Long chunkId;
    private String contentHash;
    private String heading;
    private String content;
}
