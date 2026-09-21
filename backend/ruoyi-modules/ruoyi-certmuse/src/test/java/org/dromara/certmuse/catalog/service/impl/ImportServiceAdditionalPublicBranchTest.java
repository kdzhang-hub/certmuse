package org.dromara.certmuse.catalog.service.impl;

import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.TextbookPreviewRecord;
import org.dromara.certmuse.catalog.domain.bo.CompletedImportBatchQueryBo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchDetailVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchTypeCountsVo;
import org.dromara.certmuse.catalog.domain.vo.ImportIssueVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewDocumentVo;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.ImportService;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.catalog.support.ImportJsonDocumentFactory;
import org.dromara.certmuse.catalog.support.ImportProtocol;
import org.dromara.certmuse.catalog.support.ImportStorage;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportServiceAdditionalPublicBranchTest {

    private static final long BATCH_ID = 101L;

    @Mock
    private ImportMapper repository;
    @Mock
    private ImportStorage storage;
    @Mock
    private ImportPersistenceService persistence;
    @Mock
    private KnowledgeImportReconciliationService reconciliation;

    private ImportService service;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        service = new ImportServiceImpl(
            repository,
            storage,
            persistence,
            jsonMapper,
            new ImportJsonDocumentFactory(jsonMapper),
            reconciliation
        );
    }

    @Test
    void completedBatchesUsesDefaultsAndZeroCountsForAnOrdinaryUser() {
        CompletedImportBatchQueryBo query = new CompletedImportBatchQueryBo();
        query.setKeyword("  教材  ");
        when(repository.selectCompletedBatches(
            "教材", null, null, null, null, null, 7L, false, 20, 0L
        )).thenReturn(List.of());
        when(repository.countCompletedBatches("教材", null, null, null, null, null, 7L)).thenReturn(0L);
        when(repository.selectCompletedTypeCounts(7L)).thenReturn(null);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var result = service.completedBatches(query);
            assertThat(result.getRows()).isEmpty();
            assertThat(result.getTotal()).isZero();
            assertThat(result.getTypeCounts()).isEqualTo(new ImportBatchTypeCountsVo(0, 0, 0, 0));
        }
    }

    @Test
    void completedBatchesMapsInclusiveBusinessDatesAndAscendingOffsetForSuperAdmin() {
        CompletedImportBatchQueryBo query = new CompletedImportBatchQueryBo();
        query.setImportType("question");
        query.setCompletedStartDate(LocalDate.of(2026, 8, 1));
        query.setCompletedEndDate(LocalDate.of(2026, 8, 12));
        query.setSyllabusVersionId("21");
        query.setUploaderId("7");
        query.setPageNum(2);
        query.setPageSize(5);
        query.setOrderByColumn("completedTime");
        query.setIsAsc("asc");
        OffsetDateTime start = OffsetDateTime.parse("2026-08-01T00:00:00+08:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-08-13T00:00:00+08:00");
        when(repository.selectCompletedBatches(
            null, "question", 21L, 7L, start, end, null, true, 5, 5L
        )).thenReturn(List.of());
        when(repository.countCompletedBatches(null, "question", 21L, 7L, start, end, null)).thenReturn(0L);
        when(repository.selectCompletedTypeCounts(null)).thenReturn(new ImportBatchTypeCountsVo(0, 0, 0, 0));

        try (MockedStatic<LoginHelper> ignored = superAdmin()) {
            assertThat(service.completedBatches(query).getRows()).isEmpty();
        }
    }

    @Test
    void completedBatchesRejectsInvalidRangesAndUnsupportedSortContracts() {
        CompletedImportBatchQueryBo reversed = new CompletedImportBatchQueryBo();
        reversed.setCompletedStartDate(LocalDate.of(2026, 8, 12));
        reversed.setCompletedEndDate(LocalDate.of(2026, 8, 1));
        CompletedImportBatchQueryBo badPageSize = new CompletedImportBatchQueryBo();
        badPageSize.setPageSize(0);
        CompletedImportBatchQueryBo badSort = new CompletedImportBatchQueryBo();
        badSort.setOrderByColumn("createTime");
        CompletedImportBatchQueryBo badDirection = new CompletedImportBatchQueryBo();
        badDirection.setIsAsc("sideways");

        for (CompletedImportBatchQueryBo query : List.of(reversed, badPageSize, badSort, badDirection)) {
            assertImportError(() -> service.completedBatches(query), 400, "IMPORT_CONTEXT_INVALID");
        }

        verify(repository, never()).selectCompletedBatches(
            any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt(), anyLong()
        );
    }

    @Test
    void completedBatchCalculatesTotalAndReportsMissingBatch() {
        CompletedImportBatchDetailVo detail = new CompletedImportBatchDetailVo(
            "101", null, "source.jsonl", "knowledge_point", "21", "第二版", "7", "张三",
            "completed", OffsetDateTime.parse("2026-08-12T12:00:00+08:00"),
            99, 3, 1, 2, 4
        );
        when(repository.selectCompletedBatchDetail(BATCH_ID, 7L))
            .thenReturn(detail)
            .thenReturn((CompletedImportBatchDetailVo) null);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var result = service.completedBatch("101");
            assertThat(result.totalCount()).isEqualTo(5);
            assertThat(result.generatedCount()).isEqualTo(4);
            assertImportError(() -> service.completedBatch("101"), 404, "IMPORT_BATCH_NOT_FOUND");
        }
    }

    @Test
    void createRejectsNewSyllabusWhenQualificationAlreadyHasOne() {
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectSyllabusByCertification(9L)).thenReturn(21L);

        assertImportError(
            () -> service.create(
                jsonl("knowledge.jsonl", "application/jsonl", "{}\n"),
                "request-1", "knowledge_point", "9", null, "knowledge_point/1.0",
                "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"
            ),
            400,
            "IMPORT_CONTEXT_INVALID"
        );

        verify(storage, never()).upload(anyString(), any());
    }

    @Test
    void createReportsSystemFailureWhenConcurrentRecordIsStillMissing() {
        MockMultipartFile file = jsonl("knowledge.jsonl", "application/jsonl", "{}\n");
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        when(repository.selectIdempotency(ImportProtocol.KNOWLEDGE_CREATE_ACTION, "request-race"))
            .thenReturn((CmIdempotencyRecord) null)
            .thenReturn((CmIdempotencyRecord) null);
        doThrow(new DataIntegrityViolationException("duplicate request"))
            .when(persistence).create(
                anyLong(), eq("request-race"), anyString(), eq(21L), anyString(), anyString(),
                eq("knowledge.jsonl"), eq((long) file.getSize()), eq("application/jsonl"), anyString(),
                eq(7L), eq(8L), any(), anyString()
            );
        when(storage.deleteQuietly(anyString())).thenReturn(true);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertImportError(
                () -> service.create(
                    file, "request-race", "knowledge_point", null, "21", "knowledge_point/1.0",
                    "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"
                ),
                500,
                "IMPORT_SYSTEM_FAILURE"
            );
        }
    }

    @Test
    void createReplaysExistingRequestOwnedByTheCurrentUserAndRejectsAnotherOwner() throws Exception {
        MockMultipartFile file = jsonl("knowledge.jsonl", "application/jsonl", "{}\n");
        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        String payloadHash = knowledgePayloadHash(file, 21L);
        CmIdempotencyRecord replay = new CmIdempotencyRecord(payloadHash, "succeeded", BATCH_ID, 201, "{}");
        when(repository.selectIdempotency(ImportProtocol.KNOWLEDGE_CREATE_ACTION, "request-replay"))
            .thenReturn(replay);
        when(repository.selectById(BATCH_ID)).thenReturn(batch("knowledge_point", "uploaded", 7L));

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertThat(service.create(
                file, "request-replay", "knowledge_point", null, "21", "knowledge_point/1.0",
                "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"
            ).reused()).isTrue();
        }
        verify(storage, never()).upload(anyString(), any());

        when(repository.selectIdempotency(ImportProtocol.KNOWLEDGE_CREATE_ACTION, "request-owner"))
            .thenReturn(replay);
        when(repository.selectById(BATCH_ID)).thenReturn(batch("knowledge_point", "uploaded", 8L));
        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertImportError(
                () -> service.create(
                    file, "request-owner", "knowledge_point", null, "21", "knowledge_point/1.0",
                    "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"
                ),
                409,
                "IMPORT_REQUEST_ID_CONFLICT"
            );
        }
    }

    @Test
    void progressReportsDiffRequirementOnlyForKnowledgeImportsWithDiffs() {
        CmImportBatch knowledge = batch("knowledge_point", "waiting_confirm", 7L);
        CmImportBatch question = batch("question", "failed", 7L);
        when(repository.selectVisible(BATCH_ID, 7L)).thenReturn(knowledge, question);
        when(repository.countKnowledgeImportDiffs(BATCH_ID, null, null, null, false, null, null)).thenReturn(1L);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var knowledgeProgress = service.progress("101");
            var questionProgress = service.progress("101");
            assertThat(knowledgeProgress.knowledgeDiffRequired()).isTrue();
            assertThat(knowledgeProgress.failureTraceId()).isNull();
            assertThat(questionProgress.knowledgeDiffRequired()).isFalse();
            assertThat(questionProgress.failureTraceId()).isEqualTo("trace-101");
        }
        verify(repository).countKnowledgeImportDiffs(BATCH_ID, null, null, null, false, null, null);
    }

    @Test
    void previewUsesDefaultSummaryAndMapsNullFieldsWithoutKnowledgeLookups() {
        CmImportBatch batch = textbookBatch("completed", "{\"subject_mappings\":[]}");
        TextbookPreviewRecord row = new TextbookPreviewRecord(
            3,
            "chapter-1",
            """
                {"record":{"chunk_no":1,"heading_path":[],"content":null,"knowledge_points":[]}}
                """,
            0
        );
        when(repository.selectVisible(BATCH_ID, 7L)).thenReturn(batch);
        when(repository.selectTextbookPreviewDocument(BATCH_ID))
            .thenReturn(new TextbookImportPreviewDocumentVo("201", "教材", null, null));
        when(repository.selectTextbookPreviewSummary(BATCH_ID)).thenReturn(null);
        when(repository.selectTextbookPreviewRecords(BATCH_ID, 20, 0)).thenReturn(List.of(row));
        when(repository.countTextbookPreviewRecords(BATCH_ID)).thenReturn(1L);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var result = service.preview("101", null, null);
            assertThat(result.summary().totalChunks()).isZero();
            assertThat(result.chunks().getRows()).singleElement().satisfies(chunk -> {
                assertThat(chunk.heading()).isNull();
                assertThat(chunk.contentPreview()).isNull();
                assertThat(chunk.pageStart()).isNull();
                assertThat(chunk.pageEnd()).isNull();
            });
        }
        verify(repository, never()).selectTextbookPreviewKnowledgePointLookups(anyLong(), any(), any());
    }

    @Test
    void previewRejectsUnsupportedTypeStateAndPageBounds() {
        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            when(repository.selectVisible(BATCH_ID, 7L)).thenReturn(
                batch("question", "completed", 7L),
                textbookBatch("uploaded", "{}"),
                textbookBatch("completed", "{}"),
                textbookBatch("completed", "{}")
            );
            assertImportError(() -> service.preview("101", 1, 20), 400, "IMPORT_PREVIEW_UNSUPPORTED");
            assertImportError(() -> service.preview("101", 1, 20), 409, "IMPORT_BATCH_STATE_INVALID");
            assertImportError(() -> service.preview("101", 0, 20), 400, "IMPORT_CONTEXT_INVALID");
            assertImportError(() -> service.preview("101", 1, 101), 400, "IMPORT_CONTEXT_INVALID");
        }
    }

    @Test
    void issuesSupportsAscendingAliasAndDescendingAliasWithExactPagination() {
        ImportIssueVo issue = new ImportIssueVo(
            2, "record", "field", "warning", "W1", "提示",
            OffsetDateTime.parse("2026-08-12T10:00:00+08:00")
        );
        when(repository.selectVisible(BATCH_ID, 7L)).thenReturn(
            batch("question", "waiting_confirm", 7L),
            batch("question", "waiting_confirm", 7L)
        );
        when(repository.selectIssues(BATCH_ID, "warning", "W1", "severity", true, 5, 5))
            .thenReturn(List.of(issue));
        when(repository.countIssues(BATCH_ID, "warning", "W1")).thenReturn(1L);
        when(repository.selectIssues(BATCH_ID, null, null, "createTime", false, 10, 20))
            .thenReturn(List.of());
        when(repository.countIssues(BATCH_ID, null, null)).thenReturn(0L);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var ascending = service.issues("101", 2, 5, "warning", "W1", "severity", "ascending");
            var descending = service.issues("101", 3, 10, null, null, "createTime", "descending");
            assertThat(ascending.getRows()).containsExactly(issue);
            assertThat(descending.getRows()).isEmpty();
        }
    }

    private static MockMultipartFile jsonl(String name, String mime, String content) {
        return new MockMultipartFile("file", name, mime, content.getBytes(StandardCharsets.UTF_8));
    }

    private static CmImportBatch batch(String type, String status, Long owner) {
        return new CmImportBatch(
            BATCH_ID, "request", null, 21L, null, "imports/source", "file-hash", 100L,
            type, "question".equals(type) ? "question-zip/1.0" : "knowledge_point/1.0",
            "{}", status, "stage", BigDecimal.TEN, 3, 1, 2, 0, "trace-101",
            OffsetDateTime.parse("2026-08-12T09:00:00+08:00"), null, null, owner, 8L,
            "baseline", "resolution", 9L
        );
    }

    private static CmImportBatch textbookBatch(String status, String parseConfig) {
        CmImportBatch base = batch("document_chunk", status, 7L);
        return new CmImportBatch(
            base.id(), base.requestId(), 201L, base.syllabusVersionId(), base.examSubjectId(),
            base.sourceFilePath(), base.sourceFileHash(), base.sourceFileSize(), base.importType(),
            "document_chunk/1.0", parseConfig, base.status(), base.currentStage(), base.progressPercent(),
            base.validCount(), base.warningCount(), base.failedCount(), base.generatedCount(), base.traceId(),
            base.createTime(), base.startedTime(), base.finishedTime(), base.createBy(), base.createDept(),
            base.baselineHash(), base.resolutionHash(), base.certificationId()
        );
    }

    private static String knowledgePayloadHash(MockMultipartFile file, long syllabusId) throws Exception {
        String fileHash = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(file.getBytes())
        );
        JsonMapper jsonMapper = JsonMapper.builder().build();
        String config = new ImportJsonDocumentFactory(jsonMapper).flat(
            org.dromara.certmuse.catalog.support.ImportJsonSchema.IMPORT_PARSE_CONFIG,
            java.util.Map.of(
                "subject_mappings",
                List.of(java.util.Map.of("subject_no", 1, "exam_subject_id", 11L))
            )
        );
        String payload = "knowledge_point\n" + syllabusId + "\nknowledge_point/1.0\n" + config + "\n" + fileHash;
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))
        );
    }

    private static MockedStatic<LoginHelper> ordinaryUser() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        login.when(LoginHelper::getUserId).thenReturn(7L);
        login.when(LoginHelper::getDeptId).thenReturn(8L);
        return login;
    }

    private static MockedStatic<LoginHelper> superAdmin() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(true);
        return login;
    }

    private static void assertImportError(ThrowingCall call, int status, String code) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(ImportException.class, exception -> {
                assertThat(exception.code()).isEqualTo(status);
                assertThat(exception.errorCode()).isEqualTo(code);
            });
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
