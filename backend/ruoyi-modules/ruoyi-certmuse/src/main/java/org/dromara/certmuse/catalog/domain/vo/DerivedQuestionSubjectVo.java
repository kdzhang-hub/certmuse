package org.dromara.certmuse.catalog.domain.vo;

/** A subject discovered from a question ZIP payload. */
public record DerivedQuestionSubjectVo(Integer subjectNo, String examSubjectId, String label) {
}
