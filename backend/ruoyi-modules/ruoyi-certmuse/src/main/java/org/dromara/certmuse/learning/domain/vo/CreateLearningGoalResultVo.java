package org.dromara.certmuse.learning.domain.vo;

/** Result of a successful first learning-goal creation. */
public record CreateLearningGoalResultVo(GoalVo goal, String nextAction) {
    public record GoalVo(
        String id,
        String certificationId,
        String certificationName,
        String syllabusVersionId,
        String syllabusVersionName,
        int targetExamYear,
        int targetExamMonth,
        int dailyMinutes,
        String status,
        long version
    ) {
    }
}
