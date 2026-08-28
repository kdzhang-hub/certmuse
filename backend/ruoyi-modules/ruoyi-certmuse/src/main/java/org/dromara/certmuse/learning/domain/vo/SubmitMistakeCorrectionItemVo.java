package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Synchronous correction grading result. */
public record SubmitMistakeCorrectionItemVo(int questionOrder, int submittedCount, FeedbackVo feedback) {
    public record FeedbackVo(List<String> selectedOptionLabels, boolean correct,
                             List<String> correctOptionLabels, String analysis, String mistakeStatus) {}
}
