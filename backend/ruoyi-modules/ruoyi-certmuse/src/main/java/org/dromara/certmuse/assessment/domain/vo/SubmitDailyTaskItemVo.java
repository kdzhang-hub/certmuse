package org.dromara.certmuse.assessment.domain.vo;

/** Result of synchronously grading one daily-task item. */
public record SubmitDailyTaskItemVo(int questionOrder, int submittedCount, DailyTaskItemVo.SubmissionVo submission) {}
