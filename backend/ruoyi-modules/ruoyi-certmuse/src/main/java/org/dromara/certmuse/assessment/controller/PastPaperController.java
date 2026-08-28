package org.dromara.certmuse.assessment.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.bo.PastPaperAnswerBo;
import org.dromara.certmuse.assessment.domain.bo.PastPaperFinishBo;
import org.dromara.certmuse.assessment.domain.bo.PastPaperStartBo;
import org.dromara.certmuse.assessment.domain.bo.PastPaperTimerEventBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamDraftBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamFinishBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamPauseBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamTimerEventBo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamFinishCheckVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamFinishVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamItemVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamResultVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamSessionVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamStatusVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamTimerEventVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperFinishCheckVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperFinishVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperItemVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperListVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperPreviewVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperResultVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperRevealVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperSessionVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperSetupVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperStartVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperStatusVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperTimerEventVo;
import org.dromara.certmuse.assessment.domain.vo.CompletePastPaperPracticeVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperPracticeItemVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperPracticeSessionVo;
import org.dromara.certmuse.assessment.domain.vo.StartPastPaperPracticeVo;
import org.dromara.certmuse.assessment.domain.vo.SubmitPastPaperPracticeItemVo;
import org.dromara.certmuse.assessment.service.PastPaperService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** U15 public browse and authenticated past-paper answering endpoints. */
@Validated @RestController @RequiredArgsConstructor
@RequestMapping("/api/assessment/past-papers")
public class PastPaperController {
    private final PastPaperService service;

