package org.dromara.certmuse.learning.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** Persistence projection for a submitted historical question attempt. */
@Data
public class HistoryAttemptRow {
    private long questionId;
    private long attemptId;
    private long sessionId;
    private String sessionType;
    private int questionOrder;
    private OffsetDateTime answeredAt;
    private String difficultySnapshot;
    private String presentationSnapshot;
    private String gradingSnapshot;
    private String knowledgeSnapshot;
    private String answerData;
    private BigDecimal score;
    private BigDecimal maxScore;
    private BigDecimal scoreRate;
    private String gradingStatus;
    private boolean skipped;
    private long attemptCount;
    private int pendingErrorCount;
    private int successfulCorrectionCount;
    private int errorCount;
}
