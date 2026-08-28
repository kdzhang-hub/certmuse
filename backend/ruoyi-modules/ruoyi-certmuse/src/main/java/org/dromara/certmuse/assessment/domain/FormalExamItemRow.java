package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Frozen question and draft projection for a formal examination. */
@Data
public class FormalExamItemRow {
    private Long sessionQuestionId;
    private Long attemptId;
    private Long answerId;
    private Long questionId;
    private Long questionRevisionId;
    private Long examSubjectId;
    private Integer questionOrder;
    private String evidenceGroupKey;
    private String difficulty;
    private Integer estimatedSeconds;
    private String presentationSnapshot;
    private String gradingSnapshot;
    private String knowledgeSnapshot;
    private String answerData;
    private String gradingStatus;
    private String gradingResult;
    private String gradingSource;
    private Integer gradingRevisionNo;
    private String score;
    private String maxScore;
    private String scoreRate;
    private Boolean profileApplied;
    private Integer elapsedSeconds;
    private String timerStatus;
}
