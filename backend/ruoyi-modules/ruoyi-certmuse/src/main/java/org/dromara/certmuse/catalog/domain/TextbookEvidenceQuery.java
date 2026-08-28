package org.dromara.certmuse.catalog.domain;

import java.util.List;

/** Server-owned retrieval scope for trusted textbook evidence. */
public record TextbookEvidenceQuery(List<Long> knowledgePointIds, String queryText) {
    public TextbookEvidenceQuery {
        knowledgePointIds = knowledgePointIds == null ? List.of() : knowledgePointIds.stream().distinct().toList();
        queryText = queryText == null ? "" : queryText.trim();
    }
}
