package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Learner visibility event used only to establish server-authoritative timing facts. */
@Data
public class DiagnosticTimerEventBo {
    @NotBlank
    private String eventType;
    @NotNull @Min(1)
    private Integer questionOrder;
    private String leaseId;
}
