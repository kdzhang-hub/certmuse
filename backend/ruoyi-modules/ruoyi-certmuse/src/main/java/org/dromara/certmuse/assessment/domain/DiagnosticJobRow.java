package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Diagnostic result-job projection. */
@Data
public class DiagnosticJobRow {
    private Long id; private String status; private String payload; private String lastError;
}
