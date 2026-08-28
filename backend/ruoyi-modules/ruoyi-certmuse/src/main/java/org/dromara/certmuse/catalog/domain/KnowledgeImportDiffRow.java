package org.dromara.certmuse.catalog.domain;

import lombok.Data;

import java.math.BigDecimal;

/** Internal projection for a knowledge import difference. */
@Data
public class KnowledgeImportDiffRow {
    private long id;
    private long importBatchId;
    private Long importRecordId;
    private Long oldKnowledgePointId;
    private Long suggestedKnowledgePointId;
    private Long confirmedKnowledgePointId;
    private long examSubjectId;
    private String subjectName;
    private String action;
    private String resolutionStatus;
    private String resolutionDecision;
    private Long parentDiffId;
    private BigDecimal matchScore;
    private String matchEvidence;
    private String changedFields;
    private String oldNumber;
    private String oldTitle;
    private String newNumber;
    private String newTitle;
    private String suggestedNumber;
    private String suggestedTitle;
    private String confirmedNumber;
    private String confirmedTitle;
}
