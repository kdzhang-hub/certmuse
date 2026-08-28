package org.dromara.certmuse.learning.domain.bo;

import java.time.LocalDate;
import lombok.Data;

/** Query parameters for question history. */
@Data
public class HistoryQuestionQueryBo {
    private String goalId;
    private String keyword;
    private String questionType;
    private String difficulty;
    private String knowledgePointId;
    private String correctionStatus;
    private LocalDate lastAnsweredFrom;
    private LocalDate lastAnsweredTo;
    private Integer pageNum;
    private Integer pageSize;
}
