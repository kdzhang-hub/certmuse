package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** U09 session summary and answer-card navigation. */
public record KnowledgePracticeSessionVo(String sessionId, String sessionStatus, int totalCount, int submittedCount,
                                         List<NavigationItemVo> navigation, String nextAction, String returnPath) {
    public record NavigationItemVo(int questionOrder, String state) {}
}
