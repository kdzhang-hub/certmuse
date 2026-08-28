package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.bo.CompletedImportBatchQueryBo;
import org.dromara.certmuse.catalog.domain.bo.ImportCreateBo;
import org.dromara.certmuse.catalog.domain.bo.PaperImportCreateBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffBatchResolutionBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffQueryBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffResolutionBo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchDetailVo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchPageVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchFilterOptionsVo;
import org.dromara.certmuse.catalog.domain.vo.ImportContextOptionsVo;
import org.dromara.certmuse.catalog.domain.vo.QuestionImportContextOptionsVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportContextOptionsVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewVo;
import org.dromara.certmuse.catalog.domain.vo.ImportIssueVo;
import org.dromara.certmuse.catalog.domain.vo.ImportProgressVo;
import org.dromara.certmuse.catalog.domain.vo.ImportValidationAcceptedVo;
import org.dromara.certmuse.catalog.domain.vo.PaperImportProgressVo;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeImportDiffPageVo;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeImportDiffResolutionVo;
import org.dromara.common.core.domain.PageResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * Content import use cases.
 */
public interface ImportService {

    CompletedImportBatchPageVo completedBatches(CompletedImportBatchQueryBo query);

    ImportBatchFilterOptionsVo filterOptions();

    CompletedImportBatchDetailVo completedBatch(String id);

    ImportContextOptionsVo contextOptions(String type, String syllabusVersionId, String certificationId);

    default ImportContextOptionsVo contextOptions(String type, String syllabusVersionId) {
        return contextOptions(type, syllabusVersionId, null);
    }

    QuestionImportContextOptionsVo questionContextOptions();

    TextbookImportContextOptionsVo textbookContextOptions(String syllabusVersionId);

    TextbookImportContextOptionsVo textbookContextOptions(String syllabusVersionId, String certificationId);

    ImportBatchVo create(
        MultipartFile file,
        String requestId,
        String importType,
        String certificationId,
        String syllabusVersionId,
        String templateVersion,
        String subjectMappings
    );

    default ImportBatchVo create(
        MultipartFile file, String requestId, String importType, String syllabusVersionId,
        String templateVersion, String subjectMappings
    ) {
        return create(file, requestId, importType, null, syllabusVersionId, templateVersion, subjectMappings);
    }

    ImportBatchVo createQuestion(ImportCreateBo form, String requestId);

    ImportBatchVo createTextbook(ImportCreateBo form, String requestId);

    /** Creates a paper-import batch using the already defined administration form. */
    ImportBatchVo createPaper(PaperImportCreateBo form, String requestId);

    ImportValidationAcceptedVo validate(String id);

    ImportValidationAcceptedVo confirm(String id, String requestId);

    ImportValidationAcceptedVo validatePaper(String id, String requestId);

    ImportValidationAcceptedVo confirmPaper(String id, String requestId);

    PaperImportProgressVo paperProgress(String id);

    /** Returns generated old-to-new knowledge tree differences for manual review. */
    KnowledgeImportDiffPageVo knowledgeDiff(String id, KnowledgeImportDiffQueryBo query);

    /** Applies one explicit manual knowledge point decision. */
    KnowledgeImportDiffResolutionVo resolveKnowledgeDiff(
        String id, String diffId, KnowledgeImportDiffResolutionBo command, String requestId
    );

    /** Atomically applies an explicit batch of manual knowledge point decisions. */
    KnowledgeImportDiffResolutionVo resolveKnowledgeDiffBatch(
        String id, KnowledgeImportDiffBatchResolutionBo command, String requestId
    );

    ImportProgressVo progress(String id);

    TextbookImportPreviewVo preview(String id, Integer pageNum, Integer pageSize);

    PageResult<ImportIssueVo> issues(
        String id,
        Integer pageNum,
        Integer pageSize,
        String severity,
        String issueCode,
        String orderByColumn,
        String isAsc
    );
}
