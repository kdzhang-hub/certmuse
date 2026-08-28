package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Qualified question and source material used to create immutable session snapshots. */
@Data
public class KnowledgePracticeQuestionRow {
    private Long questionId;
    private Long revisionId;
    private Long examSubjectId;
    private String evidenceGroupKey;
    private String questionType;
    private String difficulty;
    private Integer estimatedSeconds;
    private String stem;
    private String analysis;
    private String answerJson;
    private String optionsJson;
    private String imagesJson;
    private String knowledgeJson;
    private String gradingRulesJson;
    private java.math.BigDecimal maxScore;
}
