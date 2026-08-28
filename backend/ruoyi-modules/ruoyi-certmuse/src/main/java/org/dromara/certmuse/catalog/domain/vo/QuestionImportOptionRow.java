package org.dromara.certmuse.catalog.domain.vo;

/** Flat SQL projection grouped into question import options by the service. */
public record QuestionImportOptionRow(
    String examSubjectId,
    Integer subjectNo,
    String examSubjectLabel,
    String syllabusVersionId,
    String syllabusVersionLabel,
    Boolean knowledgeTreeAvailable,
    Long knowledgePointCount
) {
}
