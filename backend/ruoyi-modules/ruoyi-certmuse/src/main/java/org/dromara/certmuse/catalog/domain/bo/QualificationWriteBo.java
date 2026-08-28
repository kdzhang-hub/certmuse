package org.dromara.certmuse.catalog.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Writable qualification fields. */
@Data
public class QualificationWriteBo {
    @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_]+")
    private String certificationCode;
    @NotBlank @Size(max = 200)
    private String certificationName;
    @NotBlank
    private String qualificationLevel;
    @NotBlank @Pattern(regexp = "[01]")
    private String status;
    @NotNull @Min(0)
    private Integer sortOrder;
}
