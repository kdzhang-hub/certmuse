package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Frozen eligible question projection selected for a task. */
@Data
public class LearningTaskQuestionRow {
    private long questionId;
    private long questionRevisionId;
    private long examSubjectId;
    private String evidenceGroupKey;
    private String difficulty;
    private int estimatedSeconds;
    private String presentationSnapshot;
    private String gradingSnapshot;
    private String knowledgeSnapshot;
}
