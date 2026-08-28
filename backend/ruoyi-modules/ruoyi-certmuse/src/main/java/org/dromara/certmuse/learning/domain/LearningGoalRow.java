package org.dromara.certmuse.learning.domain;

import java.time.LocalDate;

/** Persistence projection of a learner's goal returned by the creation use case. */
public record LearningGoalRow(
    long id,
    long certificationId,
    String certificationName,
    long syllabusVersionId,
    String syllabusVersionName,
    Integer targetExamYear,
    Integer targetExamMonth,
    int dailyMinutes,
    String status,
    long rowVersion,
    String examBatchType,
    LocalDate targetExamDate
) {
    public LearningGoalRow(long id, long certificationId, String certificationName, long syllabusVersionId,
                           String syllabusVersionName, Integer targetExamYear, Integer targetExamMonth,
                           int dailyMinutes, String status, long rowVersion) {
        this(id, certificationId, certificationName, syllabusVersionId, syllabusVersionName, targetExamYear,
            targetExamMonth, dailyMinutes, status, rowVersion, "legacy_unknown", null);
    }
}
