package org.dromara.certmuse.catalog.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Mapper projection for a syllabus version. */
public record SyllabusVersionRow(long id, long certificationId, String versionName,
                                 LocalDate publishedDate, OffsetDateTime createTime,
                                 OffsetDateTime updateTime) {
}
