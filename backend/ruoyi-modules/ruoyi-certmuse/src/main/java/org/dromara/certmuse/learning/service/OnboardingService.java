package org.dromara.certmuse.learning.service;

import org.dromara.certmuse.learning.domain.vo.OnboardingStatusVo;

/** Learner initial-flow use cases. */
public interface OnboardingService {

    /** Calculates the current authenticated learner's initial-flow status. */
    OnboardingStatusVo status(Long userId);
}
