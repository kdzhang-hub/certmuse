package org.dromara.certmuse.learning.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Frozen result and question details for one completed exam session. */
public record HistoryExamDetailVo(String sessionId, String examType, String title,
                                  HistoryExamRowVo.SubjectVo subject, OffsetDateTime completedAt,
                                  HistoryExamResultVo result, Long durationSeconds,
                                  Long durationLimitSeconds, String durationStatus,
                                  List<QuestionVo> questions) {
    public record QuestionVo(String questionId, int questionOrder, String questionType, String difficulty,
                             String stem, List<OptionVo> options, List<ImageVo> images,
                             SubmissionVo submission, ResultVo result) { }
    public record OptionVo(String label, String content) { }
    public record ImageVo(int sortOrder, String url, String altText) { }
    public record SubmissionVo(List<String> selectedOptionLabels, String textAnswer, boolean unanswered,
                               OffsetDateTime submittedAt) { }
    public record ResultVo(Boolean correct, String score, String maxScore, BigDecimal scoreRate,
                           List<String> correctOptionLabels, String referenceAnswer, String analysis,
                           String gradingStatus, String gradingSource, Integer gradingRevisionNo,
                           String aiFeedback, List<GradingItemVo> gradingItems) { }
    public record GradingItemVo(String code, String description, BigDecimal weight, BigDecimal scoreRate) { }
}
