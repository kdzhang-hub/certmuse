package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Stable error payload for U12 goal-switch endpoints. */
public record GoalSwitchErrorVo(String errorCode, boolean retryable, String traceId,
                                List<ContractErrorVo.FieldErrorVo> fieldErrors, DetailsVo details) {
    public record DetailsVo(Integer abandonedSessionCount) { }
}
