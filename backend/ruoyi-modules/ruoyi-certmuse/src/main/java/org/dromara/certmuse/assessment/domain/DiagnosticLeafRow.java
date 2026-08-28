package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Diagnostic knowledge-leaf projection. */
@Data
public class DiagnosticLeafRow {
    private Long id; private Long examSubjectId; private Integer importance;
}
