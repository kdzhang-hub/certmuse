package org.dromara.certmuse.assessment.domain.vo;

/** Completion receipt for practice or asynchronous examination result generation. */
public record PastPaperFinishVo(String sessionId, String status, String action, String resultPath) {}
