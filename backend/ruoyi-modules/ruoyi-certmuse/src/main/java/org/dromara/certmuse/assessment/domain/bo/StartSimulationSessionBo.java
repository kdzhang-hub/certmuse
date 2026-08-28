package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigInteger;
import lombok.Data;

/** Expected immutable paper and goal versions for starting a simulation. */
@Data
public class StartSimulationSessionBo {
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String expectedRevisionId;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("9223372036854775807")
    private BigInteger expectedGoalVersion;
}
