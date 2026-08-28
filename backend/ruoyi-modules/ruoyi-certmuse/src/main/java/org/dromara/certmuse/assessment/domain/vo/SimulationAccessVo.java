package org.dromara.certmuse.assessment.domain.vo;

/** Learner action currently available for one simulation paper. */
public record SimulationAccessVo(
    String action,
    boolean canStart,
    String blockCode,
    String activeSessionId,
    String activeAnswerPath
) {
}
