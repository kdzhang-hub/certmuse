package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Visible active self-practice session projection. */
@Data
public class KnowledgePracticeSessionRow {
    private Long id;
    private Long userId;
    private Long goalId;
    private String requestId;
    private Integer totalCount;
}
