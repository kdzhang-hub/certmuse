package org.dromara.certmuse.question.domain.bo;

import lombok.Data;

/**
 * 驳回题目修订的请求。
 */
@Data
public class QuestionReviewRejectBo {
    /** 审核驳回意见，由服务层在去除首尾空格后校验并持久化。 */
    private String reviewOpinion;
}