    @GetMapping("/setup") public R<PastPaperSetupVo> setup() { return R.ok(service.setup(LoginHelper.isLogin() ? LoginHelper.getUserId() : null)); }
    @GetMapping public R<PageResult<PastPaperListVo>> list(@RequestParam(required = false) String certificationId,
        @RequestParam(required = false) String subjectId, @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer pageNum, @RequestParam(required = false) Integer pageSize) {
        return R.ok(service.list(LoginHelper.isLogin() ? LoginHelper.getUserId() : null,
            certificationId, subjectId, keyword, pageNum, pageSize));
    }
    @GetMapping("/{collectionId}") public R<PastPaperListVo> detail(@PathVariable long collectionId) { return R.ok(service.detail(collectionId)); }
    @GetMapping("/{collectionId}/preview") public R<PastPaperPreviewVo> preview(@PathVariable long collectionId) { return R.ok(service.preview(collectionId)); }
    @PostMapping("/{collectionId}/items/{questionOrder}/answer-reveal") @SaCheckLogin
    public R<PastPaperRevealVo> reveal(@PathVariable long collectionId, @PathVariable int questionOrder,
        @RequestHeader("X-Request-Id") String requestId) { return R.ok(service.reveal(LoginHelper.getUserId(), collectionId, questionOrder, requestId)); }
    @PostMapping("/{collectionId}/practice-sessions")
    @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    @Log(title = "真题练习", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    public R<StartPastPaperPracticeVo> startPractice(@PathVariable long collectionId, @RequestHeader("X-Request-Id") String requestId,
        @Valid @RequestBody PastPaperStartBo body) { return R.ok(service.startPractice(LoginHelper.getUserId(),collectionId,requestId,body)); }
    @PostMapping("/{collectionId}/exam-sessions")
    @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<PastPaperStartVo> startExam(@PathVariable long collectionId, @RequestHeader("X-Request-Id") String requestId,
        @Valid @RequestBody PastPaperStartBo body) { return R.ok(service.startExam(LoginHelper.getUserId(),collectionId,requestId,body)); }
    @GetMapping("/practice-sessions/{sessionId}") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<PastPaperPracticeSessionVo> practiceSession(@PathVariable long sessionId) { return R.ok(service.practiceSession(LoginHelper.getUserId(),sessionId)); }
    @GetMapping("/exam-sessions/{sessionId}") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<FormalExamSessionVo> examSession(@PathVariable long sessionId) { return R.ok(service.formalSession(LoginHelper.getUserId(),sessionId)); }
    @GetMapping("/practice-sessions/{sessionId}/items/{questionOrder}") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<PastPaperPracticeItemVo> practiceItem(@PathVariable long sessionId,@PathVariable int questionOrder) { return R.ok(service.practiceItem(LoginHelper.getUserId(),sessionId,questionOrder)); }
    @GetMapping("/exam-sessions/{sessionId}/items/{questionOrder}") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<FormalExamItemVo> examItem(@PathVariable long sessionId,@PathVariable int questionOrder) { return R.ok(service.formalItem(LoginHelper.getUserId(),sessionId,questionOrder)); }
    @PostMapping("/practice-sessions/{sessionId}/items/{questionOrder}/submit") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    @Log(title = "真题练习答题", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    public R<SubmitPastPaperPracticeItemVo> submit(@PathVariable long sessionId,@PathVariable int questionOrder,@RequestHeader("X-Request-Id") String requestId,@Valid @RequestBody PastPaperAnswerBo body) { return R.ok(service.submitPractice(LoginHelper.getUserId(),sessionId,questionOrder,requestId,body)); }
    @PostMapping("/practice-sessions/{sessionId}/complete") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    @Log(title = "结束真题练习", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    public R<CompletePastPaperPracticeVo> complete(@PathVariable long sessionId,@RequestHeader("X-Request-Id") String requestId) { return R.ok(service.completePractice(LoginHelper.getUserId(),sessionId,requestId)); }
    @PutMapping("/exam-sessions/{sessionId}/items/{questionOrder}/draft") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<FormalExamSessionVo> draft(@PathVariable long sessionId,@PathVariable int questionOrder,@RequestHeader("X-Request-Id") String requestId,@Valid @RequestBody FormalExamDraftBo body) { return R.ok(service.saveFormalDraft(LoginHelper.getUserId(),sessionId,questionOrder,requestId,body)); }
    @PostMapping("/exam-sessions/{sessionId}/timer-events") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<FormalExamTimerEventVo> timerEvent(@PathVariable long sessionId,@RequestHeader("X-Request-Id") String requestId,@Valid @RequestBody FormalExamTimerEventBo body) { return R.ok(service.formalTimerEvent(LoginHelper.getUserId(),sessionId,requestId,body)); }
    @PostMapping("/exam-sessions/{sessionId}/pause") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<FormalExamSessionVo> pause(@PathVariable long sessionId,@RequestHeader("X-Request-Id") String requestId,@Valid @RequestBody FormalExamPauseBo body) { return R.ok(service.pauseFormal(LoginHelper.getUserId(),sessionId,requestId,body)); }
    @GetMapping("/exam-sessions/{sessionId}/finish-check") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<FormalExamFinishCheckVo> finishCheck(@PathVariable long sessionId) { return R.ok(service.formalFinishCheck(LoginHelper.getUserId(),sessionId)); }
    @PostMapping("/exam-sessions/{sessionId}/finish") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<FormalExamFinishVo> finish(@PathVariable long sessionId,@RequestHeader("X-Request-Id") String requestId,@Valid @RequestBody FormalExamFinishBo body) { return R.ok(service.finishFormal(LoginHelper.getUserId(),sessionId,requestId,body)); }
    @GetMapping("/exam-sessions/{sessionId}/status") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<FormalExamStatusVo> status(@PathVariable long sessionId) { return R.ok(service.formalStatus(LoginHelper.getUserId(),sessionId)); }
    @GetMapping("/exam-sessions/{sessionId}/result") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<FormalExamResultVo> result(@PathVariable long sessionId) { return R.ok(service.formalResult(LoginHelper.getUserId(),sessionId)); }
    @PostMapping("/exam-sessions/{sessionId}/regenerate-result") @SaCheckPermission(value={"certmuse:student","certmuse:assessment:past-paper:answer"},mode=SaMode.AND)
    public R<FormalExamStatusVo> regenerate(@PathVariable long sessionId,@RequestHeader("X-Request-Id") String requestId) { return R.ok(service.regenerateFormal(LoginHelper.getUserId(),sessionId,requestId)); }

    private String sessionType(String mode) {
        return switch (mode) {
            case "practice" -> "past_paper_practice";
            case "exam" -> "past_paper_exam";
            default -> "past_paper_invalid";
        };
    }
}
