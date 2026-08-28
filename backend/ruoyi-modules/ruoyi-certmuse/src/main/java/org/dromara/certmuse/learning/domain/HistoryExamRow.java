package org.dromara.certmuse.learning.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** Persistence projection for one completed exam session in learning history. */
@Data
public class HistoryExamRow {
    private long sessionId;
    private String sessionStatus;
    private String examType;
    private String title;
    private Long subjectId;
    private String subjectName;
    private OffsetDateTime completedAt;
    private String reportStatus;
    private BigDecimal score;
    private BigDecimal maxScore;
    private BigDecimal scoreRate;
    private Long durationSeconds;
    private Long durationLimitSeconds;
    private String durationStatus;
}
