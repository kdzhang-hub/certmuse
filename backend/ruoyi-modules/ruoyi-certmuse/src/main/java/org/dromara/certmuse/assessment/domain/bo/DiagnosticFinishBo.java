package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Confirms that the learner accepts the final diagnostic answer set. */
@Data
public class DiagnosticFinishBo {
    @NotNull @Min(0)
    private Long expectedSessionVersion;
}
