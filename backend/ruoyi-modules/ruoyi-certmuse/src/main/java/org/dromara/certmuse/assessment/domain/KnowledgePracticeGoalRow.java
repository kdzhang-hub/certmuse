package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Current learner goal and U08 gate projection. */
@Data
public class KnowledgePracticeGoalRow {
    private Long id;
    private Long certificationId;
    private String certificationName;
    private Long syllabusVersionId;
    private String syllabusVersionName;
    private Long rowVersion;
    private String goalStatus;
    private String certificationStatus;
    private String syllabusStatus;
    private Boolean onboardingComplete;
    private Integer targetExamYear;
    private Integer targetExamMonth;
}
