package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Persistence projection of the unique active goal used by U10. */
@Data
public class LearningTaskGoalRow {
    private long id;
    private long userId;
    private long certificationId;
    private long syllabusVersionId;
    private Integer targetExamYear;
    private Integer targetExamMonth;
}
