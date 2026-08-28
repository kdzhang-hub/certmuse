package org.dromara.certmuse.catalog.domain;

/**
 * A knowledge point ready for bulk insertion.
 */
public record KnowledgePointInsert(
    long id,
    long syllabusVersionId,
    long examSubjectId,
    Long parentId,
    String syllabusNumber,
    String syllabusTitle,
    int treeDepth,
    int sortOrder,
    String description,
    Integer importance,
    String status,
    Long createBy,
    Long createDept
) {
}
