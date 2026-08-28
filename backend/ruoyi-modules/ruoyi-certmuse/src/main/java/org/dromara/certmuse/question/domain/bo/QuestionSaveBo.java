package org.dromara.certmuse.question.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionSaveBo {
    @NotBlank private String baseRevisionId;
    @NotBlank private String rowVersion;
    @NotBlank private String examSubjectId;
    @NotBlank private String questionType;
    private String difficulty;
    private Integer estimatedSeconds;
    @NotBlank private String stem;
    @Valid @NotNull private List<OptionBo> options = new ArrayList<>();
    @Valid @NotNull private AnswerBo answer;
    private String analysis;
    private String commonMistakes;
    @Valid @NotNull private List<KnowledgeBindingBo> knowledgeBindings = new ArrayList<>();

    @Data
    public static class OptionBo {
        @NotBlank private String label;
        @NotBlank private String content;
        @NotNull private Integer sortOrder;
    }

    @Data
    public static class AnswerBo {
        @NotBlank private String schemaVersion;
        @NotBlank private String answerType;
        private String selectionMode;
        @NotNull private Object value;
    }

    @Data
    public static class KnowledgeBindingBo {
        @NotBlank private String knowledgePointId;
        @NotBlank private String relationRole;
        @NotNull private Integer sortOrder;
    }

}
