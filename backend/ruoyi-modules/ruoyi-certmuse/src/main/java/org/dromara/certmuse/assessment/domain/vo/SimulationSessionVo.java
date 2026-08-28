package org.dromara.certmuse.assessment.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

/** Owned simulation session state and question navigation. */
public record SimulationSessionVo(String sessionId, String status, long rowVersion, int totalCount, int answeredCount,
                                  OffsetDateTime deadlineTime, long remainingSeconds, List<NavigationVo> navigation) {
    public record NavigationVo(int questionOrder, String status) { }
}
