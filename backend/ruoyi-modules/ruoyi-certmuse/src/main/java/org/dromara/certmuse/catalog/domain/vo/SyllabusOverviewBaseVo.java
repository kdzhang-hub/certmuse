package org.dromara.certmuse.catalog.domain.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Persistence projection used before the latest import is attached. */
public record SyllabusOverviewBaseVo(
    String id,
    String certificationId,
    String certificationName,
    String versionName,
    LocalDate publishedDate,
    OffsetDateTime createdTime
) {
}
