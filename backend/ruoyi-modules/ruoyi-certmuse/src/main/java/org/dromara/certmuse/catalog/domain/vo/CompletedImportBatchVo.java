package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;

/**
 * Completed import batch list item.
 */
public record CompletedImportBatchVo(
    String id,
    String fileName,
    String importType,
    String syllabusVersionId,
    String syllabusLabel,
    String uploaderId,
    String uploaderName,
    String status,
    OffsetDateTime completedTime
) {
}
