package org.dromara.certmuse.learning.domain.bo;

import java.time.LocalDate;
import lombok.Data;

/** Query parameters for completed exam history. */
@Data
public class HistoryExamQueryBo {
    private String goalId;
    private String examType;
    private LocalDate completedFrom;
    private LocalDate completedTo;
    private Integer pageNum;
    private Integer pageSize;
}
