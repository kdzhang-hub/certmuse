package org.dromara.certmuse.learning.domain;

import java.time.OffsetDateTime;
import lombok.Data;

/** Persistence projection for one completed daily task. */
@Data
public class HistoryTaskRow {
    private long taskId;
    private long subjectId;
    private String subjectName;
    private long knowledgePointId;
    private String displaySnapshot;
    private int questionCount;
    private int correctCount;
    private int incorrectCount;
    private int skippedCount;
    private int pendingCount;
    private OffsetDateTime completedAt;
}
