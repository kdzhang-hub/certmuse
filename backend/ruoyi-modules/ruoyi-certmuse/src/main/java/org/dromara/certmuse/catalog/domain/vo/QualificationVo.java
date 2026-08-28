package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

/** Qualification card returned by M01. */
public record QualificationVo(String id, String certificationCode, String certificationName,
                              String qualificationLevel, String status, int sortOrder,
                              long versionCount, long referenceCount, OffsetDateTime createTime,
                              OffsetDateTime updateTime, List<SyllabusVersionVo> versions) {
}
