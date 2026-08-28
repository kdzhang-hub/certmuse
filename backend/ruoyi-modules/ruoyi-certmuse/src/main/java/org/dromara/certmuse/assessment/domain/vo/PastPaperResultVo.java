package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Immutable scored result of a completed past-paper examination. */
public record PastPaperResultVo(String sessionId, String status, String score, String maxScore, long usedSeconds,
                                boolean profileApplied, List<ItemResultVo> questions) {
    public record ItemResultVo(int questionOrder, List<String> selectedOptionLabels, boolean correct,
                               List<String> correctOptionLabels, String analysis, boolean profileApplied,
                               String profileSuppressionReason) {}
}
