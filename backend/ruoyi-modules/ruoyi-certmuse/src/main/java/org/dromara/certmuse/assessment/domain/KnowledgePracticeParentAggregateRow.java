package org.dromara.certmuse.assessment.domain;

import java.math.BigDecimal;
import lombok.Data;

/** Parent knowledge profile derived directly from descendant leaf profiles. */
@Data
public class KnowledgePracticeParentAggregateRow {
    private Long knowledgePointId;
    private BigDecimal ability;
    private Integer assessedLeafCount;
    private String confidence;
    private String status;
}
