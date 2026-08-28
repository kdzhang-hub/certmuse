package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/** Choice or frozen subjective answer / exam draft command. */
@Data
public class PastPaperAnswerBo {
    @NotNull @Valid
    private AnswerValueBo answer;
    private Long expectedSessionVersion;

    @Data
    public static class AnswerValueBo {
        private List<String> value;
        private String schemaVersion;
        private String questionType;
        private String text;
    }
}
