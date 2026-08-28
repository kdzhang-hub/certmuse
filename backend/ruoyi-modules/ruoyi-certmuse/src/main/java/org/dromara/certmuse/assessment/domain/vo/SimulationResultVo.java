package org.dromara.certmuse.assessment.domain.vo;

/** Safe aggregate result for a finished simulation session. */
public record SimulationResultVo(String sessionId, String status, String score, String maxScore,
                                 boolean scoreComplete, int aiFailedCount) { }
