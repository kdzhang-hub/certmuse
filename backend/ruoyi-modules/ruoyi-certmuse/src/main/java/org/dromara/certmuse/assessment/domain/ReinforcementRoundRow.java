package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Persisted reinforcement recommendation and round state. */
@Data
public class ReinforcementRoundRow {
    private Long id;
    private Long userId;
    private Long sourceSessionId;
    private Integer sourceQuestionOrder;
    private Long sourceQuestionId;
    private String sourceEvidenceGroupKey;
    private String sourceDifficulty;
    private Long goalId;
    private Long syllabusVersionId;
    private Long examSubjectId;
    private Integer roundNo;
    private Long previousRoundId;
    private Long reinforcementSessionId;
    private String status;
    private String knowledgeSnapshot;
    private String sourcePresentationSnapshot;
    private Boolean sourceCorrect;
    private String recommendationReason;
    private String recommendationSource;
    private Integer estimatedCount;
    private String requestId;
    private String errorCode;
}
