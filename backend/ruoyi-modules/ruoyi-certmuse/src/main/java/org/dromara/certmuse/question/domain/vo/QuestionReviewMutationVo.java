package org.dromara.certmuse.question.domain.vo;

/**
 * 题目修订审核状态变更结果。
 */
public record QuestionReviewMutationVo(
    String questionId,
    String revisionId,
    String status,
    String rowVersion
) {
}
