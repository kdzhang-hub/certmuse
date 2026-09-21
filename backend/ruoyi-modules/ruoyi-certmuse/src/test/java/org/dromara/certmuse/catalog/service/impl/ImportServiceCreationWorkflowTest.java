package org.dromara.certmuse.catalog.service.impl;

import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.bo.ImportCreateBo;
import org.dromara.certmuse.catalog.domain.bo.PaperImportCreateBo;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.ImportService;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.catalog.support.ImportJsonDocumentFactory;
import org.dromara.certmuse.catalog.support.ImportProtocol;
import org.dromara.certmuse.catalog.support.ImportStorage;
import org.dromara.common.oss.exception.S3StorageException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Public import creation workflows and their durable compensation contracts. */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportServiceCreationWorkflowTest {
    private static final long BATCH_ID = 101L;

    @Mock private ImportMapper repository;
    @Mock private ImportStorage storage;
    @Mock private ImportPersistenceService persistence;
    @Mock private KnowledgeImportReconciliationService reconciliation;

    private ImportService service;

    @BeforeEach
    void setUp() {
        JsonMapper json = JsonMapper.builder().build();
        service = new ImportServiceImpl(repository, storage, persistence, json,
            new ImportJsonDocumentFactory(json), reconciliation);
    }

    @Test
    void questionImportAcceptsQualificationOnlyScopeAndReturnsDerivedSubjects() throws Exception {
        ImportCreateBo form = questionForm(zip("Q1"));
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectExamSubjectOptionsByCertification(9L)).thenReturn(List.of(subject(1, 11L, "综合知识")));

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var result = service.createQuestion(form, "question-create");

            assertThat(result.importType()).isEqualTo("question");
            assertThat(result.status()).isEqualTo("uploaded");
            assertThat(result.syllabusVersionId()).isNull();
            assertThat(result.derivedSubjects()).singleElement().satisfies(value -> {
                assertThat(value.subjectNo()).isEqualTo(1);
                assertThat(value.examSubjectId()).isEqualTo("11");
            });
        }
        verify(persistence).createQuestion(anyLong(), eq("question-create"), anyString(), eq(null), eq(9L),
            anyString(), anyString(), eq("questions.zip"), anyLong(), eq("application/zip"), anyString(),
            eq(7L), eq(8L), any(), anyString());
    }

    @Test
    void questionCreationCompensatesTheUploadedObjectWhenPersistenceFails() throws Exception {
        ImportCreateBo form = questionForm(zip("Q1"));
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectExamSubjectOptionsByCertification(9L)).thenReturn(List.of(subject(1, 11L, "综合知识")));
        doThrow(new IllegalStateException("database unavailable"))
            .when(persistence).createQuestion(anyLong(), eq("question-persistence-failure"), anyString(), eq(null), eq(9L),
                anyString(), anyString(), eq("questions.zip"), anyLong(), eq("application/zip"), anyString(),
                eq(7L), eq(8L), any(), anyString());
        when(storage.deleteQuietly(anyString())).thenReturn(true);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertThatThrownBy(() -> service.createQuestion(form, "question-persistence-failure"))
                .isInstanceOfSatisfying(ImportException.class, error -> {
                    assertThat(error.code()).isEqualTo(500);
                    assertThat(error.errorCode()).isEqualTo("IMPORT_SYSTEM_FAILURE");
                    assertThat(error.retryable()).isTrue();
                });
        }

        verify(storage).deleteQuietly(anyString());
    }

    @Test
    void questionCreationReplaysAnEquivalentRequestWithoutUploadingAnotherArchive() throws Exception {
        ImportCreateBo form = questionForm(zip("Q1"));
        String requestId = "question-idempotent-replay";
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectExamSubjectOptionsByCertification(9L)).thenReturn(List.of(subject(1, 11L, "综合知识")));

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var created = service.createQuestion(form, requestId);
            var payload = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(persistence).createQuestion(anyLong(), eq(requestId), payload.capture(), eq(null), eq(9L),
                anyString(), anyString(), eq("questions.zip"), anyLong(), eq("application/zip"), anyString(),
                eq(7L), eq(8L), any(), anyString());
            when(repository.selectIdempotency(ImportProtocol.QUESTION_CREATE_ACTION, requestId)).thenReturn(
                new CmIdempotencyRecord(payload.getValue(), "succeeded", Long.parseLong(created.id()), 200, "{}")
            );
            when(repository.selectById(Long.parseLong(created.id()))).thenReturn(
                replayedBatch(Long.parseLong(created.id()), "question", null, null, "{}")
            );

            var replayed = service.createQuestion(form, requestId);

            assertThat(replayed.reused()).isTrue();
            assertThat(replayed.id()).isEqualTo(created.id());
            assertThat(replayed.importType()).isEqualTo("question");
        }

        verify(storage, times(1)).upload(anyString(), any());
    }

    @Test
    void questionCreationMapsAnArchiveStorageOutageToTheRetryableStorageContract() throws Exception {
        ImportCreateBo form = questionForm(zip("Q1"));
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectExamSubjectOptionsByCertification(9L)).thenReturn(List.of(subject(1, 11L, "综合知识")));
        doThrow(new S3StorageException("archive storage unavailable")).when(storage).upload(anyString(), any());

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertThatThrownBy(() -> service.createQuestion(form, "question-storage-failure"))
                .isInstanceOfSatisfying(ImportException.class, error -> {
                    assertThat(error.code()).isEqualTo(503);
                    assertThat(error.errorCode()).isEqualTo("IMPORT_STORAGE_UNAVAILABLE");
                    assertThat(error.retryable()).isTrue();
                });
        }

        verify(persistence, never()).createQuestion(anyLong(), anyString(), anyString(), any(), anyLong(), anyString(),
            anyString(), anyString(), anyLong(), anyString(), anyString(), anyLong(), any(), any(), anyString());
    }

    @Test
    void textbookCreationCompensatesTheUploadedObjectWhenPersistenceFails() {
        ImportCreateBo form = textbookCreateForm();
        String requestId = UUID.randomUUID().toString();
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        doThrow(new IllegalStateException("database unavailable"))
            .when(persistence).createTextbook(anyLong(), eq(null), eq(true), eq(requestId), anyString(), eq(9L),
                eq(null), eq("教材"), eq("第一版"), anyString(), anyString(), eq("book.jsonl"), anyLong(),
                eq("application/jsonl"), anyString(), eq(7L), eq(8L), any(), anyString());
        when(storage.deleteQuietly(anyString())).thenReturn(true);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertThatThrownBy(() -> service.createTextbook(form, requestId))
                .isInstanceOfSatisfying(ImportException.class, error -> {
                    assertThat(error.code()).isEqualTo(500);
                    assertThat(error.errorCode()).isEqualTo("IMPORT_SYSTEM_FAILURE");
                    assertThat(error.retryable()).isTrue();
                });
        }

        verify(storage).deleteQuietly(anyString());
    }

    @Test
    void questionImportRejectsMissingMismatchedAndUnknownPublicScopesBeforeUpload() throws Exception {
        ImportCreateBo missingScope = questionForm(zip("Q1"));
        missingScope.setCertificationId(null);
        ImportCreateBo mismatch = questionForm(zip("Q1"));
        mismatch.setKnowledgeSyllabusVersionId("21");
        ImportCreateBo unknown = questionForm(zip("Q1"));

        when(repository.selectSyllabusCertification(21L)).thenReturn(10L);
        when(repository.selectCertificationName(9L)).thenReturn(null);

        assertContextError(() -> service.createQuestion(missingScope, "question-missing"));
        assertContextError(() -> service.createQuestion(mismatch, "question-mismatch"));
        assertContextError(() -> service.createQuestion(unknown, "question-unknown"));

        verify(storage, never()).upload(anyString(), any());
    }

    @Test
    void paperImportRequiresCurrentSyllabusAndReturnsThePaperBatchContract() throws Exception {
        PaperImportCreateBo form = paperForm(zip("Q1"));
        when(repository.selectLatestSyllabusForCertification(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.createPaper(form, "paper-no-syllabus"))
            .isInstanceOfSatisfying(ImportException.class, error ->
                assertThat(error.errorCode()).isEqualTo("PAPER_SYLLABUS_UNRESOLVED"));

        when(repository.selectLatestSyllabusForCertification(9L)).thenReturn(21L);
        when(repository.selectExamSubjectOptions(21L)).thenReturn(List.of(subject(1, 11L, "综合知识")));
        when(repository.selectCertificationNameBySyllabus(21L)).thenReturn("系统架构设计师");

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var result = service.createPaper(form, "paper-create");
            assertThat(result.importType()).isEqualTo("paper");
            assertThat(result.syllabusVersionId()).isEqualTo("21");
            assertThat(result.status()).isEqualTo("uploaded");
        }
        verify(persistence).createPaper(anyLong(), eq("paper-create"), anyString(), eq(21L), eq(9L),
            eq("模拟卷"), eq("SIMULATION"), eq(90), anyString(), anyString(), eq("questions.zip"), anyLong(),
            eq("application/zip"), anyString(), eq(7L), eq(8L), any(), anyString());
    }

    @Test
    void textbookCreateRejectsInvalidUuidAndMissingMappedSubjectsBeforeAnyUpload() {
        ImportCreateBo invalidId = textbookCreateForm();
        ImportCreateBo missingSubject = textbookCreateForm();

        assertContextError(() -> service.createTextbook(invalidId, "not-a-uuid"));
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectSubjectCertification(11L)).thenReturn(null);
        assertContextError(() -> service.createTextbook(missingSubject, UUID.randomUUID().toString()));

        verify(storage, never()).upload(anyString(), any());
    }

    @Test
    void textbookCreateReportsStorageFailureWithoutCallingThePersistenceBoundary() {
        ImportCreateBo form = textbookCreateForm();
        String requestId = UUID.randomUUID().toString();
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        doThrow(new IllegalStateException("storage unavailable")).when(storage).upload(anyString(), any());

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertThatThrownBy(() -> service.createTextbook(form, requestId))
                .isInstanceOfSatisfying(ImportException.class, error -> {
                    assertThat(error.code()).isEqualTo(500);
                    assertThat(error.errorCode()).isEqualTo("IMPORT_SYSTEM_FAILURE");
                });
        }
        verify(persistence, never()).createTextbook(anyLong(), any(), anyBoolean(), anyString(), anyString(), anyLong(), any(),
            anyString(), any(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyLong(), any(), any(), anyString());
    }

    @Test
    void textbookCreationReplaysAnEquivalentRequestWithoutUploadingAnotherDocument() {
        ImportCreateBo form = textbookCreateForm();
        String requestId = UUID.randomUUID().toString();
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var created = service.createTextbook(form, requestId);
            var payload = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(persistence).createTextbook(anyLong(), eq(null), eq(true), eq(requestId), payload.capture(), eq(9L),
                eq(null), eq("教材"), eq("第一版"), anyString(), anyString(), eq("book.jsonl"), anyLong(),
                eq("application/jsonl"), anyString(), eq(7L), eq(8L), any(), anyString());
            when(repository.selectIdempotency(ImportProtocol.TEXTBOOK_CREATE_ACTION, requestId)).thenReturn(
                new CmIdempotencyRecord(payload.getValue(), "succeeded", Long.parseLong(created.id()), 200, "{}")
            );
            when(repository.selectById(Long.parseLong(created.id()))).thenReturn(
                replayedBatch(Long.parseLong(created.id()), "document_chunk", null, null, "{\"mode\":\"create\"}")
            );

            var replayed = service.createTextbook(form, requestId);

            assertThat(replayed.reused()).isTrue();
            assertThat(replayed.mode()).isEqualTo("create");
            assertThat(replayed.importType()).isEqualTo("document_chunk");
        }

        verify(storage, times(1)).upload(anyString(), any());
    }

    @Test
    void textbookCreationMapsAStorageOutageToTheRetryableStorageContract() {
        ImportCreateBo form = textbookCreateForm();
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        doThrow(new S3StorageException("document storage unavailable")).when(storage).upload(anyString(), any());

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertThatThrownBy(() -> service.createTextbook(form, UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(ImportException.class, error -> {
                    assertThat(error.code()).isEqualTo(503);
                    assertThat(error.errorCode()).isEqualTo("IMPORT_STORAGE_UNAVAILABLE");
                    assertThat(error.retryable()).isTrue();
                });
        }

        verify(persistence, never()).createTextbook(anyLong(), any(), anyBoolean(), anyString(), anyString(), anyLong(), any(),
            anyString(), any(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyLong(), any(), any(), anyString());
    }

    @Test
    void paperCreationReplaysTheConcurrentRequestAfterCompensatingTheUploadedObject() throws Exception {
        PaperImportCreateBo form = paperForm(zip("Q1"));
        when(repository.selectLatestSyllabusForCertification(9L)).thenReturn(21L);
        when(repository.selectExamSubjectOptions(21L)).thenReturn(List.of(subject(1, 11L, "综合知识")));
        when(repository.selectCertificationNameBySyllabus(21L)).thenReturn("系统架构设计师");
        AtomicReference<String> payload = new AtomicReference<>();
        doAnswer(invocation -> {
            payload.set(invocation.getArgument(2, String.class));
            throw new DataIntegrityViolationException("duplicate request");
        })
            .when(persistence).createPaper(anyLong(), eq("paper-race"), anyString(), eq(21L), eq(9L), anyString(),
                anyString(), anyInt(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyLong(), any(), any(), anyString());
        when(storage.deleteQuietly(anyString())).thenReturn(true);
        when(repository.selectIdempotency(ImportProtocol.PAPER_CREATE_ACTION, "paper-race")).thenAnswer(invocation ->
            payload.get() == null ? null : new CmIdempotencyRecord(payload.get(), "succeeded", BATCH_ID, 200, "{}")
        );
        when(repository.selectById(BATCH_ID)).thenReturn(replayedBatch(BATCH_ID, "paper", null, 21L, "{}"));

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var replayed = service.createPaper(form, "paper-race");

            assertThat(replayed.reused()).isTrue();
            assertThat(replayed.id()).isEqualTo(Long.toString(BATCH_ID));
            assertThat(replayed.importType()).isEqualTo("paper");
        }
        verify(storage).deleteQuietly(anyString());
    }

    @Test
    void paperCreationCompensatesTheUploadedObjectWhenPersistenceFails() throws Exception {
        PaperImportCreateBo form = paperForm(zip("Q1"));
        when(repository.selectLatestSyllabusForCertification(9L)).thenReturn(21L);
        when(repository.selectExamSubjectOptions(21L)).thenReturn(List.of(subject(1, 11L, "综合知识")));
        when(repository.selectCertificationNameBySyllabus(21L)).thenReturn("系统架构设计师");
        doThrow(new IllegalStateException("database unavailable"))
            .when(persistence).createPaper(anyLong(), eq("paper-persistence-failure"), anyString(), eq(21L), eq(9L),
                eq("模拟卷"), eq("SIMULATION"), eq(90), anyString(), anyString(), eq("questions.zip"), anyLong(),
                eq("application/zip"), anyString(), eq(7L), eq(8L), any(), anyString());
        when(storage.deleteQuietly(anyString())).thenReturn(true);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertThatThrownBy(() -> service.createPaper(form, "paper-persistence-failure"))
                .isInstanceOfSatisfying(ImportException.class, error -> {
                    assertThat(error.code()).isEqualTo(500);
                    assertThat(error.errorCode()).isEqualTo("IMPORT_SYSTEM_FAILURE");
                    assertThat(error.retryable()).isTrue();
                });
        }

        verify(storage).deleteQuietly(anyString());
    }

    @Test
    void paperCreationMapsAnArchiveStorageOutageToTheRetryableStorageContract() throws Exception {
        PaperImportCreateBo form = paperForm(zip("Q1"));
        when(repository.selectLatestSyllabusForCertification(9L)).thenReturn(21L);
        when(repository.selectExamSubjectOptions(21L)).thenReturn(List.of(subject(1, 11L, "综合知识")));
        when(repository.selectCertificationNameBySyllabus(21L)).thenReturn("系统架构设计师");
        doThrow(new S3StorageException("paper storage unavailable")).when(storage).upload(anyString(), any());

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertThatThrownBy(() -> service.createPaper(form, "paper-storage-failure"))
                .isInstanceOfSatisfying(ImportException.class, error -> {
                    assertThat(error.code()).isEqualTo(503);
                    assertThat(error.errorCode()).isEqualTo("IMPORT_STORAGE_UNAVAILABLE");
                    assertThat(error.retryable()).isTrue();
                });
        }

        verify(persistence, never()).createPaper(anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyString(),
            anyString(), anyInt(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyLong(),
            any(), any(), anyString());
    }

    private static CmImportBatch replayedBatch(
        long id, String importType, Long documentId, Long syllabusVersionId, String parseConfig
    ) {
        String templateVersion = "document_chunk".equals(importType) ? "document_chunk/1.0" : "question-zip/1.0";
        return new CmImportBatch(
            id, "replayed-request", documentId, syllabusVersionId, null, "imports/replayed-source", "file-hash", 1L,
            importType, templateVersion, parseConfig, "uploaded", "queued", BigDecimal.ZERO,
            0, 0, 0, 0, null, OffsetDateTime.parse("2026-08-13T10:00:00+08:00"), null, null,
            7L, 8L, null, null, 9L
        );
    }

    private static ImportCreateBo questionForm(MockMultipartFile file) {
        ImportCreateBo form = new ImportCreateBo();
        form.setImportType("question");
        form.setTemplateVersion("question-zip/1.0");
        form.setCertificationId("9");
        form.setFile(file);
        return form;
    }

    private static PaperImportCreateBo paperForm(MockMultipartFile file) {
        PaperImportCreateBo form = new PaperImportCreateBo();
        form.setFile(file);
        form.setCertificationId("9");
        form.setCollectionName("模拟卷");
        form.setCollectionType("SIMULATION");
        form.setDurationMinutes(90);
        return form;
    }

    private static ImportCreateBo textbookCreateForm() {
        ImportCreateBo form = new ImportCreateBo();
        form.setImportType("document_chunk");
        form.setTemplateVersion("document_chunk/1.0");
        form.setMode("create");
        form.setCertificationId("9");
        form.setTitle("教材");
        form.setEdition("第一版");
        form.setSubjectMappings("[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]");
        form.setFile(new MockMultipartFile("file", "book.jsonl", "application/jsonl",
            ("{}\n" + "x".repeat(1_048_577)).getBytes(StandardCharsets.UTF_8)));
        return form;
    }

    private static MockMultipartFile zip(String subject) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry("questions.jsonl"));
            output.write(("{\"qid\":\"q1\",\"subject\":\"" + subject + "\",\"type\":\"single\","
                + "\"question\":\"题干\",\"options\":{\"A\":\"甲\",\"B\":\"乙\"},\"answer\":\"A\","
                + "\"knowledge_points\":[{\"subject_no\":1,\"code\":\"K1\"}],\"images\":[]}")
                .getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return new MockMultipartFile("file", "questions.zip", "application/zip", bytes.toByteArray());
    }

    private static org.dromara.certmuse.catalog.domain.vo.ExamSubjectOptionVo subject(int no, long id, String label) {
        return new org.dromara.certmuse.catalog.domain.vo.ExamSubjectOptionVo(Long.toString(id), no, label);
    }

    private static MockedStatic<LoginHelper> ordinaryUser() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        login.when(LoginHelper::getUserId).thenReturn(7L);
        login.when(LoginHelper::getDeptId).thenReturn(8L);
        return login;
    }

    private static void assertContextError(ThrowingCall call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ImportException.class, error ->
            assertThat(error.errorCode()).isEqualTo("IMPORT_CONTEXT_INVALID"));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
