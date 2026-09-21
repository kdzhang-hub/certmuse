package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.certmuse.catalog.domain.bo.ImportCreateBo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchVo;
import org.dromara.certmuse.catalog.domain.vo.ImportIssueVo;
import org.dromara.certmuse.catalog.domain.vo.ImportProgressVo;
import org.dromara.certmuse.catalog.domain.vo.ImportValidationAcceptedVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportContextOptionsVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewVo;
import org.dromara.certmuse.catalog.service.ImportService;
import org.dromara.common.core.domain.PageResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class TextbookImportControllerTest {

    @Test
    void bindsTextbookMultipartFormAndReturnsBatchResponse() throws Exception {
        ImportService service = mock(ImportService.class);
        ImportBatchVo batch = new ImportBatchVo(
            "101", "201", "document_chunk", "create", "301", null, null, List.of(),
            "document_chunk/1.0", "uploaded", false,
            OffsetDateTime.parse("2026-08-04T10:00:00+08:00")
        );
        when(service.createTextbook(any(ImportCreateBo.class), eq("550e8400-e29b-41d4-a716-446655440000")))
            .thenReturn(batch);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ImportController(service)).build();

        mvc.perform(multipart("/api/admin/imports")
                .file(new MockMultipartFile(
                    "file", "textbook.jsonl", "application/x-ndjson", "{\"chunk_no\":1}".getBytes()
                ))
                .param("importType", "document_chunk")
                .param("templateVersion", "document_chunk/1.0")
                .param("mode", "create")
                .param("syllabusVersionId", "301")
                .param("title", "测试教材")
                .header("X-Request-Id", "550e8400-e29b-41d4-a716-446655440000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value("101"))
            .andExpect(jsonPath("$.data.documentId").value("201"))
            .andExpect(jsonPath("$.data.importType").value("document_chunk"))
            .andExpect(jsonPath("$.data.mode").value("create"));

        ArgumentCaptor<ImportCreateBo> formCaptor = ArgumentCaptor.forClass(ImportCreateBo.class);
        verify(service).createTextbook(formCaptor.capture(), eq("550e8400-e29b-41d4-a716-446655440000"));
        ImportCreateBo form = formCaptor.getValue();
        assertThat(form.getFile()).extracting(MultipartFile::getOriginalFilename).isEqualTo("textbook.jsonl");
        assertThat(form.getImportType()).isEqualTo("document_chunk");
        assertThat(form.getTemplateVersion()).isEqualTo("document_chunk/1.0");
        assertThat(form.getMode()).isEqualTo("create");
        assertThat(form.getSyllabusVersionId()).isEqualTo("301");
        assertThat(form.getTitle()).isEqualTo("测试教材");
    }

    @Test
    void routesAllTextbookImportQueriesAndCommandsToSharedService() {
        ImportService service = mock(ImportService.class);
        ImportController controller = new ImportController(service);
        TextbookImportContextOptionsVo options = new TextbookImportContextOptionsVo(
            List.of(), List.of(), List.of(), List.of()
        );
        ImportValidationAcceptedVo accepted = new ImportValidationAcceptedVo(
            "101", "validating", "precheck", true
        );
        ImportProgressVo progress = new ImportProgressVo(
            "101", "waiting_confirm", "precheck", BigDecimal.valueOf(100),
            1, 1, 0, 0, null, null, null, false
        );
        TextbookImportPreviewVo preview = mock(TextbookImportPreviewVo.class);
        @SuppressWarnings("unchecked")
        PageResult<ImportIssueVo> issues = mock(PageResult.class);
        when(service.textbookContextOptions("301", "201")).thenReturn(options);
        when(service.validate("101")).thenReturn(accepted);
        when(service.progress("101")).thenReturn(progress);
        when(service.issues("101", 2, 20, "warning", "UNMAPPED_CHUNK", "lineNo", "asc"))
            .thenReturn(issues);
        when(service.preview("101", 2, 20)).thenReturn(preview);
        when(service.confirm("101", "550e8400-e29b-41d4-a716-446655440001")).thenReturn(accepted);

        assertThat(controller.contextOptions("textbook", "301", "201").getData()).isSameAs(options);
        assertThat(controller.validate("101").getData()).isSameAs(accepted);
        assertThat(controller.progress("101").getData()).isSameAs(progress);
        assertThat(controller.issues(
            "101", 2, 20, "warning", "UNMAPPED_CHUNK", "lineNo", "asc"
        ).getData()).isSameAs(issues);
        assertThat(controller.preview("101", 2, 20).getData()).isSameAs(preview);
        assertThat(controller.confirm("101", "550e8400-e29b-41d4-a716-446655440001").getData())
            .isSameAs(accepted);

        verify(service).textbookContextOptions("301", "201");
        verify(service).validate("101");
        verify(service).progress("101");
        verify(service).issues("101", 2, 20, "warning", "UNMAPPED_CHUNK", "lineNo", "asc");
        verify(service).preview("101", 2, 20);
        verify(service).confirm("101", "550e8400-e29b-41d4-a716-446655440001");
    }

    @Test
    void requiresCatalogImportPermissionOnSevenCoreEndpoints() throws Exception {
        assertImportPermission(ImportController.class.getMethod(
            "contextOptions", String.class, String.class, String.class
        ));
        assertImportPermission(ImportController.class.getMethod(
            "create", ImportCreateBo.class, String.class
        ));
        assertImportPermission(ImportController.class.getMethod("validate", String.class));
        assertImportPermission(ImportController.class.getMethod("progress", String.class));
        assertImportPermission(ImportController.class.getMethod(
            "issues", String.class, Integer.class, Integer.class, String.class, String.class, String.class, String.class
        ));
        assertImportPermission(ImportController.class.getMethod(
            "preview", String.class, Integer.class, Integer.class
        ));
        assertImportPermission(ImportController.class.getMethod("confirm", String.class, String.class));
    }

    private static void assertImportPermission(Method method) {
        assertThat(method.getAnnotation(SaCheckPermission.class).value())
            .containsExactly("certmuse:catalog:import");
    }
}
