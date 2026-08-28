package org.dromara.certmuse.learning.domain;

/** Read model for a qualification that can be selected for a learning goal. */
public record LearningGoalCertificationRow(
    long certificationId,
    String certificationCode,
    String certificationName,
    long syllabusVersionId,
    String syllabusVersionName
) {
}
