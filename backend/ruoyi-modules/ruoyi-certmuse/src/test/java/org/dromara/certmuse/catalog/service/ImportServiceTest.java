package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffCounts;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffRow;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.dromara.certmuse.catalog.domain.PaperImportRow;
import org.dromara.certmuse.catalog.domain.TextbookImportDocument;
import org.dromara.certmuse.catalog.domain.TextbookPreviewKnowledgePointLookup;
import org.dromara.certmuse.catalog.domain.TextbookPreviewRecord;
import org.dromara.certmuse.catalog.domain.bo.ImportCreateBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffBatchResolutionBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffQueryBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffResolutionBo;
import org.dromara.certmuse.catalog.domain.bo.PaperImportCreateBo;
import org.dromara.certmuse.catalog.domain.vo.ExamSubjectOptionVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchVo;
import org.dromara.certmuse.catalog.domain.vo.ImportValidationAcceptedVo;
import org.dromara.certmuse.catalog.domain.vo.ImportIssueVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusVersionOptionVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewDocumentVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewSummaryVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookOptionVo;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.ImportServiceImpl;
import org.dromara.certmuse.catalog.service.impl.KnowledgeImportReconciliationService;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.catalog.support.ImportJsonDocumentFactory;
import org.dromara.common.oss.exception.S3StorageException;
import org.dromara.certmuse.catalog.support.ImportProtocol;
import org.dromara.certmuse.catalog.support.ImportStorage;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportServiceTest {

    @Mock
    private ImportMapper repository;
    @Mock
    private ImportStorage storage;
    @Mock
    private ImportPersistenceService persistence;
    @Mock
    private KnowledgeImportReconciliationService reconciliationService;

    private ImportService service;

    @BeforeEach
    void setUp() {
        JsonMapper objectMapper = JsonMapper.builder().build();
        service = new ImportServiceImpl(
            repository,
            storage,
            persistence,
            objectMapper,
            new ImportJsonDocumentFactory(objectMapper),
            reconciliationService
        );
    }

    @Test
    void returnsSyllabusOptionsBeforeASyllabusIsSelected() {
        var versions = List.of(
            new SyllabusVersionOptionVo("21", "系统架构设计师 · 第二版", false, 0L)
        );
        when(repository.selectSyllabusOptions()).thenReturn(versions);

        var result = service.contextOptions("knowledge_point", null);

        assertThat(result.syllabusVersions()).containsExactlyElementsOf(versions);
        assertThat(result.examSubjects()).isEmpty();
    }

    @Test
    void returnsOnlySubjectsForTheSelectedSyllabus() {
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSyllabusOptions()).thenReturn(List.of());
        when(repository.selectExamSubjectOptions(21L))
            .thenReturn(List.of(new ExamSubjectOptionVo("11", 1, "综合知识")));

        var result = service.contextOptions("knowledge_point", "21");

        assertThat(result.examSubjects()).containsExactly(new ExamSubjectOptionVo("11", 1, "综合知识"));
    }

    @Test
    void returnsQualificationsWithoutSyllabusAndTheirSubjectsForNewSyllabusImport() {
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectExamSubjectOptionsByCertification(9L))
            .thenReturn(List.of(new ExamSubjectOptionVo("11", 1, "综合知识")));
        when(repository.selectSyllabusOptions()).thenReturn(List.of());
        when(repository.selectCertificationOptionsWithoutSyllabus())
            .thenReturn(List.of(new TextbookOptionVo("9", "系统架构设计师")));

        var result = service.contextOptions("knowledge_point", null, "9");

        assertThat(result.certifications()).containsExactly(new TextbookOptionVo("9", "系统架构设计师"));
        assertThat(result.examSubjects()).containsExactly(new ExamSubjectOptionVo("11", 1, "综合知识"));
    }

    @Test
    void rejectsUnsupportedContextType() {
        assertThatThrownBy(() -> service.contextOptions("question", null))
            .isInstanceOf(ImportException.class)
            .extracting(error -> ((ImportException) error).code())
            .isEqualTo(400);
    }

    @Test
    void startsPrecheckWhenTheSyllabusHasNoActiveKnowledgePoints() {
        when(repository.selectVisible(101L, 7L)).thenReturn(batch("uploaded"));
        when(persistence.enqueueValidation(101L)).thenReturn(true);
        when(repository.selectById(101L)).thenReturn(batch("parsing"));

        ImportValidationAcceptedVo result;
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            result = service.validate("101");
        }

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo("parsing");
        verify(persistence).enqueueValidation(101L);
    }

    @Test
    void startsPrecheckWhenTheSyllabusAlreadyHasKnowledgePoints() {
        when(repository.selectVisible(101L, 7L)).thenReturn(batch("uploaded"));
        when(persistence.enqueueValidation(101L)).thenReturn(true);
        when(repository.selectById(101L)).thenReturn(batch("parsing"));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThat(service.validate("101").accepted()).isTrue();
        }
        verify(persistence).enqueueValidation(101L);
    }

    @Test
    void confirmsQuestionBatchWithSuccessfulAndFailedRecords() {
        when(repository.selectVisible(101L, 7L)).thenReturn(questionBatch("waiting_confirm", 2, 1));
        when(repository.selectById(101L)).thenReturn(questionBatch("importing", 2, 1));

        ImportValidationAcceptedVo result;
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            result = service.confirm("101", "question-confirm-1");
        }

        assertThat(result.status()).isEqualTo("importing");
        assertThat(result.accepted()).isTrue();
        verify(persistence).acceptQuestionConfirmation(eq(101L), eq("question-confirm-1"), any(), eq(7L), any(), any());
    }

    @Test
    void returnsAllSyllabusVersionsSoQuestionImportsCanEnterPrecheck() {
        when(repository.selectSyllabusOptions()).thenReturn(List.of(
            new SyllabusVersionOptionVo("21", "可用", true, 3L),
            new SyllabusVersionOptionVo("22", "空树", true, 0L),
            new SyllabusVersionOptionVo("23", "未发布", false, 9L)
        ));

        var result = service.questionContextOptions();

        assertThat(result.knowledgeSyllabusVersions())
            .extracting(SyllabusVersionOptionVo::id)
            .containsExactly("21", "22", "23");
    }

    @Test
    void createsQuestionPrecheckBatchBeforeKnowledgePointsExist() throws Exception {
        ImportCreateBo form = new ImportCreateBo();
        form.setImportType("question");
        form.setKnowledgeSyllabusVersionId("21");
        form.setTemplateVersion("question-zip/1.0");
        form.setFile(new MockMultipartFile("file", "questions.zip", "application/zip", questionZip()));
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectExamSubjectOptionsByCertification(9L)).thenReturn(
            List.of(new ExamSubjectOptionVo("11", 1, "综合知识")));
        when(repository.selectCertificationNameBySyllabus(21L)).thenReturn("系统架构设计师");

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);
            var result = service.createQuestion(form, "question-create-1");
            assertThat(result.status()).isEqualTo("uploaded");
            assertThat(result.knowledgeSyllabusVersionId()).isEqualTo("21");
        }

        verify(repository, never()).countKnowledgePoints(21L);
        verify(persistence).createQuestion(anyLong(), eq("question-create-1"), any(), eq(21L), eq(9L), any(), any(),
            eq("questions.zip"), anyLong(), eq("application/zip"), any(), eq(7L), eq(8L), any(), any());
    }

    @Test
    void createsQuestionBatchFromCertificationWithoutSyllabus() throws Exception {
        ImportCreateBo form = new ImportCreateBo();
        form.setImportType("question");
        form.setCertificationId("9");
        form.setTemplateVersion("question-zip/1.0");
        form.setFile(new MockMultipartFile("file", "questions.zip", "application/zip", questionZip()));
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectExamSubjectOptionsByCertification(9L)).thenReturn(
            List.of(new ExamSubjectOptionVo("11", 1, "综合知识")));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);

            var result = service.createQuestion(form, "question-certification-create");

            assertThat(result.syllabusVersionId()).isNull();
            assertThat(result.knowledgeSyllabusVersionId()).isNull();
        }

        verify(persistence).createQuestion(anyLong(), eq("question-certification-create"), any(),
            isNull(), eq(9L), any(), any(), eq("questions.zip"), anyLong(), eq("application/zip"),
            any(), eq(7L), eq(8L), any(), any());
    }

    @Test
    void rejectsConflictingCertificationAndQuestionSyllabus() {
        ImportCreateBo form = new ImportCreateBo();
        form.setImportType("question");
        form.setCertificationId("10");
        form.setKnowledgeSyllabusVersionId("21");
        form.setTemplateVersion("question-zip/1.0");
        form.setFile(new MockMultipartFile("file", "questions.zip", "application/zip", new byte[]{1}));
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);

        assertThatThrownBy(() -> service.createQuestion(form, "question-conflict"))
            .isInstanceOfSatisfying(ImportException.class, error -> {
                assertThat(error.code()).isEqualTo(400);
                assertThat(error.errorCode()).isEqualTo("IMPORT_CONTEXT_INVALID");
            });
        verify(storage, never()).upload(any(), any());
    }

    @Test
    void reusesAnIdenticalKnowledgeImportRequestWithoutUploadingAgain() {
        MultipartFile file = new MockMultipartFile(
            "file", "tree.jsonl", "application/jsonl", "{}\n".getBytes(StandardCharsets.UTF_8)
        );
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);

        String requestId = "knowledge-reuse-request";
        ImportBatchVo first;
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);

            first = service.create(
                file, requestId, "knowledge_point", "21", "knowledge_point/1.0",
                "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"
            );

            ArgumentCaptor<String> payloadHash = ArgumentCaptor.forClass(String.class);
            verify(persistence).create(anyLong(), eq(requestId), payloadHash.capture(), eq(21L), anyString(), anyString(),
                eq("tree.jsonl"), anyLong(), eq("application/jsonl"), anyString(), eq(7L), eq(8L), any(), anyString());
            when(repository.selectIdempotency(ImportProtocol.KNOWLEDGE_CREATE_ACTION, requestId)).thenReturn(
                new CmIdempotencyRecord(payloadHash.getValue(), "succeeded", Long.parseLong(first.id()), 200, "{}")
            );
            when(repository.selectById(anyLong())).thenReturn(batch("uploaded"));

            var reused = service.create(
                file, requestId, "knowledge_point", "21", "knowledge_point/1.0",
                "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"
            );

            assertThat(reused.reused()).isTrue();
            assertThat(reused.importType()).isEqualTo("knowledge_point");
        }

        verify(storage, times(1)).upload(anyString(), any());
        verify(persistence, times(1)).create(anyLong(), eq(requestId), anyString(), eq(21L), anyString(), anyString(),
            eq("tree.jsonl"), anyLong(), eq("application/jsonl"), anyString(), eq(7L), eq(8L), any(), anyString());
    }

    @Test
    void rejectsAReusedRequestIdWhenThePayloadChanges() {
        MultipartFile file = new MockMultipartFile(
            "file", "tree.jsonl", "application/jsonl", "{}\n".getBytes(StandardCharsets.UTF_8)
        );
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        when(repository.selectIdempotency(ImportProtocol.KNOWLEDGE_CREATE_ACTION, "same-request"))
            .thenReturn(new CmIdempotencyRecord("different-payload", "succeeded", 101L, 200, "{}"));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThatThrownBy(() -> service.create(file, "same-request", "knowledge_point", "21",
                "knowledge_point/1.0", "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"))
                .isInstanceOfSatisfying(ImportException.class, exception ->
                    assertThat(exception.errorCode()).isEqualTo("IMPORT_REQUEST_ID_CONFLICT"));
        }

        verify(storage, never()).upload(anyString(), any());
        verify(persistence, never()).create(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(),
            anyString(), anyLong(), anyString(), anyString(), anyLong(), any(), any(), anyString());
    }

    @Test
    void compensatesAnUploadedObjectWhenConcurrentCreationWins() {
        MultipartFile file = new MockMultipartFile(
            "file", "tree.jsonl", "application/jsonl", "{}\n".getBytes(StandardCharsets.UTF_8)
        );
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        String requestId = "concurrent-request";
        when(repository.selectIdempotency(ImportProtocol.KNOWLEDGE_CREATE_ACTION, requestId)).thenReturn(null);
        when(repository.selectById(101L)).thenReturn(batch("uploaded"));
        when(storage.deleteQuietly(anyString())).thenReturn(false);
        org.mockito.Mockito.doAnswer(invocation -> {
            String payloadHash = invocation.getArgument(2);
            when(repository.selectIdempotency(ImportProtocol.KNOWLEDGE_CREATE_ACTION, requestId))
                .thenReturn(new CmIdempotencyRecord(payloadHash, "succeeded", 101L, 200, "{}"));
            throw new org.springframework.dao.DataIntegrityViolationException("duplicate request");
        }).when(persistence).create(anyLong(), eq(requestId), anyString(), eq(21L), anyString(), anyString(),
            anyString(), anyLong(), anyString(), anyString(), anyLong(), any(), any(), anyString());

        ImportBatchVo result;
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);
            result = service.create(file, requestId, "knowledge_point", "21", "knowledge_point/1.0",
                "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]");
        }

        assertThat(result.reused()).isTrue();
        verify(storage).deleteQuietly(anyString());
        verify(persistence).enqueueCleanup(anyString(), anyString(), eq("concurrent_create_lost"));
    }

    @Test
    void rejectsUtf8BomBeforeUploadingKnowledgeImports() {
        MultipartFile file = new MockMultipartFile(
            "file", "tree.jsonl", "application/jsonl",
            new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '{', '}', '\n'}
        );
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);

        assertThatThrownBy(() -> service.create(file, "bom-request", "knowledge_point", "21",
            "knowledge_point/1.0", "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"))
            .isInstanceOfSatisfying(ImportException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo("IMPORT_FILE_INVALID"));
        verify(storage, never()).upload(anyString(), any());
    }

    @Test
    void invalidRejectsMissingSyllabusAndMalformedPositiveIdentifiers() {
        when(repository.selectSyllabusCertification(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.contextOptions("knowledge_point", "99"))
            .isInstanceOf(ImportException.class)
            .extracting(error -> ((ImportException) error).errorCode())
            .isEqualTo("IMPORT_CONTEXT_INVALID");
        assertThatThrownBy(() -> service.contextOptions("knowledge_point", "0"))
            .isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> service.contextOptions("knowledge_point", "not-a-number"))
            .isInstanceOf(ImportException.class);
    }

    @Test
    void validBuildsProgressTotalsAndExposesTraceOnlyForFailedBatches() {
        when(repository.selectVisible(101L, 7L)).thenReturn(questionBatch("failed", 2, 3));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            var result = service.progress("101");
            assertThat(result.totalCount()).isEqualTo(5);
            assertThat(result.failureTraceId()).isNull();
        }
    }

    @Test
    void invalidRejectsInvisibleBatchAndIssueQueryAllowlistViolations() {
        org.mockito.Mockito.lenient().when(repository.selectVisible(101L, 7L)).thenReturn(batch("waiting_confirm"));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThatThrownBy(() -> service.progress("999")).isInstanceOf(ImportException.class);
            assertThatThrownBy(() -> service.issues("101", 0, 20, null, null, null, null)).isInstanceOf(ImportException.class);
            assertThatThrownBy(() -> service.issues("101", 1, 101, null, null, null, null)).isInstanceOf(ImportException.class);
            assertThatThrownBy(() -> service.issues("101", 1, 20, "info", null, null, null)).isInstanceOf(ImportException.class);
            assertThatThrownBy(() -> service.issues("101", 1, 20, null, null, "sql", null)).isInstanceOf(ImportException.class);
            assertThatThrownBy(() -> service.issues("101", 1, 20, null, null, null, "sideways")).isInstanceOf(ImportException.class);
        }
    }

    @Test
    void edgeAppliesIssueDefaultsAndSupportsDescendingLastPage() {
        ImportIssueVo issue = new ImportIssueVo(3, "k", "title", "warning", "W1", "warning", OffsetDateTime.now());
        when(repository.selectVisible(101L, 7L)).thenReturn(batch("waiting_confirm"));
        when(repository.selectIssues(101L, "warning", "W1", "createTime", false, 1, 2)).thenReturn(List.of(issue));
        when(repository.countIssues(101L, "warning", "W1")).thenReturn(3L);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            var result = service.issues("101", 3, 1, "warning", "W1", "createTime", "descending");
            assertThat(result.getRows()).containsExactly(issue);
            assertThat(result.getTotal()).isEqualTo(3L);
        }
    }

    @Test
    void enrichesPreviewKnowledgePointsWithTheirStoredTitles() {
        CmImportBatch textbookBatch = new CmImportBatch(
            101L, "upload-1", 201L, null, null, "imports/textbook.jsonl", "hash", 100L,
            "document_chunk", "document_chunk/1.0",
            "{\"subject_mappings\":[{\"subject_no\":1,\"exam_subject_id\":11}]}", "waiting_confirm", "finalize_counts",
            BigDecimal.ONE, 1, 0, 0, 0, null,
            OffsetDateTime.parse("2026-08-04T09:00:00+08:00"), null, null, 7L, 8L
        );
        when(repository.selectVisible(101L, 7L)).thenReturn(textbookBatch);
        when(repository.selectTextbookPreviewDocument(101L)).thenReturn(
            new TextbookImportPreviewDocumentVo("201", "教材", null, "系统架构设计师 · 第二版")
        );
        when(repository.selectTextbookPreviewSummary(101L)).thenReturn(
            new TextbookImportPreviewSummaryVo(1, 1, 1, 0, 1, 0, 0, 0)
        );
        when(repository.selectTextbookPreviewRecords(101L, 20, 0)).thenReturn(List.of(
            new TextbookPreviewRecord(1, "chunk-1", "{\"record\":{\"chunk_no\":1,\"heading_path\":[],\"content\":\"正文\",\"knowledge_points\":[{\"subject_no\":1,\"code\":\"1.4.3.3.3\"}]}}", 0)
        ));
        when(repository.selectTextbookPreviewKnowledgePointLookups(101L, List.of(11L), List.of("1.4.3.3.3")))
            .thenReturn(List.of(new TextbookPreviewKnowledgePointLookup(11L, "1.4.3.3.3", "形式化方法")));
        when(repository.countTextbookPreviewRecords(101L)).thenReturn(1L);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            var result = service.preview("101", null, null);
            assertThat(result.chunks().getRows().iterator().next().knowledgePoints().get(0).title())
                .isEqualTo("形式化方法");
        }
    }

    @Test
    void exposesAnExplicitFallbackWhenATerminalBatchReferenceHasNoStoredTitle() {
        CmImportBatch textbookBatch = new CmImportBatch(
            101L, "upload-1", 201L, null, null, "imports/textbook.jsonl", "hash", 100L,
            "document_chunk", "document_chunk/1.0",
            "{\"subject_mappings\":[{\"subject_no\":1,\"exam_subject_id\":11}]}", "failed", "finalize_counts",
            BigDecimal.ONE, 0, 0, 1, 0, null,
            OffsetDateTime.parse("2026-08-04T09:00:00+08:00"), null, null, 7L, 8L
        );
        when(repository.selectVisible(101L, 7L)).thenReturn(textbookBatch);
        when(repository.selectTextbookPreviewDocument(101L)).thenReturn(
            new TextbookImportPreviewDocumentVo("201", "教材", null, "系统架构设计师 · 第二版")
        );
        when(repository.selectTextbookPreviewRecords(101L, 20, 0)).thenReturn(List.of(
            new TextbookPreviewRecord(1, "chunk-1", "{\"record\":{\"chunk_no\":1,\"heading_path\":[],\"content\":\"正文\",\"knowledge_points\":[{\"subject_no\":1,\"code\":\"missing\"}]}}", 1)
        ));
        when(repository.selectTextbookPreviewKnowledgePointLookups(101L, List.of(11L), List.of("missing")))
            .thenReturn(List.of());
        when(repository.countTextbookPreviewRecords(101L)).thenReturn(1L);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            var result = service.preview("101", null, null);
            assertThat(result.chunks().getRows().iterator().next().knowledgePoints().get(0).title())
                .isEqualTo("知识点名称不可用");
        }
    }

    @Test
    void validCreatesKnowledgeImportAfterHeaderMappingAndUtf8Validation() {
        MultipartFile file = new MockMultipartFile("file", "tree.jsonl", "application/jsonl", "{}\n".getBytes());
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);
            var result = service.create(file, "create-1", "knowledge_point", "21", "knowledge_point/1.0",
                "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]");
            assertThat(result.importType()).isEqualTo("knowledge_point");
            assertThat(result.status()).isEqualTo("uploaded");
            assertThat(result.reused()).isFalse();
        }
        verify(storage).upload(org.mockito.ArgumentMatchers.contains("imports/knowledge-point/"), any());
        verify(persistence).create(anyLong(), eq("create-1"), any(), eq(21L), any(), any(), eq("tree.jsonl"),
            eq(3L), eq("application/jsonl"), any(), eq(7L), eq(8L), any(), any());
    }

    @Test
    void mapsStorageUploadFailuresToRetryableServiceUnavailable() {
        MultipartFile file = new MockMultipartFile("file", "tree.jsonl", "application/jsonl", "{}\n".getBytes());
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        org.mockito.Mockito.doThrow(new S3StorageException("storage unavailable"))
            .when(storage).upload(anyString(), any());

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);

            assertThatThrownBy(() -> service.create(file, "storage-failure", "knowledge_point", "21",
                "knowledge_point/1.0", "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"))
                .isInstanceOfSatisfying(ImportException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(503);
                    assertThat(exception.errorCode()).isEqualTo("IMPORT_STORAGE_UNAVAILABLE");
                    assertThat(exception.retryable()).isTrue();
                    assertThat(exception.traceId()).isNotBlank();
                });
        }

        verify(persistence, never()).create(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(),
            anyString(), anyLong(), anyString(), anyString(), anyLong(), any(), any(), anyString());
    }

    @Test
    void invalidRejectsHeaderAndFileErrorsBeforeUploading() {
        MultipartFile valid = new MockMultipartFile("file", "tree.jsonl", "application/jsonl", "{}".getBytes());
        MultipartFile wrongExtension = new MockMultipartFile("file", "tree.txt", "text/plain", "{}".getBytes());
        MultipartFile wrongMime = new MockMultipartFile("file", "tree.jsonl", "application/zip", "{}".getBytes());

        assertThatThrownBy(() -> service.create(valid, "", "knowledge_point", "21", "knowledge_point/1.0", "[]")).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> service.create(valid, "r", "question", "21", "knowledge_point/1.0", "[]")).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> service.create(wrongExtension, "r", "knowledge_point", "21", "knowledge_point/1.0", "[]")).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> service.create(wrongMime, "r", "knowledge_point", "21", "knowledge_point/1.0", "[]")).isInstanceOf(ImportException.class);
        verify(storage, never()).upload(any(), any());
    }

    @Test
    void textbookContextOffersQualificationsAndSyllabusesWithoutRequiringAKnowledgeTree() {
        when(repository.selectSyllabusOptions()).thenReturn(List.of(
            new SyllabusVersionOptionVo("21", "可用考纲", true, 3L),
            new SyllabusVersionOptionVo("22", "空树", true, 0L),
            new SyllabusVersionOptionVo("23", "未发布", false, 5L)
        ));
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectExamSubjectOptionsByCertification(9L)).thenReturn(
            List.of(new ExamSubjectOptionVo("11", 1, "综合知识")));
        when(repository.selectReplaceableTextbookDrafts(21L, 7L)).thenReturn(List.of());

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var empty = service.textbookContextOptions(null);
            var selected = service.textbookContextOptions("21");

            assertThat(empty.syllabusVersions()).extracting(SyllabusVersionOptionVo::id)
                .containsExactly("21", "22", "23");
            assertThat(selected.examSubjects()).containsExactly(new ExamSubjectOptionVo("11", 1, "综合知识"));
            assertThat(selected.defaultSubjectMappings().getFirst().subjectNo()).isEqualTo(1);
            assertThat(selected.defaultSubjectMappings().getFirst().examSubjectId()).isEqualTo("11");
        }
    }

    @Test
    void acceptsSelectingATextbookSyllabusWithoutARealKnowledgeTree() {
        when(repository.selectSyllabusOptions()).thenReturn(List.of(
            new SyllabusVersionOptionVo("21", "空树", true, 0L)
        ));

        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectExamSubjectOptionsByCertification(9L)).thenReturn(List.of());

        assertThat(service.textbookContextOptions("21").syllabusVersions())
            .extracting(SyllabusVersionOptionVo::id).containsExactly("21");
    }

    @Test
    void dispatchesQuestionAndTextbookValidationToTheirSpecificQueues() {
        when(repository.selectVisible(101L, 7L)).thenReturn(questionBatch(101L, "uploaded", 0, 0));
        when(repository.selectVisible(102L, 7L)).thenReturn(textbookBatch(102L, "uploaded", 0, 0));
        when(repository.selectById(101L)).thenReturn(questionBatch(101L, "parsing", 0, 0));
        when(repository.selectById(102L)).thenReturn(textbookBatch(102L, "parsing", 0, 0));
        when(persistence.enqueueQuestionValidation(101L)).thenReturn(true);
        when(persistence.enqueueTextbookValidation(102L)).thenReturn(true);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);

            assertThat(service.validate("101").accepted()).isTrue();
            assertThat(service.validate("102").accepted()).isTrue();
        }

        verify(persistence).enqueueQuestionValidation(101L);
        verify(persistence).enqueueTextbookValidation(102L);
        verify(persistence, never()).enqueueValidation(anyLong());
    }

    @Test
    void rejectsUnresolvedKnowledgeDiffsAndInvalidTextbookConfirmationState() {
        CmImportBatch knowledge = new CmImportBatch(
            101L, "knowledge-upload", null, 21L, null, "imports/knowledge.jsonl", "hash", 100L,
            "knowledge_point", "knowledge_point/1.0", "{}", "waiting_confirm", "finalize_counts",
            BigDecimal.ONE, 2, 0, 0, 0, "trace-101",
            OffsetDateTime.parse("2026-08-03T09:00:00+08:00"), null, null, 7L, 8L,
            "baseline", "resolution"
        );
        CmImportBatch textbook = textbookBatch(102L, "waiting_confirm", 1, 1);
        when(repository.selectVisible(101L, 7L)).thenReturn(knowledge);
        when(repository.selectVisible(102L, 7L)).thenReturn(textbook);
        when(repository.countPendingKnowledgeImportDiffs(101L)).thenReturn(1L);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);

            assertThatThrownBy(() -> service.confirm("101", "knowledge-confirm"))
                .isInstanceOfSatisfying(ImportException.class, exception ->
                    assertThat(exception.errorCode()).isEqualTo("KNOWLEDGE_DIFF_UNRESOLVED"));
            assertThatThrownBy(() -> service.confirm("102", UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(ImportException.class, exception ->
                    assertThat(exception.errorCode()).isEqualTo("IMPORT_BATCH_STATE_INVALID"));
        }

        verify(persistence, never()).acceptConfirmation(anyLong(), anyString(), anyString(), anyLong(), any(), anyString());
        verify(persistence, never()).acceptTextbookConfirmation(anyLong(), anyString(), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void replacesAnExistingDraftTextbookUsingTheTargetMetadata() {
        ImportCreateBo form = new ImportCreateBo();
        form.setFile(new MockMultipartFile("file", "replacement.jsonl", "application/jsonl", textbookContent()));
        form.setImportType("document_chunk");
        form.setTemplateVersion("document_chunk/1.0");
        form.setMode("replace_draft");
        form.setDocumentId("201");
        form.setSyllabusVersionId("21");
        form.setSubjectMappings("[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]");
        when(repository.selectTextbookDocument(201L, 7L)).thenReturn(
            new TextbookImportDocument(201L, 21L, "原教材", "第二版", "draft", "0", 7L, 8L, 9L)
        );
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);

        String requestId = UUID.randomUUID().toString();
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);

            var result = service.createTextbook(form, requestId);

            assertThat(result.documentId()).isEqualTo("201");
            assertThat(result.mode()).isEqualTo("replace_draft");
        }

        verify(persistence).createTextbook(anyLong(), eq(201L), eq(false), eq(requestId), anyString(),
            eq(9L), eq(21L), eq("原教材"), eq("第二版"), anyString(), anyString(), eq("replacement.jsonl"),
            eq((long) form.getFile().getSize()), eq("application/jsonl"), anyString(), eq(7L), eq(8L), any(), anyString());
    }

    @Test
    void refusesPaperConfirmationWhenPrecheckContainsWarnings() {
        when(repository.selectVisible(101L, 7L)).thenReturn(paperBatch("waiting_confirm", 3, 0, 1));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);

            assertThatThrownBy(() -> service.confirmPaper("101", UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(ImportException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(422);
                    assertThat(exception.errorCode()).isEqualTo("PAPER_IMPORT_PRECHECK_FAILED");
                });
        }

        verify(persistence, never()).acceptPaperConfirmation(anyLong(), anyString(), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void createsATextbookImportBatchAfterValidatingItsDocumentAndMappings() {
        byte[] content = textbookContent();
        ImportCreateBo form = new ImportCreateBo();
        form.setFile(new MockMultipartFile("file", "book.jsonl", "application/jsonl", content));
        form.setImportType("document_chunk");
        form.setTemplateVersion("document_chunk/1.0");
        form.setMode("create");
        form.setSyllabusVersionId("21");
        form.setTitle("教材");
        form.setEdition("第一版");
        form.setSubjectMappings("[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]");
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);

        String requestId = UUID.randomUUID().toString();
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);

            var result = service.createTextbook(form, requestId);

            assertThat(result.importType()).isEqualTo("document_chunk");
            assertThat(result.mode()).isEqualTo("create");
            assertThat(result.documentId()).isNull();
            assertThat(result.reused()).isFalse();
        }

        verify(storage).upload(org.mockito.ArgumentMatchers.contains("imports/document-chunk/"), any());
        verify(persistence).createTextbook(anyLong(), isNull(), eq(true), eq(requestId), anyString(), eq(9L), eq(21L),
            eq("教材"), eq("第一版"), anyString(), anyString(), eq("book.jsonl"), eq((long) content.length),
            eq("application/jsonl"), anyString(), eq(7L), eq(8L), any(), anyString());
    }

    @Test
    void createsQualificationScopedTextbookWithoutSyllabus() {
        byte[] content = textbookContent();
        ImportCreateBo form = new ImportCreateBo();
        form.setFile(new MockMultipartFile("file", "book.jsonl", "application/jsonl", content));
        form.setImportType("document_chunk");
        form.setTemplateVersion("document_chunk/1.0");
        form.setMode("create");
        form.setCertificationId("9");
        form.setTitle("资格级教材");
        form.setSubjectMappings("[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]");
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);

        String requestId = UUID.randomUUID().toString();
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);

            assertThat(service.createTextbook(form, requestId).status()).isEqualTo("uploaded");
        }

        verify(persistence).createTextbook(anyLong(), isNull(), eq(true), eq(requestId), anyString(),
            eq(9L), isNull(), eq("资格级教材"), isNull(), anyString(), anyString(), eq("book.jsonl"),
            eq((long) content.length), eq("application/jsonl"), anyString(), eq(7L), eq(8L), any(), anyString());
    }

    @Test
    void preservesTheOriginalDatabaseFailureWhenItIsNotAnIdempotencyRace() {
        byte[] content = textbookContent();
        ImportCreateBo form = new ImportCreateBo();
        form.setFile(new MockMultipartFile("file", "book.jsonl", "application/jsonl", content));
        form.setImportType("document_chunk");
        form.setTemplateVersion("document_chunk/1.0");
        form.setMode("create");
        form.setCertificationId("9");
        form.setTitle("资格级教材");
        form.setSubjectMappings("[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]");
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("context constraint"))
            .when(persistence).createTextbook(anyLong(), anyLong(), eq(true), anyString(), anyString(),
                eq(9L), isNull(), eq("资格级教材"), isNull(), anyString(), anyString(), eq("book.jsonl"),
                eq((long) content.length), eq("application/jsonl"), anyString(), eq(7L), eq(8L), any(), anyString());

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);

            assertThatThrownBy(() -> service.createTextbook(form, UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(ImportException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("IMPORT_SYSTEM_FAILURE");
                    assertThat(exception.traceId()).isNotBlank();
                });
        }
    }

    @Test
    void createsAPaperImportBatchWithAutomaticallyDerivedSubjects() throws Exception {
        PaperImportCreateBo form = new PaperImportCreateBo();
        form.setFile(new MockMultipartFile("file", "paper.zip", "application/zip", questionZip()));
        form.setCollectionName("模拟题集");
        form.setCollectionType("PRACTICE");
        form.setDurationMinutes(90);
        form.setCertificationId("9");
        when(repository.selectLatestSyllabusForCertification(9L)).thenReturn(21L);
        when(repository.selectExamSubjectOptions(21L)).thenReturn(
            List.of(new ExamSubjectOptionVo("11", 1, "综合知识")));
        when(repository.selectCertificationNameBySyllabus(21L)).thenReturn("系统架构设计师");

        String requestId = UUID.randomUUID().toString();
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getDeptId).thenReturn(8L);

            var result = service.createPaper(form, requestId);

            assertThat(result.importType()).isEqualTo("paper");
            assertThat(result.derivedSubjects()).extracting(subject -> subject.subjectNo()).containsExactly(1);
        }

        verify(storage).upload(org.mockito.ArgumentMatchers.contains("imports/paper/"), any());
        verify(persistence).createPaper(anyLong(), eq(requestId), anyString(), eq(21L), eq(9L), eq("模拟题集"),
            eq("PRACTICE"), eq(90), anyString(), anyString(), eq("paper.zip"), anyLong(), eq("application/zip"),
            anyString(), eq(7L), eq(8L), any(), anyString());
    }

    @Test
    void validatesConfirmsAndReportsPaperImportProgress() {
        CmImportBatch uploaded = paperBatch("uploaded", 0, 0, 0);
        CmImportBatch validating = paperBatch("validating", 0, 0, 0);
        CmImportBatch waiting = paperBatch("waiting_confirm", 3, 0, 0);
        CmImportBatch importing = paperBatch("importing", 3, 0, 0);
        CmImportBatch failed = paperBatch("failed", 2, 1, 0);
        when(repository.selectVisible(101L, 7L)).thenReturn(uploaded, waiting, failed);
        when(repository.selectById(101L)).thenReturn(validating, importing);
        when(repository.selectPaperImport(101L)).thenReturn(new PaperImportRow(101L, 9L, "模拟题集", "PRACTICE", 90, 301L, 302L));
        when(repository.selectSyllabusDisplayName(21L)).thenReturn("系统架构设计师 · 第二版");

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            var validation = service.validatePaper("101", UUID.randomUUID().toString());
            var confirmation = service.confirmPaper("101", UUID.randomUUID().toString());
            var progress = service.paperProgress("101");

            assertThat(validation.accepted()).isTrue();
            assertThat(confirmation.accepted()).isTrue();
            assertThat(progress.collectionId()).isEqualTo("301");
            assertThat(progress.revisionId()).isEqualTo("302");
            assertThat(progress.failureTraceId()).isEqualTo("trace-101");
        }

        verify(persistence).acceptPaperValidation(eq(101L), anyString(), anyString(), anyString());
        verify(persistence).acceptPaperConfirmation(eq(101L), anyString(), anyString(), eq(7L), any(), anyString());
    }

    @Test
    void listsKnowledgeDifferencesAndApprovesAnExistingUpdate() {
        CmImportBatch batch = batch("waiting_confirm");
        KnowledgeImportDiffRow row = new KnowledgeImportDiffRow();
        row.setId(301L);
        row.setImportBatchId(101L);
        row.setImportRecordId(401L);
        row.setOldKnowledgePointId(202L);
        row.setSuggestedKnowledgePointId(202L);
        row.setExamSubjectId(11L);
        row.setSubjectName("综合知识");
        row.setAction("update");
        row.setResolutionStatus("pending");
        row.setMatchScore(BigDecimal.valueOf(90));
        row.setMatchEvidence("{\"title_score\":\"90.00\",\"parent_score\":\"80.00\",\"description_score\":\"70.00\",\"children_score\":\"60.00\",\"number_score\":\"50.00\",\"total_score\":\"85.00\",\"candidate_gap\":\"10.00\",\"possible_move\":true,\"ambiguous\":false}");
        row.setChangedFields("{\"fields\":[\"syllabusTitle\"]}");
        row.setOldNumber("1.1");
        row.setOldTitle("旧标题");
        row.setNewNumber("1.1");
        row.setNewTitle("新标题");
        row.setSuggestedNumber("1.1");
        row.setSuggestedTitle("建议标题");
        when(repository.selectVisible(101L, 7L)).thenReturn(batch, batch);
        when(repository.selectKnowledgeImportDiffs(eq(101L), eq(null), eq(null), eq(null), eq(false), eq(null), eq(null), eq(20), eq(0L)))
            .thenReturn(List.of(row));
        when(repository.countKnowledgeImportDiffs(eq(101L), eq(null), eq(null), eq(null), eq(false), eq(null), eq(null))).thenReturn(1L);
        when(repository.selectKnowledgeImportDiffCounts(101L)).thenReturn(new KnowledgeImportDiffCounts(0, 1, 0, 0, 0, 1, 1, 0));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            var page = service.knowledgeDiff("101", new KnowledgeImportDiffQueryBo());
            assertThat(page.rows()).singleElement().satisfies(item -> {
                assertThat(item.action()).isEqualTo("update");
                assertThat(item.changedFields()).containsExactly("syllabusTitle");
            });

            KnowledgeImportDiffResolutionBo command = new KnowledgeImportDiffResolutionBo();
            command.setDecision("approve");
            when(repository.selectIdempotency(anyString(), anyString())).thenReturn(null);
            when(repository.selectKnowledgeImportDiffForUpdate(101L, 301L)).thenReturn(row);
            KnowledgeImportPointRow old = new KnowledgeImportPointRow();
            old.setKnowledgePointId(202L);
            old.setExamSubjectId(11L);
            when(repository.selectCurrentKnowledgePoint(21L, 202L)).thenReturn(old);
            when(repository.countConfirmedKnowledgePointUse(101L, 202L, 301L)).thenReturn(0L);
            when(repository.selectPendingDeleteDiffForOldForUpdate(101L, 202L)).thenReturn(null);
            when(repository.updateKnowledgeImportDiffResolution(101L, 301L, "update", 202L, "approve", 7L)).thenReturn(1);
            when(reconciliationService.currentResolutionHash(101L)).thenReturn("resolution-hash");
            when(repository.updateKnowledgeImportResolutionHash(101L, "resolution-hash")).thenReturn(1);
            when(repository.countPendingKnowledgeImportDiffs(101L)).thenReturn(0L);
            when(repository.completeIdempotency(anyString(), anyString(), eq(101L), anyString())).thenReturn(1);

            var resolution = service.resolveKnowledgeDiff("101", "301", command, UUID.randomUUID().toString());
            assertThat(resolution.resolvedCount()).isEqualTo(1);
            assertThat(resolution.resolutionHash()).isEqualTo("resolution-hash");
        }
    }

    @Test
    void rejectsOversizedAndDuplicateKnowledgeResolutionBatchesBeforeApplyingChanges() {
        when(repository.selectVisible(101L, 7L)).thenReturn(batch("waiting_confirm"), batch("waiting_confirm"));

        KnowledgeImportDiffBatchResolutionBo oversized = new KnowledgeImportDiffBatchResolutionBo();
        List<KnowledgeImportDiffResolutionBo> resolutions = new java.util.ArrayList<>();
        for (int index = 0; index < 501; index++) {
            KnowledgeImportDiffResolutionBo resolution = new KnowledgeImportDiffResolutionBo();
            resolution.setDiffId(String.valueOf(index + 1));
            resolution.setDecision("accept_suggestion");
            resolutions.add(resolution);
        }
        oversized.setResolutions(resolutions);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThatThrownBy(() -> service.resolveKnowledgeDiffBatch("101", oversized, "batch-request"))
                .isInstanceOfSatisfying(ImportException.class, exception ->
                    assertThat(exception.errorCode()).isEqualTo("KNOWLEDGE_DIFF_BATCH_TOO_LARGE"));

            KnowledgeImportDiffResolutionBo first = new KnowledgeImportDiffResolutionBo();
            first.setDiffId("301");
            first.setDecision("accept_suggestion");
            KnowledgeImportDiffResolutionBo duplicate = new KnowledgeImportDiffResolutionBo();
            duplicate.setDiffId("301");
            duplicate.setDecision("delete");
            KnowledgeImportDiffBatchResolutionBo duplicated = new KnowledgeImportDiffBatchResolutionBo();
            duplicated.setResolutions(List.of(first, duplicate));
            KnowledgeImportDiffRow diff = new KnowledgeImportDiffRow();
            diff.setId(301L);
            diff.setImportBatchId(101L);
            diff.setImportRecordId(401L);
            diff.setExamSubjectId(11L);
            diff.setAction("add");
            diff.setResolutionStatus("pending");
            when(repository.selectKnowledgeImportDiffForUpdate(101L, 301L)).thenReturn(diff);

            assertThatThrownBy(() -> service.resolveKnowledgeDiffBatch("101", duplicated, "duplicate-request"))
                .isInstanceOfSatisfying(ImportException.class, exception ->
                    assertThat(exception.errorCode()).isEqualTo("KNOWLEDGE_DIFF_MAPPING_CONFLICT"));
        }

        verify(repository, times(1)).selectKnowledgeImportDiffForUpdate(101L, 301L);
    }

    @Test
    void edgeRejectsEmptyDuplicateAndCrossCertificationMappings() {
        MultipartFile file = new MockMultipartFile("file", "tree.jsonl", "application/jsonl", "{}".getBytes());
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        org.mockito.Mockito.lenient().when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        org.mockito.Mockito.lenient().when(repository.selectSubjectCertification(12L)).thenReturn(10L);

        assertThatThrownBy(() -> service.create(file, "r1", "knowledge_point", "21", "knowledge_point/1.0", "[]")).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> service.create(file, "r2", "knowledge_point", "21", "knowledge_point/1.0",
            "[{\"subjectNo\":1,\"examSubjectId\":\"11\"},{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"))
            .isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> service.create(file, "r3", "knowledge_point", "21", "knowledge_point/1.0",
            "[{\"subjectNo\":1,\"examSubjectId\":\"12\"}]"))
            .isInstanceOf(ImportException.class);
    }

    private static CmImportBatch batch(String status) {
        return new CmImportBatch(
            101L, "upload-1", 21L, null, "imports/source.jsonl", "hash", 100L,
            "knowledge_point", "knowledge_point/1.0", "{}", status, "read_jsonl",
            BigDecimal.ZERO, 0, 0, 0, null,
            OffsetDateTime.parse("2026-07-31T09:00:00+08:00"), null, null, 7L, 8L
        );
    }

    private static CmImportBatch questionBatch(String status, int validCount, int failedCount) {
        return questionBatch(101L, status, validCount, failedCount);
    }

    private static CmImportBatch questionBatch(long id, String status, int validCount, int failedCount) {
        return new CmImportBatch(
            id, "question-upload-1", 21L, null, "imports/question/source.zip", "hash", 100L,
            "question", "question-zip/1.0", "{\"schema_version\":\"question_zip/2.0\"}", status, "finalize_counts",
            BigDecimal.ONE, validCount, 0, failedCount, null,
            OffsetDateTime.parse("2026-08-03T09:00:00+08:00"), null, null, 7L, 8L
        );
    }

    private static CmImportBatch textbookBatch(long id, String status, int validCount, int failedCount) {
        return new CmImportBatch(
            id, "textbook-upload-1", 201L, 21L, null, "imports/textbook/source.jsonl", "hash", 100L,
            "document_chunk", "document_chunk/1.0", "{}", status, "finalize_counts", BigDecimal.ONE,
            validCount, 0, failedCount, 0, "trace-" + id,
            OffsetDateTime.parse("2026-08-03T09:00:00+08:00"), null, null, 7L, 8L, null, null
        );
    }

    private static CmImportBatch paperBatch(String status, int validCount, int failedCount, int warningCount) {
        return new CmImportBatch(
            101L, "paper-upload-1", 21L, null, "imports/paper/source.zip", "hash", 100L,
            "paper", "question-zip/1.0", "{}", status, "paper_precheck",
            BigDecimal.valueOf(100), validCount, warningCount, failedCount, "trace-101",
            OffsetDateTime.parse("2026-08-03T09:00:00+08:00"), null, null, 7L, 8L
        );
    }

    private static byte[] textbookContent() {
        return ("{}\n" + "x".repeat(1_048_577)).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] questionZip() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("questions.jsonl"));
            zip.write("{\"knowledge_points\":[{\"subject_no\":1,\"code\":\"K1\"}]}\n"
                .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
