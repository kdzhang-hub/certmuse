package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Owned self-practice session projection used by U09. */
@Data
public class KnowledgePracticeAnsweringSessionRow {
    private Long id;
    private Long userId;
    private Long goalId;
    private Long ruleVersionId;
    private String status;
    private String taskTitle;
    private Integer totalCount;
    private Integer submittedCount;
}
