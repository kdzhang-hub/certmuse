package org.dromara.certmuse.question.domain.bo;

import lombok.Data;

@Data
public class QuestionQueryBo {
    private String keyword;
    private String certificationId;
    private String syllabusVersionId;
    private String examSubjectId;
    private String knowledgePointId;
    private Boolean includeDescendants;
    private String questionType;
    private String difficulty;
    private String status;
    private Integer pageNum;
    private Integer pageSize;
}
