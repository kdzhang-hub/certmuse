package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Completed formal-exam score and per-item disclosure. */
public record FormalExamResultVo(
    String sessionId,
    String examKind,
    String title,
    String status,
    boolean scoreComplete,
    int aiFailedCount,
    String score,
    String maxScore,
    long usedSeconds,
    int correctCount,
    int wrongCount,
    int unansweredCount,
    List<ItemResultVo> questions
) {
    public record ItemResultVo(
        int questionOrder,
        String questionType,
        String stem,
        List<OptionVo> options,
        List<ImageVo> images,
        List<String> selectedOptionLabels,
        String textAnswer,
        boolean unanswered,
        Boolean correct,
        String score,
        String maxScore,
        List<String> correctOptionLabels,
        String referenceAnswer,
        String analysis,
        boolean profileApplied,
        String gradingSource,
        int gradingRevisionNo,
        String gradingStatus,
        List<GradingItemVo> gradingItems
    ) { }

    public record OptionVo(String label, String content) { }

    public record ImageVo(int sortOrder, String url, String alt) { }

    public record GradingItemVo(String code, String scoreRate) { }
}
