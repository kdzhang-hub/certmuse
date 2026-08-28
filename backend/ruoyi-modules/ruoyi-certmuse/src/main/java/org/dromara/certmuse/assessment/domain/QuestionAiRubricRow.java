package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Immutable generated rubric owned by one question revision. */
@Data
public class QuestionAiRubricRow {
    private Long id;
    private Long questionRevisionId;
    private String contextHash;
    private String rubricData;
    private String generatorModel;
    private String generatorPromptVersion;
}
