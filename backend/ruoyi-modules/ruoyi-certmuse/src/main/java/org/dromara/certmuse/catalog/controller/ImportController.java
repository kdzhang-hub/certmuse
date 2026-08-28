package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.bo.CompletedImportBatchQueryBo;
import org.dromara.certmuse.catalog.domain.bo.ImportCreateBo;
import org.dromara.certmuse.catalog.domain.bo.PaperImportCreateBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffBatchResolutionBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffQueryBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffResolutionBo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchDetailVo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchPageVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchFilterOptionsVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchVo;
import org.dromara.certmuse.catalog.domain.vo.ImportIssueVo;
import org.dromara.certmuse.catalog.domain.vo.ImportProgressVo;
import org.dromara.certmuse.catalog.domain.vo.ImportValidationAcceptedVo;
import org.dromara.certmuse.catalog.domain.vo.PaperImportProgressVo;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeImportDiffPageVo;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeImportDiffResolutionVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewVo;
import org.dromara.certmuse.catalog.service.ImportService;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative endpoints for content imports.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class ImportController {

    private final ImportService service;

    @SaCheckPermission("certmuse:catalog:import")
    @GetMapping(value = "/imports/options", params = "type")
    public R<?> contextOptions(
        @RequestParam String type,
        @RequestParam(required = false) String syllabusVersionId,
        @RequestParam(required = false) String certificationId
    ) {
        if ("question".equals(type)) {
            return R.ok(service.questionContextOptions());
        }
        if ("textbook".equals(type)) {
            return R.ok(service.textbookContextOptions(syllabusVersionId, certificationId));
        }
        if ("knowledge_point".equals(type)) {
            return R.ok(certificationId == null
                ? service.contextOptions(type, syllabusVersionId)
                : service.contextOptions(type, syllabusVersionId, certificationId));
        }
        throw new ImportException(400, "IMPORT_CONTEXT_INVALID", "导入上下文类型不支持");
    }

    @SaCheckPermission("certmuse:catalog:import")
    @GetMapping(value = "/imports/options", params = "!type")
    public R<ImportBatchFilterOptionsVo> filterOptions() {
        return R.ok(service.filterOptions());
    }

    @SaCheckPermission("certmuse:catalog:import")
    @GetMapping("/imports")
    public R<CompletedImportBatchPageVo> completedBatches(
        @Validated CompletedImportBatchQueryBo query
    ) {
        return R.ok(service.completedBatches(query));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @GetMapping("/imports/{id}")
    public R<CompletedImportBatchDetailVo> completedBatch(@PathVariable String id) {
        return R.ok(service.completedBatch(id));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @Log(title = "内容导入批次", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportBatchVo> create(
        @ModelAttribute ImportCreateBo form,
        @RequestHeader("X-Request-Id") String requestId
    ) {
        if ("question".equals(form.getImportType())) {
            return R.ok(service.createQuestion(form, requestId));
        }
        if ("document_chunk".equals(form.getImportType())) {
            return R.ok(service.createTextbook(form, requestId));
        }
        if ("knowledge_point".equals(form.getImportType())) {
            return R.ok(
                form.getCertificationId() == null
                    ? service.create(form.getFile(), requestId, form.getImportType(), form.getSyllabusVersionId(), form.getTemplateVersion(), form.getSubjectMappings())
                    : service.create(form.getFile(), requestId, form.getImportType(), form.getCertificationId(), form.getSyllabusVersionId(), form.getTemplateVersion(), form.getSubjectMappings())
            );
        }
        throw new ImportException(400, "IMPORT_CONTEXT_INVALID", "导入类型不支持");
    }

    @SaCheckPermission("certmuse:catalog:import")
    @Log(title = "内容导入预检", businessType = BusinessType.OTHER)
    @PostMapping("/imports/{id}/validate")
    public R<ImportValidationAcceptedVo> validate(@PathVariable String id) {
        return R.ok("预检任务已受理", service.validate(id));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @Log(title = "内容确认导入", businessType = BusinessType.IMPORT)
    @PostMapping("/imports/{id}/confirm")
    public R<ImportValidationAcceptedVo> confirm(
        @PathVariable String id,
        @RequestHeader("X-Request-Id") String requestId
    ) {
        return R.ok("确认导入任务已受理", service.confirm(id, requestId));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @GetMapping("/imports/{id}/progress")
    public R<ImportProgressVo> progress(@PathVariable String id) {
        return R.ok(service.progress(id));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @GetMapping("/imports/{id}/knowledge-diff")
    public R<KnowledgeImportDiffPageVo> knowledgeDiff(
        @PathVariable String id,
        @Validated KnowledgeImportDiffQueryBo query
    ) {
        return R.ok(service.knowledgeDiff(id, query));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @Log(
        title = "知识点导入差异确认",
        businessType = BusinessType.UPDATE,
        isSaveRequestData = false,
        isSaveResponseData = false
    )
    @PutMapping("/imports/{id}/knowledge-diff/batch")
    public R<KnowledgeImportDiffResolutionVo> resolveKnowledgeDiffBatch(
        @PathVariable String id,
        @Validated @RequestBody KnowledgeImportDiffBatchResolutionBo command,
        @RequestHeader("X-Request-Id") String requestId
    ) {
        return R.ok(service.resolveKnowledgeDiffBatch(id, command, requestId));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @Log(
        title = "知识点导入差异确认",
        businessType = BusinessType.UPDATE,
        isSaveRequestData = false,
        isSaveResponseData = false
    )
    @PutMapping("/imports/{id}/knowledge-diff/{diffId}")
    public R<KnowledgeImportDiffResolutionVo> resolveKnowledgeDiff(
        @PathVariable String id,
        @PathVariable String diffId,
        @Validated @RequestBody KnowledgeImportDiffResolutionBo command,
        @RequestHeader("X-Request-Id") String requestId
    ) {
        return R.ok(service.resolveKnowledgeDiff(id, diffId, command, requestId));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @GetMapping("/imports/{id}/preview")
    public R<TextbookImportPreviewVo> preview(
        @PathVariable String id,
        @RequestParam(required = false) Integer pageNum,
        @RequestParam(required = false) Integer pageSize
    ) {
        return R.ok(service.preview(id, pageNum, pageSize));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @GetMapping("/imports/{id}/issues")
    public R<PageResult<ImportIssueVo>> issues(
        @PathVariable String id,
        @RequestParam(required = false) Integer pageNum,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String issueCode,
        @RequestParam(required = false) String orderByColumn,
        @RequestParam(required = false) String isAsc
    ) {
        return R.ok(service.issues(id, pageNum, pageSize, severity, issueCode, orderByColumn, isAsc));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @Log(title = "试卷导入批次", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping(value = "/paper-imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportBatchVo> createPaper(
        @Validated @ModelAttribute PaperImportCreateBo form,
        @RequestHeader("X-Request-Id") String requestId
    ) {
        return R.ok(service.createPaper(form, requestId));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @Log(title = "试卷导入预检", businessType = BusinessType.OTHER, isSaveRequestData = false)
    @PostMapping("/paper-imports/{id}/validate")
    public R<ImportValidationAcceptedVo> validatePaper(
        @PathVariable String id,
        @RequestHeader("X-Request-Id") String requestId
    ) {
        return R.ok("预检任务已受理", service.validatePaper(id, requestId));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @Log(title = "试卷确认导入", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    @PostMapping("/paper-imports/{id}/confirm")
    public R<ImportValidationAcceptedVo> confirmPaper(
        @PathVariable String id,
        @RequestHeader("X-Request-Id") String requestId
    ) {
        return R.ok("确认导入任务已受理", service.confirmPaper(id, requestId));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @GetMapping("/paper-imports/{id}/progress")
    public R<PaperImportProgressVo> paperProgress(@PathVariable String id) {
        return R.ok(service.paperProgress(id));
    }

    @SaCheckPermission("certmuse:catalog:import")
    @GetMapping("/paper-imports/{id}/issues")
    public R<PageResult<ImportIssueVo>> paperIssues(
        @PathVariable String id,
        @RequestParam(required = false) Integer pageNum,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String issueCode
    ) {
        return R.ok(service.issues(id, pageNum, pageSize, severity, issueCode, null, null));
    }
}
