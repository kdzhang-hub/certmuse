package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Frozen item exposed while answering or after practice submission. */
public record PastPaperItemVo(int questionOrder, int totalCount, QuestionVo question, SubmissionVo submission,
                              List<String> draftOptionLabels) {
    public record QuestionVo(String questionType, String stem, List<OptionVo> options, List<ImageVo> images) {}
    public record OptionVo(String label, String content) {}
    public record ImageVo(int sortOrder, String url, String alt) {}
    public record SubmissionVo(List<String> selectedOptionLabels, boolean correct, List<String> correctOptionLabels,
                               String analysis, boolean profileApplied, String profileSuppressionReason,
                               String gradingStatus, String gradingFeedback) {}
}
