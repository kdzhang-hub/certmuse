package org.dromara.certmuse.catalog.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Progress for a paper import including its resulting collection. */
public record PaperImportProgressVo(
    String id,
    String status,
    String currentStage,
    BigDecimal progressPercent,
    int totalCount,
    int validCount,
    int warningCount,
    int failedCount,
    String syllabusVersionId,
    String syllabusVersionName,
    String collectionId,
    String revisionId,
    OffsetDateTime startedTime,
    OffsetDateTime finishedTime,
    String failureTraceId
) {
}
