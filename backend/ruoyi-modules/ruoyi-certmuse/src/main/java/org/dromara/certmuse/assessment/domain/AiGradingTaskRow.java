package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Durable AI rubric-generation or answer-grading task. */
@Data
public class AiGradingTaskRow {
    private Long id;
    private String taskKey;
    private String taskType;
    private String status;
    private Long questionRevisionId;
    private Long sessionQuestionId;
    private Long attemptId;
    private String payload;
    private String result;
    private String errorCode;
    private Integer retryCount;
}
