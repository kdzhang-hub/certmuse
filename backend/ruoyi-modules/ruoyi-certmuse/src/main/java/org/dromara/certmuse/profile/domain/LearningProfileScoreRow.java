package org.dromara.certmuse.profile.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** Persistence projection for the learner's current overall profile score. */
@Data
public class LearningProfileScoreRow {
    private String certificationName;
    private BigDecimal overallScore;
    private OffsetDateTime calculatedTime;
}
