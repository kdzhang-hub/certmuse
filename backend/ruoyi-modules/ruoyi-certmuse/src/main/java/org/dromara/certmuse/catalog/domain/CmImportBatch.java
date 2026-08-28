package org.dromara.certmuse.catalog.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Import batch persistence projection.
 */
public record CmImportBatch(
    long id,
    String requestId,
    Long documentId,
    Long syllabusVersionId,
    Long examSubjectId,
    String sourceFilePath,
    String sourceFileHash,
    long sourceFileSize,
    String importType,
    String templateVersion,
    String parseConfig,
    String status,
    String currentStage,
    BigDecimal progressPercent,
    int validCount,
    int warningCount,
    int failedCount,
    int generatedCount,
    String traceId,
    OffsetDateTime createTime,
    OffsetDateTime startedTime,
    OffsetDateTime finishedTime,
    Long createBy,
    Long createDept,
    String baselineHash,
    String resolutionHash,
    Long certificationId
) {
    public CmImportBatch(
        long id, String requestId, Long documentId, Long syllabusVersionId, Long examSubjectId,
        String sourceFilePath, String sourceFileHash, long sourceFileSize, String importType,
        String templateVersion, String parseConfig, String status, String currentStage,
        BigDecimal progressPercent, int validCount, int warningCount, int failedCount, int generatedCount,
        String traceId, OffsetDateTime createTime, OffsetDateTime startedTime, OffsetDateTime finishedTime,
        Long createBy, Long createDept, String baselineHash, String resolutionHash
    ) {
        this(id, requestId, documentId, syllabusVersionId, examSubjectId, sourceFilePath, sourceFileHash,
            sourceFileSize, importType, templateVersion, parseConfig, status, currentStage, progressPercent,
            validCount, warningCount, failedCount, generatedCount, traceId, createTime, startedTime,
            finishedTime, createBy, createDept, baselineHash, resolutionHash, null);
    }

    public CmImportBatch(
        long id,
        String requestId,
        Long documentId,
        Long syllabusVersionId,
        Long examSubjectId,
        String sourceFilePath,
        String sourceFileHash,
        long sourceFileSize,
        String importType,
        String templateVersion,
        String parseConfig,
        String status,
        String currentStage,
        BigDecimal progressPercent,
        int validCount,
        int warningCount,
        int failedCount,
        int generatedCount,
        String traceId,
        OffsetDateTime createTime,
        OffsetDateTime startedTime,
        OffsetDateTime finishedTime,
        Long createBy,
        Long createDept
    ) {
        this(
            id, requestId, documentId, syllabusVersionId, examSubjectId, sourceFilePath, sourceFileHash,
            sourceFileSize, importType, templateVersion, parseConfig, status, currentStage, progressPercent,
            validCount, warningCount, failedCount, generatedCount, traceId, createTime, startedTime,
            finishedTime, createBy, createDept, null, null, null
        );
    }

    public CmImportBatch(
        long id,
        String requestId,
        Long syllabusVersionId,
        Long examSubjectId,
        String sourceFilePath,
        String sourceFileHash,
        long sourceFileSize,
        String importType,
        String templateVersion,
        String parseConfig,
        String status,
        String currentStage,
        BigDecimal progressPercent,
        int validCount,
        int warningCount,
        int failedCount,
        String traceId,
        OffsetDateTime createTime,
        OffsetDateTime startedTime,
        OffsetDateTime finishedTime,
        Long createBy,
        Long createDept
    ) {
        this(
            id, requestId, null, syllabusVersionId, examSubjectId, sourceFilePath, sourceFileHash,
            sourceFileSize, importType, templateVersion, parseConfig, status, currentStage,
            progressPercent, validCount, warningCount, failedCount, 0, traceId, createTime,
            startedTime, finishedTime, createBy, createDept, null, null, null
        );
    }
}
