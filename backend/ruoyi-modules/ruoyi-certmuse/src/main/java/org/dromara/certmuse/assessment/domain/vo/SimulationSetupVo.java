package org.dromara.certmuse.assessment.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

/** Setup data for the learner simulation page. */
public record SimulationSetupVo(
    OffsetDateTime serverTime,
    String timezone,
    CurrentGoalVo currentGoal,
    List<CertificationVo> certifications
) {
    public record CurrentGoalVo(
        String id,
        String certificationId,
        String certificationName,
        String syllabusVersionId,
        String syllabusVersionName,
        long version
    ) {
    }

    public record CertificationVo(String id, String name, List<SyllabusVersionVo> syllabusVersions) {
    }

    public record SyllabusVersionVo(String id, String name) {
    }
}
