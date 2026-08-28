package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.bo.SyllabusPublishedDateUpdateBo;
import org.dromara.certmuse.catalog.service.KnowledgeTreeService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dedicated controller keeps this write API's validation boundary isolated from catalogue queries. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/catalog/syllabus-versions")
public class SyllabusPublishedDateController {
    private final KnowledgeTreeService service;

    @SaCheckPermission("certmuse:catalog:knowledge:edit")
    @Log(title = "考纲发布日期", businessType = BusinessType.UPDATE)
    @PutMapping("/{syllabusVersionId}/published-date")
    public R<Void> updateSyllabusPublishedDate(@PathVariable String syllabusVersionId,
                                                @RequestHeader("X-Request-Id") String requestId,
                                                @Valid @RequestBody SyllabusPublishedDateUpdateBo command) {
        service.updateSyllabusPublishedDate(syllabusVersionId, requestId, command);
        return R.ok();
    }
}
