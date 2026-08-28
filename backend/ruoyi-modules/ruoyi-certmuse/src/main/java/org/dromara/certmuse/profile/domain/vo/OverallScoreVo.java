package org.dromara.certmuse.profile.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Current qualification overall score shown on the learner profile page. */
public record OverallScoreVo(
    String certificationName,
    BigDecimal overallScore,
    OffsetDateTime calculatedTime
) {
}
