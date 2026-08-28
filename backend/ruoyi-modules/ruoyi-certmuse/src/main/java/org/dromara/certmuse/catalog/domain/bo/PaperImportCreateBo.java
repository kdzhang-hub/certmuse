package org.dromara.certmuse.catalog.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Administration form for importing one paper as a collection draft. */
@Data
public class PaperImportCreateBo {
    /** A single question ZIP; retained for backwards compatibility. */
    private MultipartFile file;
    /** ZIP files selected from one paper folder. Mutually exclusive with {@link #file}. */
    private List<MultipartFile> files;
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

    /** Returns the submitted source ZIPs while retaining the original single-file request contract. */
    public List<MultipartFile> sourceFiles() {
        return files == null || files.isEmpty() ? (file == null ? List.of() : List.of(file)) : List.copyOf(files);
    }
}
