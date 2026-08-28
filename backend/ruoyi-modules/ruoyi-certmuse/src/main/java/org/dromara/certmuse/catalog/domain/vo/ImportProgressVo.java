package org.dromara.certmuse.catalog.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Import validation progress response.
 */
public record ImportProgressVo(
    String id,
    String status,
    String currentStage,
    BigDecimal progressPercent,
    int totalCount,
    int validCount,
    int warningCount,
    int failedCount,
    OffsetDateTime startedTime,
    OffsetDateTime finishedTime,
    String failureTraceId,
    boolean knowledgeDiffRequired
) {
}
