package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;

public record TextbookDetailVo(String id, String title, String syllabusVersionId, String syllabusVersionName,
                               String documentType, String edition, String status, StatisticsVo statistics,
                               LatestImportBatchVo latestImportBatch, String submittedByName,
                               OffsetDateTime submittedTime, String reviewedByName, OffsetDateTime reviewedTime,
                               String reviewComment, String publishedByName, OffsetDateTime publishedTime,
                               String createBy, String createByName, OffsetDateTime createTime,
                               OffsetDateTime updateTime, boolean deletable, String deleteDisabledReason) {
    public record StatisticsVo(long chunkCount, long mappedChunkCount, long unmappedChunkCount,
                               long knowledgePointCount, long imageCount) {}
    public record LatestImportBatchVo(String id, String sourceFileName, long sourceFileSize,
                                      String sourceFileHash, String status, int validCount,
                                      int warningCount, int failedCount, OffsetDateTime createTime,
                                      OffsetDateTime finishedTime) {}
}
