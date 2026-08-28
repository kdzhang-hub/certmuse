package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Answer-card state for an owned past-paper practice session. */
public record PastPaperPracticeSessionVo(String sessionId, String sessionStatus, int totalCount, int submittedCount,
                                         List<NavigationVo> navigation, String nextAction, String returnPath) {
    public record NavigationVo(int questionOrder, String state) {}
}
