package org.dromara.certmuse.learning.domain.vo;

/** Result of an atomic learner goal switch. */
public record SwitchLearningGoalResultVo(PreviousGoalVo previousGoal, GoalSwitchOptionsVo.CurrentGoalVo currentGoal,
                                         int abandonedSessionCount, String nextAction) {
    public record PreviousGoalVo(String id, String status, long version) { }
}
