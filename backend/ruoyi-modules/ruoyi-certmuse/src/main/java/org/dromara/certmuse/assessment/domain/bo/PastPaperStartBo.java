package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** Revision and goal optimistic-lock values supplied when a formal session starts. */
@Data
public class PastPaperStartBo {
    @NotBlank
    private String expectedRevisionId;
    @NotNull @PositiveOrZero
    private Long expectedGoalVersion;
}
