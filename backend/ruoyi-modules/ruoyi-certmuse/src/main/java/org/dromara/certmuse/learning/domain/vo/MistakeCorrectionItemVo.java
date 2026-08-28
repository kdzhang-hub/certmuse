package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** One frozen correction-session item. */
public record MistakeCorrectionItemVo(String sessionId, int questionOrder, int totalCount, QuestionVo question,
                                      SubmissionVo submission) {
    public record QuestionVo(String questionType, String stem, String selectionMode,
                             List<OptionVo> options, List<ImageVo> images) {}
    public record OptionVo(String label, String content) {}
    public record ImageVo(String url, String alt, int sortOrder) {}
    public record SubmissionVo(List<String> selectedOptionLabels, boolean correct,
                               List<String> correctOptionLabels, String analysis) {}
}
