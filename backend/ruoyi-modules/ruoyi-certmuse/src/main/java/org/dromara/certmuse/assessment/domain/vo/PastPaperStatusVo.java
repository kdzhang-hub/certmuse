package org.dromara.certmuse.assessment.domain.vo;

/** Coarse result pipeline state; body details stay in the result endpoint. */
public record PastPaperStatusVo(String sessionId, String status, String action, String failureCode) {}
