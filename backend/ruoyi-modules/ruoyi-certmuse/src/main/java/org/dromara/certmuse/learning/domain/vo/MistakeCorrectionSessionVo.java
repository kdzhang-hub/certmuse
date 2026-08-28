package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Correction-session summary and answer-card navigation. */
public record MistakeCorrectionSessionVo(String sessionId, String sessionStatus, String title, int totalCount,
                                         int submittedCount, List<NavigationVo> navigation,
                                         String nextAction, String returnPath) {
    public record NavigationVo(int questionOrder, String state) {}
}
