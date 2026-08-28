package org.dromara.certmuse.assessment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.bo.StartKnowledgePracticeBo;
import org.dromara.certmuse.assessment.domain.bo.SubmitKnowledgePracticeItemBo;
import org.dromara.certmuse.assessment.domain.vo.SubmitKnowledgePracticeItemVo;
import org.dromara.certmuse.assessment.domain.vo.CompleteKnowledgePracticeVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeSetupVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeSessionVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeItemVo;
import org.dromara.certmuse.assessment.domain.vo.StartKnowledgePracticeVo;
import org.dromara.certmuse.assessment.domain.vo.ReinforcementSuggestionVo;
import org.dromara.certmuse.assessment.domain.vo.ReinforcementRoundVo;
import org.dromara.certmuse.assessment.service.KnowledgePracticeService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learner knowledge-practice setup and session creation endpoints. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assessment/knowledge-practices")
public class KnowledgePracticeController {
    private final KnowledgePracticeService service;

    @GetMapping("/setup")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:query"}, mode = SaMode.AND)
    public R<KnowledgePracticeSetupVo> setup() {
        return R.ok(service.setup(LoginHelper.getUserId()));
    }

    @PostMapping
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:start"}, mode = SaMode.AND)
    public R<StartKnowledgePracticeVo> start(@RequestHeader("X-Request-Id") String requestId,
                                             @Valid @RequestBody StartKnowledgePracticeBo command) {
        return R.ok(service.start(LoginHelper.getUserId(), requestId, command));
    }

    @GetMapping("/{sessionId}")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:answer"}, mode = SaMode.AND)
    public R<KnowledgePracticeSessionVo> session(@PathVariable long sessionId) {
        return R.ok(service.session(LoginHelper.getUserId(), sessionId));
    }

    @GetMapping("/{sessionId}/items/{questionOrder}")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:answer"}, mode = SaMode.AND)
    public R<KnowledgePracticeItemVo> item(@PathVariable long sessionId, @PathVariable int questionOrder) {
        return R.ok(service.item(LoginHelper.getUserId(), sessionId, questionOrder));
    }

    @PostMapping("/{sessionId}/items/{questionOrder}/submit")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:answer"}, mode = SaMode.AND)
    public R<SubmitKnowledgePracticeItemVo> submit(@PathVariable long sessionId, @PathVariable int questionOrder,
                                                   @RequestHeader("X-Request-Id") String requestId,
                                                   @Valid @RequestBody SubmitKnowledgePracticeItemBo command) {
        return R.ok(service.submit(LoginHelper.getUserId(), sessionId, questionOrder, requestId, command));
    }

    @PostMapping("/{sessionId}/complete")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:answer"}, mode = SaMode.AND)
    public R<CompleteKnowledgePracticeVo> complete(@PathVariable long sessionId,
                                                   @RequestHeader("X-Request-Id") String requestId) {
        return R.ok(service.complete(LoginHelper.getUserId(), sessionId, requestId));
    }

    @GetMapping("/{sessionId}/items/{questionOrder}/reinforcement")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:answer"}, mode = SaMode.AND)
    public R<ReinforcementSuggestionVo> reinforcement(@PathVariable long sessionId,
                                                       @PathVariable int questionOrder) {
        return R.ok(service.reinforcement(LoginHelper.getUserId(), sessionId, questionOrder));
    }

    @PostMapping("/{sessionId}/items/{questionOrder}/reinforcement/dismiss")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:answer"}, mode = SaMode.AND)
    public R<Void> dismissReinforcement(@PathVariable long sessionId, @PathVariable int questionOrder) {
        service.dismissReinforcement(LoginHelper.getUserId(), sessionId, questionOrder);
        return R.ok();
    }

    @PostMapping("/{sessionId}/items/{questionOrder}/reinforcement/rounds")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:answer"}, mode = SaMode.AND)
    public R<ReinforcementRoundVo> createReinforcement(@PathVariable long sessionId,
                                                        @PathVariable int questionOrder,
                                                        @RequestHeader("X-Request-Id") String requestId) {
        return R.ok(service.createReinforcement(LoginHelper.getUserId(), sessionId, questionOrder, requestId));
    }

}
