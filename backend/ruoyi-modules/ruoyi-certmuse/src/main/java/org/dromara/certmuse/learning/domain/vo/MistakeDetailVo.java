package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Frozen detail and historical attempts for one learner mistake. */
public record MistakeDetailVo(String questionId, String stemPreview, String status, long wrongCount, long skipCount,
                              List<String> sources, String lastWrongAt, QuestionVo question,
                              List<String> originalAnswer, List<String> correctAnswer, String analysis,
                              List<HistoryVo> history, boolean canCorrect, String blockReason) {
    public record QuestionVo(String questionType, String stem, String selectionMode,
                             List<OptionVo> options, List<ImageVo> images) {}
    public record OptionVo(String label, String content) {}
    public record ImageVo(String url, String alt, int sortOrder) {}
    public record HistoryVo(String attemptId, String occurredAt, String source, List<String> answer, boolean skipped) {}
}
