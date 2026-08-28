package org.dromara.certmuse.assessment.domain.vo;

/** Created or resumed formal past-paper session. */
public record PastPaperStartVo(String sessionId, int totalCount, String action, String answerPath) {}
