package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Frozen correction-session question with optional submitted result. */
@Data
public class MistakeSessionItemRow {
    private Long sessionQuestionId;
    private Long questionId;
    private Long questionRevisionId;
    private Long attemptId;
    private Long userId;
    private Long goalId;
    private Integer questionOrder;
    private Integer totalCount;
    private String sessionStatus;
    private String presentationSnapshot;
    private String gradingSnapshot;
    private String knowledgeSnapshot;
    private String answerData;
    private Boolean correct;
    private String gradingStatus;
    private String gradingResult;
    private String aiRubricSnapshot;
    private Long examSubjectId;
    private String evidenceGroupKey;
    private String difficultySnapshot;
}
