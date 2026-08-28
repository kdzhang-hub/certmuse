package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** Explicit pause request for a formal examination. */
@Data
public class FormalExamPauseBo {
    @NotNull
    @Positive
    private Integer currentQuestionOrder;

    @NotNull
    @PositiveOrZero
    private Long expectedSessionVersion;
}
