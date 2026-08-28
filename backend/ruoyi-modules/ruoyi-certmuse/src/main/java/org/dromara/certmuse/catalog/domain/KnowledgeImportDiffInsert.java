package org.dromara.certmuse.catalog.domain;

import java.math.BigDecimal;

/** Persistence command for one generated knowledge import difference. */
public record KnowledgeImportDiffInsert(
    long id,
    long importBatchId,
    Long importRecordId,
    Long oldKnowledgePointId,
    Long suggestedKnowledgePointId,
    Long confirmedKnowledgePointId,
    long examSubjectId,
    String action,
    String resolutionStatus,
    BigDecimal matchScore,
    String matchEvidence,
    String changedFields,
    Long userId
) {
}
