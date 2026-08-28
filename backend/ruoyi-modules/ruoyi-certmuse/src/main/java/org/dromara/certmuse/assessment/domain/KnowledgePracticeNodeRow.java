package org.dromara.certmuse.assessment.domain;

import java.math.BigDecimal;
import lombok.Data;

/** Visible knowledge-node projection with profile and eligible question count. */
@Data
public class KnowledgePracticeNodeRow {
    private Long id;
    private Long parentId;
    private Long examSubjectId;
    private String subjectName;
    private Integer subjectOrder;
    private String syllabusNumber;
    private String syllabusTitle;
    private Integer treeDepth;
    private Integer sortOrder;
    private Integer importance;
    private Long questionCount;
    private BigDecimal currentDirectAbility;
    private String profileStatus;
    private String confidenceLevel;
}
