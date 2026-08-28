package org.dromara.certmuse.catalog.domain.vo;

public record KnowledgeSubjectVo(
    String id,
    String subjectCode,
    String subjectName,
    Integer sortOrder,
    Long knowledgePointCount
) {
}
