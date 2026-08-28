package org.dromara.certmuse.learning.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.learning.domain.bo.CreateLearningGoalBo;
import org.dromara.certmuse.learning.domain.vo.CreateLearningGoalResultVo;
import org.dromara.certmuse.learning.domain.vo.LearningGoalOptionsVo;
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

/** Learner HTTP endpoints for first learning-goal setup. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning/goals")
public class LearningGoalController {
    private final LearningGoalService learningGoalService;

    /** Returns server-authoritative choices for the first-goal form. */
    @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:onboarding:query"}, mode = SaMode.AND)
    @GetMapping("/options")
    public R<LearningGoalOptionsVo> options() {
        return R.ok(learningGoalService.options(LoginHelper.getUserId()));
    }

    /** Creates the learner's first active goal without creating a diagnostic session. */
    @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:goal:create"}, mode = SaMode.AND)
    @Log(title = "学习目标", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public R<CreateLearningGoalResultVo> create(@RequestHeader("X-Request-Id") String requestId,
                                                 @Valid @RequestBody CreateLearningGoalBo command) {
        return R.ok("学习目标已保存", learningGoalService.create(LoginHelper.getUserId(), requestId, command));
    }
}
