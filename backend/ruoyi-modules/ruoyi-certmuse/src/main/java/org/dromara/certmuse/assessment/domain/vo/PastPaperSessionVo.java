package org.dromara.certmuse.assessment.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

/** Answer-card state for a past-paper practice or examination session. */
public record PastPaperSessionVo(String sessionId, String mode, String status, int totalCount, int answeredCount,
                                 Long sessionVersion, OffsetDateTime deadlineTime, Long remainingSeconds,
                                 List<NavigationVo> navigation, String returnPath) {
    public record NavigationVo(int questionOrder, String status) {}
}
