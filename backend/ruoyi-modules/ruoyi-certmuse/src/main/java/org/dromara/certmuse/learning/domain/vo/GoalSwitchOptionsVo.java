package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Server-authoritative data for the learner goal-switch confirmation page. */
public record GoalSwitchOptionsVo(String serverTime, String timezone, CurrentGoalVo currentGoal,
                                  List<SessionImpactVo> interruptedSessions,
                                  List<CertificationOptionVo> certifications) {
    public record CurrentGoalVo(String id, String certificationId, String certificationName, String syllabusVersionId,
                                String syllabusVersionName, int targetExamYear, int targetExamMonth,
                                String targetExamDate, String examBatchType, String status, long version) { }
    public record SessionImpactVo(String sessionType, String title, String status) { }
    public record CertificationOptionVo(String id, String code, String name, List<ExamBatchVo> examBatches) { }
    public record ExamBatchVo(int targetExamYear, int targetExamMonth, String examBatchType, String targetExamDate) { }
}
