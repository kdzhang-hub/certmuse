package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Frozen past-paper practice item and immutable answer feedback. */
public record PastPaperPracticeItemVo(String sessionId, int questionOrder, int totalCount, QuestionVo question,
                                      SubmissionVo submission) {
    public record QuestionVo(String questionType, String stem, String selectionMode,
                             List<OptionVo> options, List<ImageVo> images) {}
    public record OptionVo(String label, String content) {}
    public record ImageVo(String url, String alt, int sortOrder) {}
    public record SubmissionVo(List<String> selectedOptionLabels, String textAnswer, Boolean correct,
                               List<String> correctOptionLabels, String analysis, SubjectiveGradingVo subjectiveGrading) {}
    public record SubjectiveGradingVo(String state, String score, String maxScore, String scoreRate,
                                      String feedback, List<GradingItemVo> items, String errorCode) {}
    public record GradingItemVo(String code, String scoreRate) {}
}
