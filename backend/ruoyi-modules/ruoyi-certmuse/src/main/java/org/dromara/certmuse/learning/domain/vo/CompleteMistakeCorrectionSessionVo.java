package org.dromara.certmuse.learning.domain.vo;

/** Completion result for a correction session. */
public record CompleteMistakeCorrectionSessionVo(String sessionId, String sessionStatus, int submittedCount,
                                                 String returnPath) {}
