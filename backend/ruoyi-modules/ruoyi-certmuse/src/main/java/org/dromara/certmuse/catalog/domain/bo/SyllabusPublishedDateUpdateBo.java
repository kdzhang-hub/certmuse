package org.dromara.certmuse.catalog.domain.bo;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

/** Request to update the published date of a syllabus version. */
@Data
public class SyllabusPublishedDateUpdateBo {
    @NotNull(message = "发布日期不能为空")
    private LocalDate publishedDate;
}
