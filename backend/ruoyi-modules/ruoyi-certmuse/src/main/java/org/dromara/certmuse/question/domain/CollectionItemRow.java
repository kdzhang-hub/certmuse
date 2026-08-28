package org.dromara.certmuse.question.domain;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 题集题目明细及题目修订元数据的持久化投影。
 */
@Data
public class CollectionItemRow {
    private Integer itemOrder;
    private Long questionRevisionId;
    private Long questionId;
    private String questionCode;
    private String stem;
    private String questionType;
    private String difficulty;
    private String status;
    private Long examSubjectId;
    private String examSubjectName;
    private Long knowledgePointId;
    private String knowledgePointLabel;
    private BigDecimal reportScore;
    private String answerSchema;
    private Integer estimatedSeconds;
    private Long certificationId;
    private Long syllabusVersionId;
    private Integer invalidKnowledgeCount;
    private Integer missingLeafImportanceCount;
}
