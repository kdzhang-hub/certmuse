package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Deterministically ranked knowledge-point candidate and its frozen content JSON. */
@Data
public class LearningTaskCandidateRow {
    private long knowledgePointId;
    private long examSubjectId;
    private String knowledgeName;
    private String profileStatus;
    private String contentSnapshot;
}
