package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;

public record KnowledgeTreeNodeVo(
    String id,
    String parentId,
    String examSubjectId,
    String examSubjectCode,
    String syllabusNumber,
    String syllabusTitle,
    Integer treeDepth,
    Integer sortOrder,
    Integer importance,
    Boolean diagnosticEnabled,
    Boolean recommendationEnabled,
    String status,
    Boolean hasChildren,
    OffsetDateTime createdTime,
    OffsetDateTime updatedTime
) {
}
