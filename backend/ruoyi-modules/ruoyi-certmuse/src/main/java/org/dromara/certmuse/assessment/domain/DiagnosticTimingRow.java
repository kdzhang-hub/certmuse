package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Diagnostic aggregate timing projection. */
@Data
public class DiagnosticTimingRow {
    private Integer estimatedDurationSeconds; private Long effectiveElapsedSeconds; private String serverTime;
}
