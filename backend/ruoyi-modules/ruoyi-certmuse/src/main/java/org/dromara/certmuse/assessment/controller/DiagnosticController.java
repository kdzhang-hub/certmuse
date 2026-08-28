package org.dromara.certmuse.assessment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticDraftBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticFinishBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticPauseBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticStartBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticTimerEventBo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticDraftVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticFinishCheckVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticFinishVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticItemVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticPreflightVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticReportVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticSessionVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticStartVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticStatusVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticTimerEventVo;
import org.dromara.certmuse.assessment.service.DiagnosticService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Frozen U04-U06 learner diagnostic HTTP contract. */
@Validated @RestController @RequiredArgsConstructor
@RequestMapping("/api/assessment/diagnostics")
public class DiagnosticController {
    private final DiagnosticService service;
    private Long userId() { return LoginHelper.getUserId(); }
    @SaCheckPermission("certmuse:learning:diagnostic") @GetMapping("/preflight")
    public R<DiagnosticPreflightVo> preflight() { return R.ok(service.preflight(userId())); }
    @SaCheckPermission("certmuse:learning:diagnostic") @PostMapping
    public R<DiagnosticStartVo> start(@RequestHeader("X-Request-Id") String id, @Valid @RequestBody DiagnosticStartBo body) { return R.ok(service.start(userId(), id, body)); }
    @SaCheckPermission("certmuse:learning:diagnostic") @GetMapping("/{sessionId}")
    public R<DiagnosticSessionVo> session(@PathVariable long sessionId) { return R.ok(service.session(userId(), sessionId)); }
    @SaCheckPermission("certmuse:learning:diagnostic") @GetMapping("/{sessionId}/items/{questionOrder}")
    public R<DiagnosticItemVo> item(@PathVariable long sessionId, @PathVariable int questionOrder) { return R.ok(service.item(userId(), sessionId, questionOrder)); }
    @SaCheckPermission("certmuse:learning:diagnostic") @PostMapping("/{sessionId}/timer-events")
    public R<DiagnosticTimerEventVo> timerEvent(@PathVariable long sessionId, @RequestHeader("X-Request-Id") String id,
                                                 @Valid @RequestBody DiagnosticTimerEventBo body) {
        return R.ok(service.timerEvent(userId(), sessionId, id, body));
    }
    @SaCheckPermission("certmuse:learning:diagnostic") @PutMapping("/{sessionId}/items/{questionOrder}/draft")
    public R<DiagnosticDraftVo> draft(@PathVariable long sessionId, @PathVariable int questionOrder, @RequestHeader("X-Request-Id") String id, @Valid @RequestBody DiagnosticDraftBo body) { return R.ok(service.saveDraft(userId(), sessionId, questionOrder, id, body)); }
    @SaCheckPermission("certmuse:learning:diagnostic") @PostMapping("/{sessionId}/pause")
    public R<DiagnosticSessionVo> pause(@PathVariable long sessionId, @RequestHeader("X-Request-Id") String id, @Valid @RequestBody DiagnosticPauseBo body) { return R.ok(service.pause(userId(), sessionId, id, body)); }
    @SaCheckPermission("certmuse:learning:diagnostic") @GetMapping("/{sessionId}/finish-check")
    public R<DiagnosticFinishCheckVo> finishCheck(@PathVariable long sessionId) { return R.ok(service.finishCheck(userId(), sessionId)); }
    @SaCheckPermission("certmuse:learning:diagnostic") @PostMapping("/{sessionId}/finish")
    public R<DiagnosticFinishVo> finish(@PathVariable long sessionId, @RequestHeader("X-Request-Id") String id, @Valid @RequestBody DiagnosticFinishBo body) { return R.ok(service.finish(userId(), sessionId, id, body)); }
    @SaCheckPermission("certmuse:learning:diagnostic") @GetMapping("/{sessionId}/status")
    public R<DiagnosticStatusVo> status(@PathVariable long sessionId) { return R.ok(service.status(userId(), sessionId)); }
    @SaCheckPermission("certmuse:learning:diagnostic") @PostMapping("/{sessionId}/regenerate-result")
    public R<DiagnosticStatusVo> regenerate(@PathVariable long sessionId, @RequestHeader("X-Request-Id") String id) { return R.ok(service.regenerate(userId(), sessionId, id)); }
    @SaCheckPermission("certmuse:learning:diagnostic") @GetMapping("/{sessionId}/report")
    public R<DiagnosticReportVo> report(@PathVariable long sessionId) { return R.ok(service.report(userId(), sessionId)); }
}
