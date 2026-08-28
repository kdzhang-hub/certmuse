package org.dromara.certmuse.catalog.domain;

/** Existing knowledge point values applied during an incremental tree import. */
public record KnowledgePointUpdate(
    long id,
    Long parentId,
    String syllabusNumber,
    String syllabusTitle,
    int treeDepth,
    int sortOrder,
    String description,
    Integer importance,
    String status,
    Long userId
) {
}
