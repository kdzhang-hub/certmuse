package org.dromara.certmuse.learning.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.learning.domain.bo.CreateMistakeCorrectionSessionBo;
import org.dromara.certmuse.learning.domain.bo.SubmitMistakeCorrectionItemBo;
import org.dromara.certmuse.learning.domain.vo.*;
import org.dromara.certmuse.learning.service.MistakeReviewService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Student mistake-review APIs. */
@Validated @RestController @RequiredArgsConstructor
@RequestMapping("/api/learning/mistakes")
public class MistakeReviewController {
    private final MistakeReviewService service;
    @GetMapping @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:mistake:query"}, mode = SaMode.AND)
    public R<MistakeListVo> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String knowledgePointId, @RequestParam(required = false) String source,
                                 @RequestParam(required = false) Integer minWrongCount, @RequestParam(required = false) Integer pageNum,
                                 @RequestParam(required = false) Integer pageSize) {
        return R.ok(service.list(LoginHelper.getUserId(), keyword, status, knowledgePointId, source, minWrongCount, pageNum, pageSize));
    }
    @GetMapping("/{questionId}") @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:mistake:query"}, mode = SaMode.AND)
    public R<MistakeDetailVo> detail(@PathVariable long questionId) { return R.ok(service.detail(LoginHelper.getUserId(), questionId)); }
    @PostMapping("/correction-sessions") @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:mistake:correct"}, mode = SaMode.AND)
    public R<CreateMistakeCorrectionSessionVo> create(@RequestHeader("X-Request-Id") String requestId,
                                                      @Valid @RequestBody CreateMistakeCorrectionSessionBo command) {
        return R.ok(service.create(LoginHelper.getUserId(), requestId, command));
    }
    @GetMapping("/correction-sessions/{sessionId}") @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:mistake:query"}, mode = SaMode.AND)
    public R<MistakeCorrectionSessionVo> session(@PathVariable long sessionId) { return R.ok(service.session(LoginHelper.getUserId(), sessionId)); }
    @GetMapping("/correction-sessions/{sessionId}/items/{questionOrder}") @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:mistake:query"}, mode = SaMode.AND)
    public R<MistakeCorrectionItemVo> item(@PathVariable long sessionId, @PathVariable int questionOrder) { return R.ok(service.item(LoginHelper.getUserId(), sessionId, questionOrder)); }
    @PostMapping("/correction-sessions/{sessionId}/items/{questionOrder}/submit") @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:mistake:correct"}, mode = SaMode.AND)
    public R<SubmitMistakeCorrectionItemVo> submit(@PathVariable long sessionId, @PathVariable int questionOrder,
                                                    @RequestHeader("X-Request-Id") String requestId, @Valid @RequestBody SubmitMistakeCorrectionItemBo command) {
        return R.ok(service.submit(LoginHelper.getUserId(), sessionId, questionOrder, requestId, command));
    }
    @PostMapping("/correction-sessions/{sessionId}/complete") @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:mistake:correct"}, mode = SaMode.AND)
    public R<CompleteMistakeCorrectionSessionVo> complete(@PathVariable long sessionId, @RequestHeader("X-Request-Id") String requestId) { return R.ok(service.complete(LoginHelper.getUserId(), sessionId, requestId)); }
}
