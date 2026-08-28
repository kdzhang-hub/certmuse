package org.dromara.certmuse.assessment.domain;

import java.time.LocalDate;
import lombok.Data;

/** Qualification and syllabus option backed by at least one current published simulation. */
@Data
public class SimulationOptionRow {
    private Long certificationId;
    private String certificationName;
    private Integer certificationSortOrder;
    private Long syllabusVersionId;
    private String syllabusVersionName;
    private LocalDate syllabusPublishedDate;
}
