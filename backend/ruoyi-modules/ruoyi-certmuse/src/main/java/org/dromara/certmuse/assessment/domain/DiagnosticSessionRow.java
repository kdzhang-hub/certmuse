package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Learner diagnostic-session projection. */
@Data
public class DiagnosticSessionRow {
    private Long id; private Long userId; private Long goalId; private String status;
    private Long rowVersion; private Integer lastQuestionOrder; private Long certificationId;
    private Long syllabusVersionId; private Long collectionRevisionId; private Long ruleVersionId;
    private String submittedTime;
}
