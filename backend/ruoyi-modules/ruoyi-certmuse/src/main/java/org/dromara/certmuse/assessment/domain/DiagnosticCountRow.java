package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Diagnostic answer-count projection. */
@Data
public class DiagnosticCountRow {
    private Integer answeredCount; private Integer totalCount; private Integer firstUnanswered;
}
