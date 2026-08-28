package org.dromara.certmuse.profile.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.profile.domain.vo.OverallScoreVo;
import org.dromara.certmuse.profile.service.LearningProfileQueryService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learner endpoints for the current learning profile. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning/profile")
public class LearningProfileController {
    private final LearningProfileQueryService learningProfileQueryService;

    /** Returns the current learner's persisted overall score without recalculation. */
    @GetMapping("/overall-score")
    @SaCheckPermission(value = {
        "certmuse:student", "certmuse:profile:overall-score:query"
    }, mode = SaMode.AND)
    public ResponseEntity<R<OverallScoreVo>> overallScore() {
        R<OverallScoreVo> response = R.ok(
            learningProfileQueryService.overallScore(LoginHelper.getUserId()));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(response);
    }
}
