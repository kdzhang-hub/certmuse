package org.dromara.certmuse.learning.domain.vo;

import lombok.Data;

/** Learner first-flow state. Only stable status enums and business identifiers are exposed. */
@Data
public class OnboardingStatusVo {
    private String goalStatus;
    private String diagnosticStatus;
    private String currentCertificationId;
    private String activeSessionId;
    private String nextAction;
}
