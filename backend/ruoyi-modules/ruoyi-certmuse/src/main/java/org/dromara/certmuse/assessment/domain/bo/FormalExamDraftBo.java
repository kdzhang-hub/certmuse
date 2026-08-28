package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import lombok.Data;

/** Draft answer saved during a formal examination. */
@Data
public class FormalExamDraftBo {
    @NotNull
    @Valid
    private AnswerBo answer;

    @NotNull
    @PositiveOrZero
    private Long expectedSessionVersion;

    @NotNull
    @Positive
    private Integer currentQuestionOrder;

    @Data
    public static class AnswerBo {
        private List<String> choiceValue;
        private String textValue;
    }
}
