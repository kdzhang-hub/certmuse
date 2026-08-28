package org.dromara.certmuse.catalog.domain;

/** Knowledge point projection used while validating textbook mappings. */
public record TextbookKnowledgePointLookup(
    long id,
    long syllabusVersionId,
    long examSubjectId,
    String syllabusNumber
) {
}
