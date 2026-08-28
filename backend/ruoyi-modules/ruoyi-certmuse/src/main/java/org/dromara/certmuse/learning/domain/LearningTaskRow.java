package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Owned task and its fixed item IDs. */
@Data
public class LearningTaskRow {
    private long id;
    private long userId;
    private long goalId;
    private long examSubjectId;
    private long ruleVersionId;
    private long knowledgeItemId;
    private long questionItemId;
    private int questionCount;
    private boolean completed;
}
