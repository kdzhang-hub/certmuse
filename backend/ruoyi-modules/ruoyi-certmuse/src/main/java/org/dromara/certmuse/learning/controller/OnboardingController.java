package org.dromara.certmuse.learning.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.learning.domain.vo.OnboardingStatusVo;
import org.dromara.certmuse.learning.service.OnboardingService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learner-only initial-flow state endpoint. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @SaCheckPermission("certmuse:learning:onboarding:query")
    @GetMapping("/status")
    public R<OnboardingStatusVo> status() {
        return R.ok(onboardingService.status(LoginHelper.getUserId()));
    }
}
