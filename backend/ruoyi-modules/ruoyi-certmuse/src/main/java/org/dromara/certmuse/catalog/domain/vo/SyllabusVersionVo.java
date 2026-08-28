package org.dromara.certmuse.catalog.domain.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Syllabus version returned by M01. */
public record SyllabusVersionVo(String id, String certificationId, String versionName,
                                LocalDate publishedDate, long referenceCount,
                                OffsetDateTime createTime, OffsetDateTime updateTime) {
}
