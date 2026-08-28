package org.dromara.certmuse.learning.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.learning.domain.bo.HistoryExamQueryBo;
import org.dromara.certmuse.learning.domain.bo.HistoryQuestionQueryBo;
import org.dromara.certmuse.learning.domain.bo.HistoryPracticeQueryBo;
import org.dromara.certmuse.learning.domain.bo.HistoryTaskQueryBo;
import org.dromara.certmuse.learning.domain.vo.HistoryExamDetailVo;
import org.dromara.certmuse.learning.domain.vo.HistoryExamPageVo;
import org.dromara.certmuse.learning.domain.vo.HistoryGoalsVo;
import org.dromara.certmuse.learning.domain.vo.HistoryQuestionDetailVo;
import org.dromara.certmuse.learning.domain.vo.HistoryQuestionPageVo;
import org.dromara.certmuse.learning.domain.vo.HistoryPracticeDetailVo;
import org.dromara.certmuse.learning.domain.vo.HistoryPracticePageVo;
import org.dromara.certmuse.learning.domain.vo.HistoryTaskDetailVo;
import org.dromara.certmuse.learning.domain.vo.HistoryTaskPageVo;
import org.dromara.certmuse.learning.service.LearningHistoryService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Student endpoints for immutable learning-history projections. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning/history")
@SaCheckPermission(value = {"certmuse:student", "certmuse:learning:history:query"}, mode = SaMode.AND)
public class LearningHistoryController {
    private final LearningHistoryService service;

    @GetMapping("/goals")
    public R<HistoryGoalsVo> goals() { return R.ok(service.goals(LoginHelper.getUserId())); }

    @GetMapping("/tasks")
    public R<HistoryTaskPageVo> tasks(@ModelAttribute HistoryTaskQueryBo query) {
        return R.ok(service.tasks(LoginHelper.getUserId(), query));
    }

    @GetMapping("/tasks/{taskId}")
    public R<HistoryTaskDetailVo> task(@PathVariable String taskId,
                                       @RequestParam(required = false) String goalId) {
        return R.ok(service.task(LoginHelper.getUserId(), taskId, goalId));
    }

    @GetMapping("/questions")
    public R<HistoryQuestionPageVo> questions(@ModelAttribute HistoryQuestionQueryBo query) {
        return R.ok(service.questions(LoginHelper.getUserId(), query));
    }

    @GetMapping("/questions/{questionId}")
    public R<HistoryQuestionDetailVo> question(@PathVariable String questionId,
                                               @RequestParam(required = false) String goalId) {
        return R.ok(service.question(LoginHelper.getUserId(), questionId, goalId));
    }

    @GetMapping("/practices")
    public R<HistoryPracticePageVo> practices(@ModelAttribute HistoryPracticeQueryBo query) {
        return R.ok(service.practices(LoginHelper.getUserId(), query));
    }

    @GetMapping("/practices/{sessionId}")
    public R<HistoryPracticeDetailVo> practice(@PathVariable String sessionId,
                                               @RequestParam(required = false) String goalId) {
        return R.ok(service.practice(LoginHelper.getUserId(), sessionId, goalId));
    }

    @GetMapping("/exams")
    public R<HistoryExamPageVo> exams(@ModelAttribute HistoryExamQueryBo query) {
        return R.ok(service.exams(LoginHelper.getUserId(), query));
    }

    @GetMapping("/exams/{sessionId}")
    public R<HistoryExamDetailVo> exam(@PathVariable String sessionId,
                                       @RequestParam(required = false) String goalId) {
        return R.ok(service.exam(LoginHelper.getUserId(), sessionId, goalId));
    }
}
