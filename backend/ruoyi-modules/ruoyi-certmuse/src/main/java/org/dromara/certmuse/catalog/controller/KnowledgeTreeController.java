package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeTreeVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusListVo;
import org.dromara.certmuse.catalog.service.KnowledgeTreeService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/catalog/syllabus-versions")
public class KnowledgeTreeController {
    private final KnowledgeTreeService service;

    @SaCheckPermission("certmuse:catalog:knowledge:list")
    @GetMapping
    public R<PageResult<SyllabusListVo>> syllabuses(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String certificationId,
                                                    @RequestParam(required = false) String versionName,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Integer pageNum,
                                                    @RequestParam(required = false) Integer pageSize) {
        return R.ok(service.syllabuses(keyword, certificationId, versionName, status, pageNum, pageSize));
    }

    @SaCheckPermission("certmuse:catalog:knowledge:list")
    @GetMapping("/{syllabusVersionId}/knowledge-tree")
    public R<KnowledgeTreeVo> knowledgeTree(@PathVariable String syllabusVersionId) {
        return R.ok(service.knowledgeTree(syllabusVersionId));
    }

    @SaCheckPermission("certmuse:catalog:knowledge:remove")
    @Log(title = "知识点树", businessType = BusinessType.DELETE)
    @DeleteMapping("/{syllabusVersionId}")
    public R<Void> deleteSyllabusVersion(@PathVariable String syllabusVersionId) {
        service.deleteKnowledgeTree(syllabusVersionId);
        return R.ok();
    }
}
