package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Joined frozen error projection used by mistake-review reads and session freezing. */
@Data
public class MistakeErrorRow {
    private Long errorRecordId;
    private Long userId;
    private Long goalId;
    private Long questionId;
    private Long questionRevisionId;
    private Long sessionQuestionId;
    private Long attemptId;
    private String sessionType;
    private String errorStatus;
    private String statusReason;
    private String detectedTime;
    private Boolean skipped;
    private String evidenceGroupKey;
    private Long examSubjectId;
    private String difficultySnapshot;
    private Integer estimatedSecondsSnapshot;
    private String presentationSnapshot;
    private String gradingSnapshot;
    private String knowledgeSnapshot;
    private String aiRubricSnapshot;
    /** Current active-goal names for frozen leaf ids, already ordered by catalog order. */
    private String currentKnowledgePoints;
    private String answerData;
}
