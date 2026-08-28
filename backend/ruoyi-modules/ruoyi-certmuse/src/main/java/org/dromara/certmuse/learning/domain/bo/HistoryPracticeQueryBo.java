package org.dromara.certmuse.learning.domain.bo;

import java.time.LocalDate;
import lombok.Data;

/** Query parameters for completed practice history. */
@Data
public class HistoryPracticeQueryBo {
    private String goalId;
    private String practiceType;
    private LocalDate completedFrom;
    private LocalDate completedTo;
    private Integer pageNum;
    private Integer pageSize;
}
