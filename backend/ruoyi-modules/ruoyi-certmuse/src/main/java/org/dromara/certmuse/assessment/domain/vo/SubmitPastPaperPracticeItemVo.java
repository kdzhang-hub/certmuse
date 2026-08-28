package org.dromara.certmuse.assessment.domain.vo;

/** Result of synchronously grading one past-paper practice item. */
public record SubmitPastPaperPracticeItemVo(int questionOrder, int submittedCount,
                                            PastPaperPracticeItemVo.SubmissionVo feedback) {}
