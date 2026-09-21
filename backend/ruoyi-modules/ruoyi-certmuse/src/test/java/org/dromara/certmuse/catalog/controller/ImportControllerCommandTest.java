package org.dromara.certmuse.catalog.controller;

import org.dromara.certmuse.catalog.domain.bo.ImportCreateBo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchVo;
import org.dromara.certmuse.catalog.domain.vo.ImportProgressVo;
import org.dromara.certmuse.catalog.domain.vo.ImportValidationAcceptedVo;
import org.dromara.certmuse.catalog.domain.vo.QuestionImportContextOptionsVo;
import org.dromara.certmuse.catalog.service.ImportService;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class ImportControllerCommandTest {

    @Test
    void validRoutesKnowledgeCommandsAndQueriesToTheService() {
        ImportService service = mock(ImportService.class);
        ImportController controller = new ImportController(service);
        ImportCreateBo form = knowledgeForm();
        ImportBatchVo batch = mock(ImportBatchVo.class);
        ImportValidationAcceptedVo accepted = mock(ImportValidationAcceptedVo.class);
        ImportProgressVo progress = mock(ImportProgressVo.class);
        when(service.create(form.getFile(), "r1", "knowledge_point", "21", "knowledge_point/1.0", "[]")).thenReturn(batch);
        when(service.validate("101")).thenReturn(accepted);
        when(service.confirm("101", "r2")).thenReturn(accepted);
        when(service.progress("101")).thenReturn(progress);
        when(service.issues("101", 1, 20, "error", "E1", "lineNo", "asc")).thenReturn(PageResult.build(List.of(), 0));

        assertThat(controller.create(form, "r1").getData()).isSameAs(batch);
        assertThat(controller.validate("101").getData()).isSameAs(accepted);
        assertThat(controller.confirm("101", "r2").getData()).isSameAs(accepted);
        assertThat(controller.progress("101").getData()).isSameAs(progress);
        assertThat(controller.issues("101", 1, 20, "error", "E1", "lineNo", "asc").getData().getRows()).isEmpty();
    }

    @Test
    void invalidPreservesServiceBusinessErrorsForGlobalExceptionHandling() {
        ImportService service = mock(ImportService.class);
        ImportController controller = new ImportController(service);
        when(service.validate("missing")).thenThrow(new ImportException(404, "IMPORT_BATCH_NOT_FOUND", "批次不存在"));

        assertThatThrownBy(() -> controller.validate("missing"))
            .isInstanceOf(ImportException.class)
            .extracting(error -> ((ImportException) error).code())
            .isEqualTo(404);
    }

    @Test
    void edgeRoutesQuestionCreationAndQuestionOptionsWithoutKnowledgeOnlyFields() {
        ImportService service = mock(ImportService.class);
        ImportController controller = new ImportController(service);
        ImportCreateBo form = new ImportCreateBo();
        form.setImportType("question");
        form.setFile(new MockMultipartFile("file", "questions.zip", "application/zip", new byte[]{1}));
        ImportBatchVo batch = mock(ImportBatchVo.class);
        QuestionImportContextOptionsVo options = new QuestionImportContextOptionsVo(List.of());
        when(service.createQuestion(form, "q1")).thenReturn(batch);
        when(service.questionContextOptions()).thenReturn(options);

        assertThat(controller.create(form, "q1").getData()).isSameAs(batch);
        assertThat(controller.contextOptions("question", null, null).getData()).isSameAs(options);
        verify(service).createQuestion(form, "q1");
        verify(service).questionContextOptions();
    }

    @Test
    void delegatesEveryRemainingImportQueryAndPaperLifecycleRoute() {
        ImportService service = mock(ImportService.class);
        ImportController controller = new ImportController(service);

        controller.contextOptions("textbook", "21", "11");
        controller.contextOptions("knowledge_point", "21", null);
        controller.filterOptions();
        controller.completedBatches(mock(org.dromara.certmuse.catalog.domain.bo.CompletedImportBatchQueryBo.class));
        controller.completedBatch("101");

        ImportCreateBo textbook = new ImportCreateBo();
        textbook.setImportType("document_chunk");
        textbook.setFile(new MockMultipartFile("file", "book.jsonl", "application/jsonl", new byte[]{1}));
        controller.create(textbook, "textbook-request");
        controller.knowledgeDiff("101", mock(org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffQueryBo.class));
        controller.resolveKnowledgeDiffBatch("101",
            mock(org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffBatchResolutionBo.class), "diff-batch-request");
        controller.resolveKnowledgeDiff("101", "201",
            mock(org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffResolutionBo.class), "diff-request");
        controller.preview("101", 2, 20);

        org.dromara.certmuse.catalog.domain.bo.PaperImportCreateBo paper = mock(
            org.dromara.certmuse.catalog.domain.bo.PaperImportCreateBo.class);
        controller.createPaper(paper, "paper-request");
        controller.validatePaper("701", "paper-validate-request");
        controller.confirmPaper("701", "paper-confirm-request");
        controller.paperProgress("701");
        controller.paperIssues("701", 1, 20, "error", "E1");

        verify(service).textbookContextOptions("21", "11");
        verify(service).contextOptions("knowledge_point", "21");
        verify(service).createTextbook(textbook, "textbook-request");
        verify(service).createPaper(paper, "paper-request");
        verify(service).validatePaper("701", "paper-validate-request");
        verify(service).confirmPaper("701", "paper-confirm-request");
        verify(service).paperProgress("701");
        verify(service).issues("701", 1, 20, "error", "E1", null, null);
    }

    @Test
    void doesNotAuditUploadedFileContent() throws Exception {
        Method method = ImportController.class.getMethod("create", ImportCreateBo.class, String.class);

        Log log = method.getAnnotation(Log.class);

        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isTrue();
    }

    private static ImportCreateBo knowledgeForm() {
        ImportCreateBo form = new ImportCreateBo();
        form.setImportType("knowledge_point");
        form.setFile(new MockMultipartFile("file", "tree.jsonl", "application/jsonl", "{}".getBytes()));
        form.setSyllabusVersionId("21");
        form.setTemplateVersion("knowledge_point/1.0");
        form.setSubjectMappings("[]");
        return form;
    }
}
