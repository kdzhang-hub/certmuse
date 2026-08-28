package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.vo.PublicTextbookCertificationVo;
import org.dromara.certmuse.catalog.domain.vo.PublicTextbookPdfListItemVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookPdfReaderVo;
import org.dromara.certmuse.catalog.service.TextbookPdfService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous read-only catalogue for published textbook original PDFs. */
@SaIgnore
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/textbooks")
public class PublicTextbookPdfController {
    private final TextbookPdfService service;

    @GetMapping("/certifications")
    public R<List<PublicTextbookCertificationVo>> certifications() {
        return R.ok(service.publicCertifications());
    }

    @GetMapping
    public R<List<PublicTextbookPdfListItemVo>> list(@RequestParam String certificationId,
                                                      @RequestParam(required = false) String keyword) {
        return R.ok(service.publicList(certificationId, keyword));
    }

    @GetMapping("/{textbookId}/reader")
    public R<TextbookPdfReaderVo> reader(@PathVariable String textbookId) {
        return R.ok(service.publicReader(textbookId));
    }
}
