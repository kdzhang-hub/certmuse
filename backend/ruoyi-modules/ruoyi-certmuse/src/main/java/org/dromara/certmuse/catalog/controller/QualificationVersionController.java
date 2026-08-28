package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.bo.QualificationQueryBo;
import org.dromara.certmuse.catalog.domain.bo.QualificationWriteBo;
import org.dromara.certmuse.catalog.domain.bo.SyllabusVersionWriteBo;
import org.dromara.certmuse.catalog.domain.vo.QualificationVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusVersionVo;
import org.dromara.certmuse.catalog.service.QualificationVersionService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrative HTTP entry points defined by M01. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/catalog/certifications")
public class QualificationVersionController {
    private final QualificationVersionService service;

    @SaCheckPermission("certmuse:catalog:subject:list")
    @GetMapping
    public R<PageResult<QualificationVo>> list(@ModelAttribute QualificationQueryBo query) { return R.ok(service.list(query)); }

    @SaCheckPermission("certmuse:catalog:subject:add")
    @Log(title = "资格", businessType = BusinessType.INSERT)
    @PostMapping
    public R<QualificationVo> createQualification(@RequestHeader("X-Request-Id") String requestId,
                                                    @Valid @RequestBody QualificationWriteBo command) {
        return R.ok("资格已新增", service.createQualification(requestId, command));
    }

    @SaCheckPermission("certmuse:catalog:subject:edit")
    @Log(title = "资格", businessType = BusinessType.UPDATE)
    @PutMapping("/{certificationId}")
    public R<QualificationVo> updateQualification(@PathVariable String certificationId,
                                                    @RequestHeader("X-Request-Id") String requestId,
                                                    @Valid @RequestBody QualificationWriteBo command) {
        return R.ok(service.updateQualification(certificationId, requestId, command));
    }

    @SaCheckPermission("certmuse:catalog:subject:remove")
    @Log(title = "资格", businessType = BusinessType.DELETE)
    @DeleteMapping("/{certificationId}")
    public R<Void> deleteQualification(@PathVariable String certificationId, @RequestHeader("X-Request-Id") String requestId) {
        service.deleteQualification(certificationId, requestId); return R.ok();
    }

    @SaCheckPermission("certmuse:catalog:subject:add")
    @Log(title = "考纲版本", businessType = BusinessType.INSERT)
    @PostMapping("/{certificationId}/syllabus-versions")
    public R<SyllabusVersionVo> createVersion(@PathVariable String certificationId,
                                                @RequestHeader("X-Request-Id") String requestId,
                                                @Valid @RequestBody SyllabusVersionWriteBo command) {
        return R.ok(service.createVersion(certificationId, requestId, command));
    }

    @SaCheckPermission("certmuse:catalog:subject:edit")
    @Log(title = "考纲版本", businessType = BusinessType.UPDATE)
    @PutMapping("/{certificationId}/syllabus-versions/{versionId}")
    public R<SyllabusVersionVo> updateVersion(@PathVariable String certificationId, @PathVariable String versionId,
                                                @RequestHeader("X-Request-Id") String requestId,
                                                @Valid @RequestBody SyllabusVersionWriteBo command) {
        return R.ok(service.updateVersion(certificationId, versionId, requestId, command));
    }

    @SaCheckPermission("certmuse:catalog:subject:remove")
    @Log(title = "考纲版本", businessType = BusinessType.DELETE)
    @DeleteMapping("/{certificationId}/syllabus-versions/{versionId}")
    public R<Void> deleteVersion(@PathVariable String certificationId, @PathVariable String versionId,
                                 @RequestHeader("X-Request-Id") String requestId) {
        service.deleteVersion(certificationId, versionId, requestId); return R.ok();
    }
}
