package org.dromara.certmuse.learning.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/** Choice or plain-text subjective answer submitted in a correction session. */
@Data
public class SubmitMistakeCorrectionItemBo {
    @NotNull @Valid
    private AnswerBo answer;

    @Data
    public static class AnswerBo {
        private List<String> value;
        private String text;
    }
}
