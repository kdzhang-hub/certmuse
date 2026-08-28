package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** Filters for the current published simulation page. */
@Data
public class SimulationQueryBo {
    private String certificationId;
    private String syllabusVersionId;
    private String keyword;

    @Min(1)
    @Max(10000)
    private Integer pageNum;

    @Min(1)
    @Max(50)
    private Integer pageSize;
}
