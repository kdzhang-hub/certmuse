package org.dromara.certmuse.learning.domain.vo;

/** One learner-visible knowledge-point directory label. */
public record KnowledgePointLookupVo(
    String id,
    String syllabusNumber,
    String syllabusTitle,
    String examSubjectId,
    String examSubjectName
) {
}
