package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Frozen daily-task choice item with optional immutable submission result. */
public record DailyTaskItemVo(String sessionId, int questionOrder, int totalCount, QuestionVo question,
                              SubmissionVo submission) {
    public record QuestionVo(String questionType, String stem, String selectionMode,
                             List<OptionVo> options, List<ImageVo> images) {}
    public record OptionVo(String label, String content) {}
    public record ImageVo(String url, String alt, int sortOrder) {}
    public record SubmissionVo(List<String> selectedOptionLabels, boolean correct,
                               List<String> correctOptionLabels, String analysis) {}
}
