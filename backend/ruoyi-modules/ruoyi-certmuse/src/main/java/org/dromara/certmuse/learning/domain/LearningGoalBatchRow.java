package org.dromara.certmuse.learning.domain;

import java.time.LocalDate;

/** Server-resolved examination batch snapshot for a learning goal. */
public record LearningGoalBatchRow(String examBatchType, LocalDate targetExamDate) {
}
