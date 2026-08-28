package org.dromara.certmuse.assessment.domain;

import java.time.OffsetDateTime;
import lombok.Data;

/** Owned frozen simulation session/item projection. */
@Data
public class SimulationAttemptRow {
    private Long sessionId;
    private Long goalId;
    private Long ruleVersionId;
    private Long userId;
    private String status;
    private Long rowVersion;
    private Integer totalCount;
    private Integer answeredCount;
    private OffsetDateTime deadlineTime;
    private Long sessionQuestionId;
    private Long questionId;
    private String evidenceGroupKey;
    private Long examSubjectId;
    private String difficulty;
    private Long questionRevisionId;
    private Integer questionOrder;
    private String presentationSnapshot;
    private String gradingSnapshot;
    private String knowledgeSnapshot;
    private Long attemptId;
    private String answerData;
    private String gradingStatus;
    private String score;
    private String maxScore;
}
