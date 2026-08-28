package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request to create or resume an initial diagnostic. */
@Data
public class DiagnosticStartBo {
    @NotBlank
    private String diagnosticRevisionId;
    @NotNull
    private Long goalVersion;
}
