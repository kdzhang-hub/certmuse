package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;

/**
 * Completed import batch detail.
 */
public record CompletedImportBatchDetailVo(
    String id,
    String documentId,
    String fileName,
    String importType,
    String syllabusVersionId,
    String syllabusLabel,
    String uploaderId,
    String uploaderName,
    String status,
    OffsetDateTime completedTime,
    int totalCount,
    int validCount,
    int warningCount,
    int failedCount,
    int generatedCount
) {

    public CompletedImportBatchDetailVo(
        String id,
        String fileName,
        String importType,
        String syllabusVersionId,
        String syllabusLabel,
        String uploaderId,
        String uploaderName,
        String status,
        OffsetDateTime completedTime,
        int totalCount,
        int validCount,
        int warningCount,
        int failedCount
    ) {
        this(
            id, null, fileName, importType, syllabusVersionId, syllabusLabel, uploaderId,
            uploaderName, status, completedTime, totalCount, validCount, warningCount, failedCount, 0
        );
    }

    public CompletedImportBatchDetailVo(
        String id,
        String fileName,
        String importType,
        String syllabusVersionId,
        String syllabusLabel,
        String uploaderId,
        String uploaderName,
        String status,
        OffsetDateTime completedTime,
        Integer validCount,
        Integer warningCount,
        Integer failedCount
    ) {
        this(
            id, null, fileName, importType, syllabusVersionId, syllabusLabel, uploaderId,
            uploaderName, status, completedTime, validCount, warningCount, failedCount, 0
        );
    }

    public CompletedImportBatchDetailVo(
        String id,
        String documentId,
        String fileName,
        String importType,
        String syllabusVersionId,
        String syllabusLabel,
        String uploaderId,
        String uploaderName,
        String status,
        OffsetDateTime completedTime,
        Integer validCount,
        Integer warningCount,
        Integer failedCount,
        Integer generatedCount
    ) {
        this(
            id,
            documentId,
            fileName,
            importType,
            syllabusVersionId,
            syllabusLabel,
            uploaderId,
            uploaderName,
            status,
            completedTime,
            0,
            countOrZero(validCount),
            countOrZero(warningCount),
            countOrZero(failedCount),
            countOrZero(generatedCount)
        );
    }

    private static int countOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    public CompletedImportBatchDetailVo withCalculatedTotalCount() {
        return new CompletedImportBatchDetailVo(
            id,
            documentId,
            fileName,
            importType,
            syllabusVersionId,
            syllabusLabel,
            uploaderId,
            uploaderName,
            status,
            completedTime,
            validCount + failedCount,
            validCount,
            warningCount,
            failedCount,
            generatedCount
        );
    }
}
