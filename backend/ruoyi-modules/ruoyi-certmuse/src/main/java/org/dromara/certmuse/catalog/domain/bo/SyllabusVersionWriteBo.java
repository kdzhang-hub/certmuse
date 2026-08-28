package org.dromara.certmuse.catalog.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** Writable syllabus-version fields. */
@Data
public class SyllabusVersionWriteBo {
    @NotBlank @Size(max = 200)
    private String versionName;
    private LocalDate publishedDate;
}
