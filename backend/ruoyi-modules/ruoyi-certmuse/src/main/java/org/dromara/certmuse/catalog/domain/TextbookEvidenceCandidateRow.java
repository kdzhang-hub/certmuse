package org.dromara.certmuse.catalog.domain;

import java.math.BigDecimal;
import lombok.Data;

/** Persistence projection for one lexical or semantic textbook candidate. */
@Data
public class TextbookEvidenceCandidateRow {
    private Long chunkId;
    private Long textbookId;
    private String textbookTitle;
    private String edition;
    private String headingPath;
    private String sourceLocator;
    private String content;
    private String contentHash;
    private BigDecimal score;
}
