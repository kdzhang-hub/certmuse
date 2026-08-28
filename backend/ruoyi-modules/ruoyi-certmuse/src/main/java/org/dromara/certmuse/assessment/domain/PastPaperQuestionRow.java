package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Frozen-or-published past-paper question projection. */
@Data
public class PastPaperQuestionRow {
    private Long sessionQuestionId;
    private Long sessionId;
    private Long questionId;
    private Long questionRevisionId;
    private Long examSubjectId;
    private Integer questionOrder;
    private String questionType;
    private String stem;
    private String analysis;
    private String answerJson;
    private String optionsJson;
    private String imagesJson;
    private String knowledgeJson;
    private String difficulty;
    private Integer estimatedSeconds;
    private String evidenceGroupKey;
    private String presentationSnapshot;
    private String gradingSnapshot;
    private String knowledgeSnapshot;
    private String draftAnswerJson;
    private String finalAnswerJson;
    private Boolean correct;
    private Boolean profileApplied;
    private String reportScore;
    private Long attemptId;
    private String score;
    private String maxScore;
    private String scoreRate;
    private String gradingStatus;
    private String gradingResult;
    private String aiRubricSnapshot;
    private Long userId;
    private Long goalId;
    private Long ruleVersionId;
}
