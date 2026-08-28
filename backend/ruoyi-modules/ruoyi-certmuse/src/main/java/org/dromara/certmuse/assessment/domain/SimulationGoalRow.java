package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Current active learning goal projected for the simulation setup response. */
@Data
public class SimulationGoalRow {
    private Long id;
    private Long certificationId;
    private String certificationName;
    private Long syllabusVersionId;
    private String syllabusVersionName;
    private Long rowVersion;
}
