package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Complete U08 setup-page response. */
public record KnowledgePracticeSetupVo(GoalVo goal, List<SubjectVo> subjects) {
    public record GoalVo(String id, String certificationName, String syllabusVersionName, long version) {}
    public record SubjectVo(String id, String subjectName, List<KnowledgePracticeNodeVo> nodes) {}
}
