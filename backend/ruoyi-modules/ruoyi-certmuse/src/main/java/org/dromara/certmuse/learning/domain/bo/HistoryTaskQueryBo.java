package org.dromara.certmuse.learning.domain.bo;

import java.time.LocalDate;
import lombok.Data;

/** Query parameters for completed daily-task history. */
@Data
public class HistoryTaskQueryBo {
    private String goalId;
    private String keyword;
    private LocalDate completedFrom;
    private LocalDate completedTo;
    private String subjectId;
    private String knowledgePointId;
    private Integer pageNum;
    private Integer pageSize;
}
