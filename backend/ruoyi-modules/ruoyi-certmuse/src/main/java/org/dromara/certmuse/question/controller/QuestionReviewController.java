package org.dromara.certmuse.question.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.question.domain.bo.QuestionReviewRejectBo;
import org.dromara.certmuse.question.domain.vo.QuestionReviewMutationVo;
import org.dromara.certmuse.question.domain.vo.QuestionSubmitReviewResultVo;
import org.dromara.certmuse.question.service.QuestionService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端题目修订审核接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/question-revisions")
public class QuestionReviewController {
    private final QuestionService service;

    /**
     * 将满足发布门禁的题目草稿提交审核。
     */
    @SaCheckPermission("certmuse:question:submit-review")
    @Log(title = "题目提交审核", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{revisionId}/submit-review")
    public R<QuestionSubmitReviewResultVo> submitReview(@PathVariable String revisionId,
                                                         @RequestHeader("X-Request-Id") String requestId) {
        return R.ok("题目已提交审核", service.submitReview(revisionId, requestId));
    }

    /**
     * 审核通过题目修订并发布。
     */
    @SaCheckPermission("certmuse:question:review")
    @Log(title = "题目审核发布", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{revisionId}/approve")
    public R<QuestionReviewMutationVo> approve(@PathVariable String revisionId,
                                                @RequestHeader("X-Request-Id") String requestId) {
        return R.ok("题目已审核并发布", service.approve(revisionId, requestId));
    }

    /**
     * 驳回题目修订并等待修改。
     */
    @SaCheckPermission("certmuse:question:review")
    @Log(title = "题目审核驳回", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{revisionId}/reject")
    public R<QuestionReviewMutationVo> reject(@PathVariable String revisionId,
                                               @RequestHeader("X-Request-Id") String requestId,
                                               @Valid @RequestBody(required = false) QuestionReviewRejectBo command) {
        return R.ok("题目已驳回", service.reject(revisionId, requestId,
            command == null ? null : command.getReviewOpinion()));
    }

    /**
     * 下架已发布题目并恢复为草稿。
     */
    @SaCheckPermission("certmuse:question:offline")
    @Log(title = "题目下架", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{revisionId}/offline")
    public R<QuestionReviewMutationVo> takeOffline(@PathVariable String revisionId,
                                                    @RequestHeader("X-Request-Id") String requestId) {
        return R.ok("题目已下架并退回草稿", service.takeOffline(revisionId, requestId));
    }
}
