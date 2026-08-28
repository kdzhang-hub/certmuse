package org.dromara.certmuse.assessment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.bo.SubmitDailyTaskItemBo;
import org.dromara.certmuse.assessment.domain.vo.CompleteDailyTaskVo;
import org.dromara.certmuse.assessment.domain.vo.DailyTaskItemVo;
import org.dromara.certmuse.assessment.domain.vo.DailyTaskSessionVo;
import org.dromara.certmuse.assessment.domain.vo.SubmitDailyTaskItemVo;
import org.dromara.certmuse.assessment.service.DailyTaskService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** U11 daily-task answering endpoints. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assessment/daily-tasks")
public class DailyTaskController {
    private final DailyTaskService service;

    @GetMapping("/{sessionId}")
    @SaCheckPermission(value={"certmuse:student","certmuse:assessment:daily-task:answer"}, mode=SaMode.AND)
    public R<DailyTaskSessionVo> session(@PathVariable String sessionId) {
        return R.ok(service.session(LoginHelper.getUserId(), sessionId));
    }

    @GetMapping("/{sessionId}/items/{questionOrder}")
    @SaCheckPermission(value={"certmuse:student","certmuse:assessment:daily-task:answer"}, mode=SaMode.AND)
    public R<DailyTaskItemVo> item(@PathVariable String sessionId, @PathVariable int questionOrder) {
        return R.ok(service.item(LoginHelper.getUserId(), sessionId, questionOrder));
    }

    @PostMapping("/{sessionId}/items/{questionOrder}/submit")
    @SaCheckPermission(value={"certmuse:student","certmuse:assessment:daily-task:answer"}, mode=SaMode.AND)
    @Log(title="提交每日任务答案", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public R<SubmitDailyTaskItemVo> submit(@PathVariable String sessionId, @PathVariable int questionOrder,
        @RequestHeader("X-Request-Id") String requestId, @Valid @RequestBody SubmitDailyTaskItemBo command) {
        return R.ok(service.submit(LoginHelper.getUserId(), sessionId, questionOrder, requestId, command));
    }

    @PostMapping("/{sessionId}/complete")
    @SaCheckPermission(value={"certmuse:student","certmuse:assessment:daily-task:answer"}, mode=SaMode.AND)
    @Log(title="完成每日任务", businessType=BusinessType.OTHER, isSaveRequestData=false, isSaveResponseData=false)
    public R<CompleteDailyTaskVo> complete(@PathVariable String sessionId,
        @RequestHeader("X-Request-Id") String requestId) {
        return R.ok(service.complete(LoginHelper.getUserId(), sessionId, requestId));
    }
}
