package org.dromara.certmuse.assessment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.bo.SimulationQueryBo;
import org.dromara.certmuse.assessment.domain.bo.StartSimulationSessionBo;
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
import org.dromara.certmuse.assessment.domain.vo.SimulationDetailVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationListItemVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationPreviewVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationSetupVo;
import org.dromara.certmuse.assessment.domain.vo.StartSimulationSessionVo;
import org.dromara.certmuse.assessment.service.SimulationService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

/** Learner simulation setup, list, instructions and gated start endpoints. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assessment/simulations")
public class SimulationController {
    private final SimulationService service;

    @GetMapping("/setup")
    @SaCheckPermission("certmuse:student")
    public R<SimulationSetupVo> setup() {
        return R.ok(service.setup(LoginHelper.getUserId()));
    }

    @GetMapping
    @SaCheckPermission("certmuse:student")
    public R<PageResult<SimulationListItemVo>> list(@Valid @ModelAttribute SimulationQueryBo query) {
        return R.ok(service.list(LoginHelper.getUserId(), query));
    }

    @GetMapping("/{collectionId}")
    @SaCheckPermission("certmuse:student")
    public R<SimulationDetailVo> detail(@PathVariable String collectionId) {
        return R.ok(service.detail(LoginHelper.getUserId(), collectionId));
    }

    @GetMapping("/{collectionId}/preview")
    @SaCheckPermission("certmuse:student")
    public R<SimulationPreviewVo> preview(@PathVariable String collectionId) {
        return R.ok(service.preview(LoginHelper.getUserId(), collectionId));
    }

    @PostMapping("/{collectionId}/sessions")
    @SaCheckPermission("certmuse:student")
    public R<StartSimulationSessionVo> start(@PathVariable String collectionId,
                                             @RequestHeader("X-Request-Id") String requestId,
                                             @Valid @RequestBody StartSimulationSessionBo command) {
        return R.ok(service.start(LoginHelper.getUserId(), collectionId, requestId, command));
    }

    @GetMapping("/sessions/{sessionId}") @SaCheckPermission("certmuse:student")
    public R<FormalExamSessionVo> session(@PathVariable long sessionId) { return R.ok(service.session(LoginHelper.getUserId(), sessionId)); }
    @GetMapping("/sessions/{sessionId}/items/{questionOrder}") @SaCheckPermission("certmuse:student")
    public R<FormalExamItemVo> item(@PathVariable long sessionId,@PathVariable int questionOrder) { return R.ok(service.item(LoginHelper.getUserId(),sessionId,questionOrder)); }
    @PutMapping("/sessions/{sessionId}/items/{questionOrder}/draft") @SaCheckPermission("certmuse:student")
    public R<FormalExamSessionVo> draft(@PathVariable long sessionId,@PathVariable int questionOrder,@RequestHeader("X-Request-Id") String requestId,@Valid @RequestBody FormalExamDraftBo body){return R.ok(service.saveDraft(LoginHelper.getUserId(),sessionId,questionOrder,requestId,body));}
    @PostMapping("/sessions/{sessionId}/timer-events") @SaCheckPermission("certmuse:student")
    public R<FormalExamTimerEventVo> timer(@PathVariable long sessionId,@RequestHeader("X-Request-Id") String requestId,@Valid @RequestBody FormalExamTimerEventBo body){return R.ok(service.timerEvent(LoginHelper.getUserId(),sessionId,requestId,body));}
    @PostMapping("/sessions/{sessionId}/pause") @SaCheckPermission("certmuse:student")
    public R<FormalExamSessionVo> pause(@PathVariable long sessionId,@RequestHeader("X-Request-Id") String requestId,@Valid @RequestBody FormalExamPauseBo body){return R.ok(service.pause(LoginHelper.getUserId(),sessionId,requestId,body));}
    @GetMapping("/sessions/{sessionId}/finish-check") @SaCheckPermission("certmuse:student")
    public R<FormalExamFinishCheckVo> finishCheck(@PathVariable long sessionId){return R.ok(service.finishCheck(LoginHelper.getUserId(),sessionId));}
    @PostMapping("/sessions/{sessionId}/finish") @SaCheckPermission("certmuse:student")
    public R<FormalExamFinishVo> finish(@PathVariable long sessionId,@RequestHeader("X-Request-Id") String requestId,@Valid @RequestBody FormalExamFinishBo body){return R.ok(service.finish(LoginHelper.getUserId(),sessionId,requestId,body));}
    @GetMapping("/sessions/{sessionId}/status") @SaCheckPermission("certmuse:student")
    public R<FormalExamStatusVo> status(@PathVariable long sessionId){return R.ok(service.status(LoginHelper.getUserId(),sessionId));}
    @PostMapping("/sessions/{sessionId}/regenerate-result") @SaCheckPermission("certmuse:student")
    public R<FormalExamStatusVo> regenerate(@PathVariable long sessionId,@RequestHeader("X-Request-Id") String requestId){return R.ok(service.regenerate(LoginHelper.getUserId(),sessionId,requestId));}
    @GetMapping("/sessions/{sessionId}/result") @SaCheckPermission("certmuse:student")
    public R<FormalExamResultVo> result(@PathVariable long sessionId){return R.ok(service.result(LoginHelper.getUserId(),sessionId));}
}
