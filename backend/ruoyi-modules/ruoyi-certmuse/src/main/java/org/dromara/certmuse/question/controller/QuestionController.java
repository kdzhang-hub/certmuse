package org.dromara.certmuse.question.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.question.domain.bo.QuestionQueryBo;
import org.dromara.certmuse.question.domain.bo.QuestionSaveBo;
import org.dromara.certmuse.question.domain.vo.QuestionDetailVo;
import org.dromara.certmuse.question.domain.vo.QuestionListVo;
import org.dromara.certmuse.question.domain.vo.QuestionPreviewVo;
import org.dromara.certmuse.question.domain.vo.QuestionSaveResultVo;
import org.dromara.certmuse.question.service.QuestionService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/questions")
public class QuestionController {
    private final QuestionService service;

    @SaCheckPermission("certmuse:question:list")
    @GetMapping
    public R<PageResult<QuestionListVo>> list(@ModelAttribute QuestionQueryBo query) {
        return R.ok(service.list(query));
    }

    @SaCheckPermission("certmuse:question:list")
    @GetMapping("/{questionId}")
    public R<QuestionDetailVo> detail(@PathVariable String questionId,
                                     @RequestParam(required = false) String revisionId) {
        return R.ok(service.detail(questionId, revisionId));
    }

    @SaCheckPermission("certmuse:question:edit")
    @Log(
        title = "题目草稿",
        businessType = BusinessType.UPDATE,
        isSaveRequestData = false,
        isSaveResponseData = false
    )
    @PutMapping("/{questionId}")
    public R<QuestionSaveResultVo> save(@PathVariable String questionId,
                                       @RequestHeader("X-Request-Id") String requestId,
                                       @Valid @RequestBody QuestionSaveBo command) {
        return R.ok("题目草稿已保存", service.save(questionId, requestId, command));
    }

    @SaCheckPermission("certmuse:question:preview")
    @GetMapping("/{questionId}/preview")
    public R<QuestionPreviewVo> preview(@PathVariable String questionId,
                                       @RequestParam String revisionId) {
        return R.ok(service.preview(questionId, revisionId));
    }

    @SaCheckPermission("certmuse:question:remove")
    @Log(title = "题目", businessType = BusinessType.DELETE)
    @DeleteMapping("/{questionId}")
    public R<Void> delete(@PathVariable String questionId,
                          @RequestHeader("X-Request-Id") String requestId) {
        service.delete(questionId, requestId);
        return R.ok("题目已删除");
    }
}
