package org.dromara.certmuse.assessment.domain.vo;

/** Receipt returned after a formal examination enters result processing. */
public record FormalExamFinishVo(String sessionId, String status, String action, String resultPath) { }
