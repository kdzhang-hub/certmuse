package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Request to freeze all eligible questions for one visible leaf node. */
@Data
public class StartKnowledgePracticeBo {
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String knowledgePointId;

    @NotNull
    @Min(0)
    private Long expectedGoalVersion;
}
