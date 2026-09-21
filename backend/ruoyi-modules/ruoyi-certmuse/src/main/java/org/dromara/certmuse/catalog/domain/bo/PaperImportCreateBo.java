package org.dromara.certmuse.catalog.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/** Existing administration form for importing one paper as a collection draft. */
@Data
public class PaperImportCreateBo {
    @NotNull
    private MultipartFile file;
    @NotBlank @Size(max = 200)
    private String collectionName;
    @NotBlank
    private String collectionType;
    @NotNull @Min(1) @Max(1440)
    private Integer durationMinutes;
    @NotBlank
    private String certificationId;
    /** Required when collectionType is PAST_PAPER. */
    private Integer examYear;
    /** Required when collectionType is PAST_PAPER; only May or November is valid. */
    private Integer examMonth;
    /** Required when collectionType is PAST_PAPER. */
    private String paperTypeCode;
    /** Required when collectionType is PAST_PAPER. */
    private String paperTypeName;
}
