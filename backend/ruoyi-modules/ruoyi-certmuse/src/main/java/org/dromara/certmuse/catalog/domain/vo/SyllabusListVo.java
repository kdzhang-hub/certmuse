package org.dromara.certmuse.catalog.domain.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SyllabusListVo(
    String id,
    String displayName,
    String certificationId,
    String certificationName,
    String versionName,
    LocalDate publishedDate,
    OffsetDateTime updateTime,
    OffsetDateTime latestKnowledgeImportTime,
    Long knowledgePointCount,
    String status
) {
}
