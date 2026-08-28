package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/** A saved answer and the optimistic version observed by the learner. */
@Data
public class DiagnosticDraftBo {
    @NotNull
    private JsonNode answer;
    @NotNull @Min(0)
    private Long expectedSessionVersion;
    @NotNull @Min(1)
    private Integer currentQuestionOrder;
}
