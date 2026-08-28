package org.dromara.certmuse.learning.domain.vo;

/** Result of completing textbook learning and opening daily-task practice. */
public record StartDailyTaskPracticeVo(String taskId, String sessionId, String nextAction) {
}
