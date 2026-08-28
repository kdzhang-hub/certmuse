package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Diagnostic question and answer projection. */
@Data
public class DiagnosticItemRow {
    private Long sessionQuestionId; private Long attemptId; private Long answerId; private Long questionId;
    private Long questionRevisionId; private Long examSubjectId; private Long knowledgePointId;
    private Integer questionOrder; private Integer estimatedSecondsSnapshot;
    private java.math.BigDecimal reportScore; private String questionType; private String difficulty;
    private String stem; private String presentationSnapshot; private String knowledgeSnapshot;
    private String evidenceGroupKey; private String answerData; private String answerSchema;
    private String answerKey; private String status; private String timerStatus;
    private String gradingSnapshot; private String aiRubricSnapshot; private String gradingStatus;
    private String gradingResult; private java.math.BigDecimal score; private java.math.BigDecimal maxScore;
    private java.math.BigDecimal scoreRate;
    private Integer elapsedSeconds; private String presentedAt;
}
