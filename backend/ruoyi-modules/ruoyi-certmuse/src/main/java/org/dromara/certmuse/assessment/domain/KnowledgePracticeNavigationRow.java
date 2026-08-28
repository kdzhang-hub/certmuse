package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** One frozen question's navigation state. */
@Data
public class KnowledgePracticeNavigationRow {
    private Integer questionOrder;
    private Boolean submitted;
    private Boolean correct;
}
