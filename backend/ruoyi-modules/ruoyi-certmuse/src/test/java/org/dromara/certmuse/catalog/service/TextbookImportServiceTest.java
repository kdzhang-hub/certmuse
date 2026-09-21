package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.TextbookImportDocument;
import org.dromara.certmuse.catalog.domain.bo.ImportCreateBo;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.ImportServiceImpl;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.catalog.support.ImportJsonDocumentFactory;
import org.dromara.certmuse.catalog.support.ImportStorage;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TextbookImportServiceTest {

    private static final String REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Mock
    private ImportMapper repository;
    @Mock
    private ImportStorage storage;
    @Mock
    private ImportPersistenceService persistence;

    private JsonMapper mapper;
    private ImportService service;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder().build();
        service = new ImportServiceImpl(repository, storage, persistence, mapper, new ImportJsonDocumentFactory(mapper));
    }

    @Test
    void createsTextbookBatchWithFrozenFormalParseConfig() throws Exception {
        ImportCreateBo form = createForm(file("application/x-ndjson"));
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);

            var result = service.createTextbook(form, REQUEST_ID);

            assertThat(result.importType()).isEqualTo("document_chunk");
            assertThat(result.mode()).isEqualTo("create");
            assertThat(result.documentId()).isNull();
            assertThat(result.syllabusVersionId()).isNull();
        }

        ArgumentCaptor<String> config = ArgumentCaptor.forClass(String.class);
        verify(persistence).createTextbook(
            anyLong(), isNull(), eq(true), eq(REQUEST_ID), anyString(), eq(9L), eq(21L), eq("Book"), isNull(),
            anyString(), anyString(), eq("book.jsonl"), eq(1_048_576L), eq("application/x-ndjson"),
            config.capture(), eq(7L), eq(8L), any(OffsetDateTime.class), anyString()
        );
        var parsed = mapper.readTree(config.getValue());
        assertThat(parsed.size()).isEqualTo(8);
        assertThat(parsed.path("schema_version").textValue()).isEqualTo("import_parse_config/1.0");
        assertThat(parsed.path("import_type").textValue()).isEqualTo("document_chunk");
        assertThat(parsed.path("mode").textValue()).isEqualTo("create");
        assertThat(parsed.path("subject_mappings").get(0).path("exam_subject_id").longValue()).isEqualTo(11L);
        assertThat(parsed.has("template_version")).isFalse();
    }

    @Test
    void rejectsNonUuidAndMissingMimeBeforePersistence() throws Exception {
        assertThatThrownBy(() -> service.createTextbook(createForm(mock(MultipartFile.class)), "request-1"))
            .isInstanceOfSatisfying(ImportException.class, error ->
                assertThat(error.errorCode()).isEqualTo("IMPORT_CONTEXT_INVALID"));

        ImportCreateBo missingMime = createForm(headerFile(null));
        assertThatThrownBy(() -> service.createTextbook(missingMime, REQUEST_ID))
            .isInstanceOfSatisfying(ImportException.class, error ->
                assertThat(error.errorCode()).isEqualTo("IMPORT_FILE_INVALID"));
        verify(persistence, never()).createTextbook(
            anyLong(), anyLong(), anyBoolean(), anyString(), anyString(), anyLong(), nullable(Long.class), anyString(),
            nullable(String.class), anyString(), anyString(), anyString(), anyLong(), nullable(String.class),
            anyString(), anyLong(), any(), any(), anyString()
        );
    }

    @Test
    void rejectsReplaceTargetThatIsNotDraft() throws Exception {
        ImportCreateBo form = createForm(headerFile("application/x-ndjson"));
        form.setMode("replace_draft");
        form.setDocumentId("201");
        form.setSyllabusVersionId(null);
        when(repository.selectTextbookDocument(201L, 7L)).thenReturn(
            new TextbookImportDocument(201L, 21L, "Book", null, "published", "0", 7L, 8L)
        );

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            assertThatThrownBy(() -> service.createTextbook(form, REQUEST_ID))
                .isInstanceOfSatisfying(ImportException.class, error ->
                    assertThat(error.errorCode()).isEqualTo("IMPORT_TARGET_NOT_DRAFT"));
        }
    }

    @Test
    void confirmsTextbookWithDedicatedPersistencePath() {
        CmImportBatch waiting = batch("waiting_confirm");
        CmImportBatch importing = batch("importing");
        when(repository.selectVisible(101L, 7L)).thenReturn(waiting);
        when(repository.selectById(101L)).thenReturn(importing);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var result = service.confirm("101", REQUEST_ID);

            assertThat(result.accepted()).isTrue();
            assertThat(result.status()).isEqualTo("importing");
        }
        verify(persistence).acceptTextbookConfirmation(
            eq(101L), eq(REQUEST_ID), anyString(), eq(7L), any(OffsetDateTime.class), anyString()
        );
    }

    @Test
    void letsSuperAdminReadAnyVisibleBatchWithoutOwnerFilter() {
        when(repository.selectVisible(101L, null)).thenReturn(batch("waiting_confirm"));
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);

            assertThat(service.progress("101").id()).isEqualTo("101");
        }

        verify(repository).selectVisible(101L, null);
    }

    private static ImportCreateBo createForm(MultipartFile file) {
        ImportCreateBo form = new ImportCreateBo();
        form.setFile(file);
        form.setImportType("document_chunk");
        form.setMode("create");
        form.setSyllabusVersionId("21");
        form.setTitle("Book");
        form.setSubjectMappings("[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]");
        form.setTemplateVersion("document_chunk/1.0");
        return form;
    }

    private static MultipartFile file(String mime) throws Exception {
        byte[] bytes = new byte[1_048_576];
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("book.jsonl");
        when(file.getSize()).thenReturn((long) bytes.length);
        when(file.getContentType()).thenReturn(mime);
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
        return file;
    }

    private static MultipartFile headerFile(String mime) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("book.jsonl");
        when(file.getSize()).thenReturn(1_048_576L);
        when(file.getContentType()).thenReturn(mime);
        when(file.isEmpty()).thenReturn(false);
        return file;
    }

    private static CmImportBatch batch(String status) {
        return new CmImportBatch(
            101L,
            "upload-request",
            201L,
            null,
            null,
            "imports/document-chunk/101/source.jsonl",
            "file-hash",
            3_000_000L,
            "document_chunk",
            "document_chunk/1.0",
            "{\"mode\":\"create\"}",
            status,
            "importing".equals(status) ? "persist_document_chunks" : "finalize_counts",
            BigDecimal.ZERO,
            1,
            0,
            0,
            0,
            null,
            OffsetDateTime.parse("2026-08-03T10:00:00+08:00"),
            null,
            null,
            7L,
            8L
        );
    }
}
