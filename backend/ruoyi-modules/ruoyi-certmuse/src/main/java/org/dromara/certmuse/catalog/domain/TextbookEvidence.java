package org.dromara.certmuse.catalog.domain;

import java.math.BigDecimal;
import java.util.List;

/** One ranked, published textbook excerpt selected for an Agent run. */
public record TextbookEvidence(
    long chunkId,
    long textbookId,
    String textbookTitle,
    String edition,
    List<String> headingPath,
    Integer pageStart,
    Integer pageEnd,
    String content,
    String contentHash,
    BigDecimal lexicalScore,
    BigDecimal semanticScore,
    BigDecimal fusedScore
) {
}
