package org.dromara.certmuse.catalog.domain;

/** Knowledge point title projection used to enrich textbook import previews. */
public record TextbookPreviewKnowledgePointLookup(
    long examSubjectId,
    String syllabusNumber,
    String syllabusTitle
) {
}
