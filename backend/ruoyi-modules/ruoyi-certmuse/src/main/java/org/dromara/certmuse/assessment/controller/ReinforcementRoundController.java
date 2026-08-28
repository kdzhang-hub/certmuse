package org.dromara.certmuse.assessment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.vo.ReinforcementResultVo;
import org.dromara.certmuse.assessment.domain.vo.ReinforcementRoundVo;
import org.dromara.certmuse.assessment.service.KnowledgePracticeService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Refresh, continuation and result endpoints for reinforcement rounds. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assessment/reinforcement-rounds")
public class ReinforcementRoundController {
    private final KnowledgePracticeService service;

    @GetMapping("/{roundId}")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:answer"}, mode = SaMode.AND)
    public R<ReinforcementRoundVo> round(@PathVariable long roundId) {
        return R.ok(service.reinforcementRound(LoginHelper.getUserId(), roundId));
    }

    @PostMapping("/{roundId}/continue")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:answer"}, mode = SaMode.AND)
    public R<ReinforcementRoundVo> continueRound(@PathVariable long roundId,
                                                 @RequestHeader("X-Request-Id") String requestId) {
        return R.ok(service.continueReinforcement(LoginHelper.getUserId(), roundId, requestId));
    }

    @GetMapping("/{roundId}/result")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:answer"}, mode = SaMode.AND)
    public R<ReinforcementResultVo> result(@PathVariable long roundId) {
        return R.ok(service.reinforcementResult(LoginHelper.getUserId(), roundId));
    }
}
