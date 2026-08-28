package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** Server-authoritative formal-exam timer event. */
@Data
public class FormalExamTimerEventBo {
    @NotBlank
    private String eventType;

    @NotNull
    @Positive
    private Integer questionOrder;

    private String leaseId;

    @NotNull
    @PositiveOrZero
    private Long expectedSessionVersion;
}
