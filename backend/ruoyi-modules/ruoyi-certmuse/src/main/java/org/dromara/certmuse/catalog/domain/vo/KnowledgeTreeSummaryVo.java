package org.dromara.certmuse.catalog.domain.vo;

public record KnowledgeTreeSummaryVo(
    Integer syllabusVersionRecordCount,
    Integer subjectCount,
    Long knowledgePointCount,
    Long directoryCount,
    Long leafCount
) {
}
