package org.dromara.certmuse.learning.domain;

import lombok.Data;

/** Owned correction session lock/read projection. */
@Data
public class MistakeSessionRow {
    private Long id;
    private Long userId;
    private Long goalId;
    private Long ruleVersionId;
    private String status;
    private Integer totalCount;
    private Integer submittedCount;
}
