package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Owned immutable question snapshots and optional formal answer used for AI context. */
@Data
public class PracticeAiQuestionContextRow {
    private Long sessionId;
    private Long userId;
    private Integer questionOrder;
    private String sessionStatus;
    private String presentationSnapshot;
    private String gradingSnapshot;
    private String knowledgeSnapshot;
    private Long attemptId;
    private String answerData;
    private Boolean correct;
}
