package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.vo.TextbookPdfInfoVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookPdfReaderVo;
import org.dromara.certmuse.catalog.service.TextbookPdfService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Additive administrator API for textbook original-PDF attachments. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/catalog/textbooks")
public class TextbookPdfAdminController {
    private final TextbookPdfService service;

    @SaCheckPermission("certmuse:catalog:resource:query")
    @GetMapping("/{textbookId}/original-pdf")
    public R<TextbookPdfInfoVo> info(@PathVariable String textbookId) { return R.ok(service.adminInfo(textbookId)); }

    @SaCheckPermission("certmuse:catalog:resource:edit")
    @Log(title = "教材原始PDF", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{textbookId}/original-pdf")
    public R<TextbookPdfInfoVo> upload(@PathVariable String textbookId, @RequestPart("file") MultipartFile file) {
        return R.ok(service.upload(textbookId, file));
    }

    @SaCheckPermission("certmuse:catalog:resource:edit")
    @Log(title = "教材原始PDF", businessType = BusinessType.DELETE, isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/{textbookId}/original-pdf")
    public R<Void> delete(@PathVariable String textbookId) {
        service.delete(textbookId);
        return R.ok();
    }

    @SaCheckPermission("certmuse:catalog:resource:query")
    @GetMapping("/{textbookId}/original-pdf/reader")
    public R<TextbookPdfReaderVo> reader(@PathVariable String textbookId) { return R.ok(service.adminReader(textbookId)); }
}
