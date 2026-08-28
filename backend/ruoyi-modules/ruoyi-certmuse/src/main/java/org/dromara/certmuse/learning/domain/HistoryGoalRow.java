package org.dromara.certmuse.learning.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/** Persistence projection for one goal visible in learning history. */
@Data
public class HistoryGoalRow {
    private long goalId;
    private String certificationName;
    private String syllabusVersionName;
    private LocalDate targetExamDate;
    private String status;
    private boolean current;
    private OffsetDateTime lastRecordAt;
}
