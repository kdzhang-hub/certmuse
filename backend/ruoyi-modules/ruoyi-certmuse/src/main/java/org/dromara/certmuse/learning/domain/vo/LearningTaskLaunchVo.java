package org.dromara.certmuse.learning.domain.vo;

/** Result of starting or continuing one frozen task. */
public record LearningTaskLaunchVo(String taskId, String nextAction, String sessionId) {
}
