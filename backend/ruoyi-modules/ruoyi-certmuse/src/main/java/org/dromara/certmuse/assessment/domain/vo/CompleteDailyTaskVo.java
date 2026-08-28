package org.dromara.certmuse.assessment.domain.vo;

/** Result returned after completing both daily-task items. */
public record CompleteDailyTaskVo(String sessionId, String sessionStatus, int submittedCount, String nextAction) {}
