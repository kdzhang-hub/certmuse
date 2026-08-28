package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Formal-exam session state consumed by the dedicated learner pages. */
public record FormalExamSessionVo(
    String sessionId,
    String examKind,
    String title,
    String status,
    long sessionVersion,
    int currentQuestionOrder,
    int totalCount,
    int answeredCount,
    int unansweredCount,
    int estimatedDurationSeconds,
    int effectiveElapsedSeconds,
    String serverTime,
    LeaseVo lease,
    List<NavigationVo> navigation,
    String returnPath
) {
    public record LeaseVo(boolean active, Integer questionOrder, String leaseId, String lastHeartbeatTime) { }
    public record NavigationVo(int questionOrder, String state) { }
}
