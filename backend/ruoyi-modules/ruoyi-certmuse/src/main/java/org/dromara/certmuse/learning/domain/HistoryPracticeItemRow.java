package org.dromara.certmuse.learning.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** Frozen question and optional answer facts for one completed practice. */
@Data
public class HistoryPracticeItemRow {
    private long questionId;
    private int questionOrder;
    private String difficultySnapshot;
    private String presentationSnapshot;
    private String gradingSnapshot;
    private String knowledgeSnapshot;
    private String aiRubricSnapshot;
    private Long attemptId;
    private Boolean skipped;
    private OffsetDateTime submittedAt;
    private String answerData;
    private String gradingStatus;
    private String gradingResult;
    private String gradingSource;
    private Integer gradingRevisionNo;
    private BigDecimal score;
    private BigDecimal maxScore;
    private BigDecimal scoreRate;
}
