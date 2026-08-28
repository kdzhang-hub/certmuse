package org.dromara.certmuse.question.domain;

import lombok.Data;

/** Published revision that must return to draft after knowledge removal. */
@Data
public class QuestionKnowledgeMaintenanceRow {
    private long questionId;
    private long revisionId;
    private long eventId;
    private long auditId;
    private String requestId;
    private String traceId;
    private Long operatorId;
}
