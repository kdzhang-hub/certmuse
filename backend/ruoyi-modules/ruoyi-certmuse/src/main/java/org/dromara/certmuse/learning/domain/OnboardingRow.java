package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Mapper projection for the learner's initial goal and diagnosis state. */
@Data
public class OnboardingRow {
    private Long goalId;
    private Long certificationId;
    private String goalStatus;
    private Long sessionId;
    private String sessionStatus;
    private String reportStatus;
    private Integer profileCount;
    private Integer taskCount;
}
