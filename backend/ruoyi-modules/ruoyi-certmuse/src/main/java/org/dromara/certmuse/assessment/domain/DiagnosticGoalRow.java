package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Active learning-goal projection used by diagnostic queries. */
@Data
public class DiagnosticGoalRow {
    private Long id; private Long certificationId; private String certificationName;
    private Long syllabusVersionId; private String syllabusVersionName; private Long rowVersion;
    private Integer targetExamYear; private Integer targetExamMonth;
}
