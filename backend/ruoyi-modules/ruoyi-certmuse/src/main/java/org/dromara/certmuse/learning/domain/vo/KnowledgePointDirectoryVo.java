package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Batch knowledge-point directory response for the authenticated learner's current goal. */
public record KnowledgePointDirectoryVo(List<KnowledgePointLookupVo> items) {
    public KnowledgePointDirectoryVo {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
