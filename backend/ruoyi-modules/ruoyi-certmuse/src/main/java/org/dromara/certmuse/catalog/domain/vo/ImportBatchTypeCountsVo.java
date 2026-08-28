package org.dromara.certmuse.catalog.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Counts of visible completed import batches by public import type.
 */
public record ImportBatchTypeCountsVo(
    Long all,
    @JsonProperty("knowledge_point")
    Long knowledgePoint,
    Long question,
    Long textbook
) {

    /**
     * Keeps direct callers concise while MyBatis uses the canonical Long constructor.
     */
    public ImportBatchTypeCountsVo(long all, long knowledgePoint, long question, long textbook) {
        this(Long.valueOf(all), Long.valueOf(knowledgePoint), Long.valueOf(question), Long.valueOf(textbook));
    }
}
