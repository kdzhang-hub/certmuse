package org.dromara.certmuse.assessment.domain.vo;

/** Formal simulation finish acknowledgement; final score becomes available after grading. */
public record SimulationFinishVo(String sessionId, String status, String action) { }
