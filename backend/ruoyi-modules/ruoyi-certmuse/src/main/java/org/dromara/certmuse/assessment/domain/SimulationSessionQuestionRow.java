package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Published simulation question projected once and frozen when a formal session starts. */
@Data
public class SimulationSessionQuestionRow {
    private Long questionId;
    private Long questionRevisionId;
    private Long examSubjectId;
    private Integer questionOrder;
    private String questionType;
    private String stem;
    private String optionsJson;
    private String imagesJson;
    private String answerJson;
    private String analysis;
    private String knowledgeJson;
    private String difficulty;
    private Integer estimatedSeconds;
    private String evidenceGroupKey;
    private String reportScore;
}
