package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Explicitly persists the learner's current diagnostic position. */
@Data
public class DiagnosticPauseBo {
    @NotNull @Min(1)
    private Integer currentQuestionOrder;
    @NotNull @Min(0)
    private Long expectedSessionVersion;
}
