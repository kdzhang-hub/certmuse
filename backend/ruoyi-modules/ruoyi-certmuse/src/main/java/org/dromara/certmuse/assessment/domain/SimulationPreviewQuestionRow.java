package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Minimal published question projection for the explicitly enabled learner preview. */
@Data
public class SimulationPreviewQuestionRow {
    private Integer questionOrder;
    private String questionType;
    private String stem;
}
