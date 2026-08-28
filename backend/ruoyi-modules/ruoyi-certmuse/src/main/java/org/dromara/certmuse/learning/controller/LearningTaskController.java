package org.dromara.certmuse.learning.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.learning.domain.bo.LearningTaskQueryBo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskLaunchVo;
import org.dromara.certmuse.learning.domain.vo.DailyTaskLearningContentVo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskPageVo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskSupplementVo;
import org.dromara.certmuse.learning.domain.vo.StartDailyTaskPracticeVo;
import org.dromara.certmuse.learning.service.LearningTaskService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learner endpoints for listing, launching and replenishing frozen tasks. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning/tasks")
public class LearningTaskController {
    private final LearningTaskService service;

    @GetMapping
    @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:task:query"}, mode = SaMode.AND)
    public R<LearningTaskPageVo> page(@Valid @ModelAttribute LearningTaskQueryBo query) {
        return R.ok(service.page(LoginHelper.getUserId(), query));
    }

    @PostMapping("/{taskId}/launch")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:task:launch"}, mode = SaMode.AND)
    @Log(title = "启动学习任务", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<LearningTaskLaunchVo> launch(@PathVariable String taskId,
                                          @RequestHeader("X-Request-Id") String requestId) {
        return R.ok(service.launch(LoginHelper.getUserId(), taskId, requestId));
    }

    @GetMapping("/{taskId}/learning-content")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:daily-task:answer"}, mode = SaMode.AND)
    public R<DailyTaskLearningContentVo> learningContent(@PathVariable String taskId) {
        return R.ok(service.learningContent(LoginHelper.getUserId(), taskId));
    }

    @PostMapping("/{taskId}/start-practice")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:daily-task:answer"}, mode = SaMode.AND)
    @Log(title = "开始每日任务练习", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<StartDailyTaskPracticeVo> startPractice(@PathVariable String taskId,
                                                      @RequestHeader("X-Request-Id") String requestId) {
        return R.ok(service.startPractice(LoginHelper.getUserId(), taskId, requestId));
    }

    @PostMapping("/supplement")
    @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:task:supplement"}, mode = SaMode.AND)
    @Log(title = "补充学习任务", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<LearningTaskSupplementVo> supplement(@RequestHeader("X-Request-Id") String requestId) {
        return R.ok(service.supplement(LoginHelper.getUserId(), requestId));
    }
}
