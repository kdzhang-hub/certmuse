package org.dromara.certmuse.catalog.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

/** Filters for knowledge import differences. */
@Data
public class KnowledgeImportDiffQueryBo {
    private String examSubjectId;
    private String resolutionStatus;
    private String action;
    /** Whether to omit exact matches from a human-review workspace. */
    private Boolean excludeUnchanged;
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal minScore;
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal maxScore;
    @Min(1)
    private Integer pageNum;
    @Min(1)
    @Max(100)
    private Integer pageSize;
}
