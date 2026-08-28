package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Public qualification and subject filters; the active goal is optional for a logged-in learner. */
public record PastPaperSetupVo(List<CertificationVo> certifications, GoalVo goal) {
    public record CertificationVo(String id, String name, List<SubjectVo> subjects) {}
    public record SubjectVo(String id, String name) {}
    public record GoalVo(String id, long rowVersion, String certificationId, String certificationName) {}
}
