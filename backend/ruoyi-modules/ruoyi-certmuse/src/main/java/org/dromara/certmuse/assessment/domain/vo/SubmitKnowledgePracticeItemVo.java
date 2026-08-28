package org.dromara.certmuse.assessment.domain.vo;

/** Result of synchronously grading one U09 item. */
public record SubmitKnowledgePracticeItemVo(int questionOrder, int submittedCount,
                                            KnowledgePracticeItemVo.SubmissionVo feedback) {}
