package org.dromara.certmuse.learning.domain;

import java.time.OffsetDateTime;
import lombok.Data;

/** Persistence projection for one completed practice session. */
@Data
public class HistoryPracticeSessionRow {
    private long sessionId;
    private String sessionType;
    private String title;
    private long subjectId;
    private String subjectName;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private int questionCount;
    private int answeredCount;
    private int correctCount;
    private int incorrectCount;
    private String knowledgeSnapshots;
}
