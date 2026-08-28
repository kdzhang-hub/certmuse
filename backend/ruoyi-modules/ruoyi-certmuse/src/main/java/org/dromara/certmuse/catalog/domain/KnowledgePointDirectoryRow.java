package org.dromara.certmuse.catalog.domain;

/** One row returned by the learner-scoped knowledge-point directory query. */
public record KnowledgePointDirectoryRow(
    boolean currentGoalFound,
    Long id,
    String syllabusNumber,
    String syllabusTitle,
    Long examSubjectId,
    String examSubjectName
) {
}
