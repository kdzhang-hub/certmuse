package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Available diagnostic collection revision projection. */
@Data
public class DiagnosticRevisionRow {
    private Long id; private Long collectionId; private Integer questionCount;
    private Integer durationMinutes; private String status;
}
