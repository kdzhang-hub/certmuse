package org.dromara.certmuse.learning.domain.vo;

/** Recoverable current-task details returned when another task owns the goal session. */
public record LearningTaskConflictDetailsVo(String currentTaskId, String nextAction, String sessionId) {
}
