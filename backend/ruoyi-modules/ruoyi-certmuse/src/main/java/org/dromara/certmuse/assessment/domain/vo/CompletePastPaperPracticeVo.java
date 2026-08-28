package org.dromara.certmuse.assessment.domain.vo;

/** Receipt for completing a past-paper practice session. */
public record CompletePastPaperPracticeVo(String sessionId, String sessionStatus, int submittedCount,
                                          String returnPath) {}
