package org.dromara.certmuse.catalog.domain;

import java.time.OffsetDateTime;

public final class TextbookRows {
    private TextbookRows() {}
    public record Detail(Long id, Long syllabusVersionId, Long certificationId, String syllabusVersionName, String title,
                         String edition, String status, Long createBy, String createByName,
                         OffsetDateTime createTime, OffsetDateTime updateTime, String submittedByName,
                         OffsetDateTime submittedTime, String reviewedByName, OffsetDateTime reviewedTime,
                         String reviewComment, String publishedByName, OffsetDateTime publishedTime,
                         Long chunkCount, Long mappedChunkCount, Long unmappedChunkCount,
                         Long knowledgePointCount, Long imageCount, Long latestImportBatchId,
                         String sourceFileName, Long sourceFileSize, String sourceFileHash,
                         String latestImportStatus, Integer validCount, Integer warningCount,
                         Integer failedCount, OffsetDateTime latestImportCreateTime,
                         OffsetDateTime latestImportFinishedTime) {}
    public record Chunk(Long id, Long documentId, Integer chunkOrder, String heading, String content,
                        String headingPath, String sourceLocator, String contentHash,
                        String knowledgePoints, OffsetDateTime updateTime, String documentStatus) {}
    public record Scope(Long syllabusVersionId, Long examSubjectId) {}
}
