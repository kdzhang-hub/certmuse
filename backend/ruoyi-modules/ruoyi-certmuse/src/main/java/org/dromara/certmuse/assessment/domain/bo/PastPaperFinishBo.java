package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** Optimistic-lock value for manually finishing a past-paper exam. */
@Data
public class PastPaperFinishBo {
    @NotNull @PositiveOrZero
    private Long expectedSessionVersion;
}
