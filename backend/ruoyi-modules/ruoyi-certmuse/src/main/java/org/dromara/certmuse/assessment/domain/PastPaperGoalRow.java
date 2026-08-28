package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Current active goal projection for creating a formal past-paper session. */
@Data
public class PastPaperGoalRow {
    private Long id;
    private Long certificationId;
    private String certificationName;
    private Long syllabusVersionId;
    private Long rowVersion;
}
