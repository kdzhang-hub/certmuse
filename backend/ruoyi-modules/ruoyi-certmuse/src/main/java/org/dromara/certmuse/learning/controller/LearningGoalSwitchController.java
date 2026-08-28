package org.dromara.certmuse.learning.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.learning.domain.bo.SwitchLearningGoalBo;
import org.dromara.certmuse.learning.domain.vo.GoalSwitchOptionsVo;
import org.dromara.certmuse.learning.domain.vo.SwitchLearningGoalResultVo;
import org.dromara.certmuse.learning.service.LearningGoalService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learner HTTP endpoints for switching an active learning goal. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning/goals")
public class LearningGoalSwitchController {
    private final LearningGoalService learningGoalService;

    @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:goal:switch"}, mode = SaMode.AND)
    @GetMapping("/current/switch-options")
    public R<GoalSwitchOptionsVo> options() { return R.ok(learningGoalService.switchOptions(LoginHelper.getUserId())); }

    @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:goal:switch"}, mode = SaMode.AND)
    @Log(title = "学习目标切换", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/switch")
    public R<SwitchLearningGoalResultVo> switchGoal(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                     @Valid @RequestBody SwitchLearningGoalBo command) {
        return R.ok("学习目标已切换", learningGoalService.switchGoal(LoginHelper.getUserId(), requestId, command));
    }
}
