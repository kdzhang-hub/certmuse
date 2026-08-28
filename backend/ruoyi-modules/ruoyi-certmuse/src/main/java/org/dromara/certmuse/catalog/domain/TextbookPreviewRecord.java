package org.dromara.certmuse.catalog.domain;

/** Raw preview row loaded from textbook import staging data. */
public record TextbookPreviewRecord(
    int lineNo,
    String sourceKey,
    String rawRecord,
    int issueCount
) {
}
