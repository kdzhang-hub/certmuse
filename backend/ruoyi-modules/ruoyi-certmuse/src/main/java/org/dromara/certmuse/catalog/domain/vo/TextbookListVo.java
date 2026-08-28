package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;

public record TextbookListVo(String id, String certificationId, String syllabusVersionId, String syllabusVersionName,
                             String certificationName, String title,
                             String edition, String status, Long chunkCount, Long mappedChunkCount,
                             Long unmappedChunkCount, Long knowledgePointCount, String latestImportBatchId,
                             String latestImportStatus, String createBy, String createByName,
                             OffsetDateTime createTime, OffsetDateTime updateTime, Boolean deletable,
                             String deleteDisabledReason) {}
