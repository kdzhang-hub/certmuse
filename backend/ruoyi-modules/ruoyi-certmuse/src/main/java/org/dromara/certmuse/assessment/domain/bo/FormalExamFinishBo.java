package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** Optimistic-lock command used to submit a formal examination. */
@Data
public class FormalExamFinishBo {
    @NotNull
    @PositiveOrZero
    private Long expectedSessionVersion;
}
