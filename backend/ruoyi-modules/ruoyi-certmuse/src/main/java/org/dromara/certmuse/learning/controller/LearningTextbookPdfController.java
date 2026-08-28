package org.dromara.certmuse.learning.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.vo.LearningTextbookPdfListItemVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookPdfReaderVo;
import org.dromara.certmuse.catalog.service.TextbookPdfService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Learner access to published textbook original PDFs. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning/textbooks")
public class LearningTextbookPdfController {
    private final TextbookPdfService service;

    @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:textbook:query"}, mode = SaMode.AND)
    @GetMapping
    public R<List<LearningTextbookPdfListItemVo>> list(@RequestParam(required = false) String keyword) {
        return R.ok(service.learnerList(LoginHelper.getUserId(), keyword));
    }

    @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:textbook:query"}, mode = SaMode.AND)
    @GetMapping("/{textbookId}/reader")
    public R<TextbookPdfReaderVo> reader(@PathVariable String textbookId) {
        return R.ok(service.learnerReader(LoginHelper.getUserId(), textbookId));
    }
}
