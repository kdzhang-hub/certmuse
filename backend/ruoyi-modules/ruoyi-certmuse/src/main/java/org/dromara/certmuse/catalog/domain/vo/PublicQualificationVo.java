package org.dromara.certmuse.catalog.domain.vo;

import java.time.LocalDate;

/** Anonymous catalogue item exposed to the public site. */
public record PublicQualificationVo(
    String certificationCode,
    String certificationName,
    String qualificationLevel,
    LocalDate nearestExamStartDate
) {
}
