package org.dromara.certmuse.catalog.domain.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SyllabusOverviewVo(
    String id,
    String certificationId,
    String certificationName,
    String versionName,
    LocalDate publishedDate,
    OffsetDateTime createdTime,
    LatestKnowledgeImportVo latestKnowledgeImport
) {
}
