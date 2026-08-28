package org.dromara.certmuse.question.domain.vo;

/**
 * 题目草稿提交审核结果。
 */
public record QuestionSubmitReviewResultVo(
    String questionId,
    String revisionId,
    String status,
    String rowVersion
) {
}
