package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Daily-task session projection used for locking and continuation. */
@Data
public class LearningTaskSessionRow {
    private long id;
    private long sourceTaskId;
    private String status;
    private boolean learningCompleted;
}
