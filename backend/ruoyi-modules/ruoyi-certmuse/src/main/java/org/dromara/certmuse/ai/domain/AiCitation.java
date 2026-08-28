package org.dromara.certmuse.ai.domain;

import java.util.List;

/** Public-safe textbook source attached to an AI answer. */
public record AiCitation(
    String textbookId,
    String textbookTitle,
    String edition,
    List<String> headingPath,
    Integer pageStart,
    Integer pageEnd
) {
    public AiCitation {
        headingPath = headingPath == null ? List.of() : List.copyOf(headingPath);
    }
}
