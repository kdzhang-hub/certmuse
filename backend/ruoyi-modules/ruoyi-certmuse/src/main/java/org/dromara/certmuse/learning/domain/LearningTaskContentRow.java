package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Frozen learning-content projection owned by one current task. */
@Data
public class LearningTaskContentRow {
    private long taskId;
    private int estimatedMinutes;
    private String targetData;
}
