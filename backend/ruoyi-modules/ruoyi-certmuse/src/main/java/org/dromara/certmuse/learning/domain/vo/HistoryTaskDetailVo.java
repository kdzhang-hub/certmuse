package org.dromara.certmuse.learning.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Completed daily-task history detail. */
public record HistoryTaskDetailVo(String taskId, String title, OffsetDateTime completedAt,
                                  LookupVo knowledgePoint, SummaryVo summary,
                                  List<QuestionVo> questions) {
    public record LookupVo(String id, String name) {}
    public record SummaryVo(int questionCount, int correctCount, int incorrectCount, int skippedCount) {}
    public record QuestionVo(String questionId, int questionOrder, String questionType, String difficulty,
                             String stem, String selectionMode, List<OptionVo> options, List<ImageVo> images,
                             List<LookupVo> knowledgePoints, SubmissionVo submission, ResultVo result) {}
    public record OptionVo(String label, String content) {}
    public record ImageVo(int sortOrder, String url, String altText) {}
    public record SubmissionVo(List<String> selectedOptionLabels, boolean unanswered, OffsetDateTime submittedAt) {}
    public record ResultVo(Boolean correct, String score, String maxScore, BigDecimal scoreRate,
                           List<String> correctOptionLabels, String analysis) {}
}
