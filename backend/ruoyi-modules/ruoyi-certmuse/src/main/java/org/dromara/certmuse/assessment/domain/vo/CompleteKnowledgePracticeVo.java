package org.dromara.certmuse.assessment.domain.vo;

/** Result returned after ending a self-practice session. */
public record CompleteKnowledgePracticeVo(String sessionId, String sessionStatus, int submittedCount,
                                           String returnPath) {}
