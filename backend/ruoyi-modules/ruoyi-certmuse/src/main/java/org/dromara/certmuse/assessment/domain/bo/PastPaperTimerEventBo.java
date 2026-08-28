package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** Server-acknowledged whole-paper visibility signal. It never carries client-side time values. */
@Data
public class PastPaperTimerEventBo {
    @NotBlank
    private String eventType;

    @NotNull
    @PositiveOrZero
    private Long expectedSessionVersion;
}
