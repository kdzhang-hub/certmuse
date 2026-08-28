package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;

public record LatestKnowledgeImportVo(
    String batchId,
    OffsetDateTime finishedTime,
    String sourceFileName,
    Integer generatedCount
) {
}
