package org.dromara.certmuse.catalog.domain;

import java.time.OffsetDateTime;

/** Mapper projection for a qualification. */
public record QualificationRow(long id, String certificationCode, String certificationName,
                               String qualificationLevel, String status, int sortOrder,
                               OffsetDateTime createTime, OffsetDateTime updateTime) {
}
