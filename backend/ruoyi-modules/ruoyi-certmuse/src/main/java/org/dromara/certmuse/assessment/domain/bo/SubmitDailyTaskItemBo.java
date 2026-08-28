package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/** Frozen single-choice answer submitted for one U11 daily-task item. */
@Data
public class SubmitDailyTaskItemBo {
    @NotNull @Valid
    private AnswerBo answer;

    @Data
    public static class AnswerBo {
        @NotEmpty
        private List<String> value;
    }
}
