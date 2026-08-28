package org.dromara.certmuse.question.service;

import org.dromara.certmuse.question.domain.bo.QuestionQueryBo;
import org.dromara.certmuse.question.domain.vo.QuestionReviewMutationVo;
import org.dromara.certmuse.question.domain.bo.QuestionSaveBo;
import org.dromara.certmuse.question.domain.vo.QuestionDetailVo;
import org.dromara.certmuse.question.domain.vo.QuestionListVo;
import org.dromara.certmuse.question.domain.vo.QuestionPreviewVo;
import org.dromara.certmuse.question.domain.vo.QuestionSaveResultVo;
import org.dromara.certmuse.question.domain.vo.QuestionSubmitReviewResultVo;
import org.dromara.common.core.domain.PageResult;

/** 题目修订与草稿管理入口。 */
public interface QuestionService {

    /** 分页查询当前用户可见的题目。 */
    PageResult<QuestionListVo> list(QuestionQueryBo query);

    /** 查询题目或指定修订详情。 */
    QuestionDetailVo detail(String questionId, String revisionId);

    /** 查询题目修订预览。 */
    QuestionPreviewVo preview(String questionId, String revisionId);

    /** 保存题目草稿并保证请求幂等。 */
    QuestionSaveResultVo save(String questionId, String requestId, QuestionSaveBo command);

    /** 删除符合条件的题目并保证请求幂等。 */
    void delete(String questionId, String requestId);

    /** 将满足发布门禁的草稿修订提交审核并保证请求幂等。 */
    QuestionSubmitReviewResultVo submitReview(String revisionId, String requestId);

    /** 审核通过待审核题目修订并发布。 */
    QuestionReviewMutationVo approve(String revisionId, String requestId);

    /** 驳回待审核题目修订并退回草稿。 */
    QuestionReviewMutationVo reject(String revisionId, String requestId, String reviewOpinion);

    /** 将已发布题目下架并恢复为可编辑草稿。 */
    QuestionReviewMutationVo takeOffline(String revisionId, String requestId);
}
