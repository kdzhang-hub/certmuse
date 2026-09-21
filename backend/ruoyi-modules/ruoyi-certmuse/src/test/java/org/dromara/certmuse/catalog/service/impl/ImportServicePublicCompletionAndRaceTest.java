package org.dromara.certmuse.catalog.service.impl;

import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffRow;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.dromara.certmuse.catalog.domain.bo.ImportCreateBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffBatchResolutionBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffResolutionBo;
import org.dromara.certmuse.catalog.domain.bo.PaperImportCreateBo;
import org.dromara.certmuse.catalog.domain.vo.ExamSubjectOptionVo;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.catalog.support.ImportJsonDocumentFactory;
import org.dromara.certmuse.catalog.support.ImportProtocol;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Public completion, recovery and idempotency behaviour for the content-import service.
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportServicePublicCompletionAndRaceTest {

    private static final long BATCH_ID = 101L;

    @Mock
    private ImportMapper repository;
    @Mock
    private ImportStorage storage;
    @Mock
    private ImportPersistenceService persistence;
    @Mock
    private KnowledgeImportReconciliationService reconciliation;

    private ImportServiceImpl service;

    @BeforeEach
    void setUp() {
        JsonMapper json = JsonMapper.builder().build();
        service = new ImportServiceImpl(
            repository, storage, persistence, json, new ImportJsonDocumentFactory(json), reconciliation
        );
    }

    @Test
    void knowledgeCreationReportsMissingQualificationAndCompensatesAnUnexpectedPersistenceFailure() {
        MockMultipartFile file = jsonl("knowledge.jsonl", "application/jsonl", "{}\n");
        when(repository.selectCertificationName(9L)).thenReturn(null);

        assertImportError(
            () -> service.create(
                file, "knowledge-missing-certification", "knowledge_point", "9", null,
                "knowledge_point/1.0", "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"
            ),
            400,
            "IMPORT_CONTEXT_INVALID"
        );
        verify(storage, never()).upload(anyString(), any());

        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        when(storage.deleteQuietly(anyString())).thenReturn(true);
        doThrow(new IllegalStateException("database unavailable"))
            .when(persistence).create(
                anyLong(), eq("knowledge-persistence-failure"), anyString(), eq(21L), anyString(), anyString(),
                eq("knowledge.jsonl"), eq((long) file.getSize()), eq("application/jsonl"), anyString(),
                eq(7L), eq(8L), any(), anyString()
            );

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertImportError(
                () -> service.create(
                    file, "knowledge-persistence-failure", "knowledge_point", null, "21",
                    "knowledge_point/1.0", "[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]"
                ),
                500,
                "IMPORT_SYSTEM_FAILURE"
            );
        }
        verify(storage).deleteQuietly(anyString());
    }

    @Test
    void questionCreationRecoversFromAConcurrentRequestThroughItsPublicContract() throws Exception {
        ImportCreateBo question = questionForm(zip("Q1"));
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectExamSubjectOptionsByCertification(9L)).thenReturn(List.of(subject(1, 11L, "综合知识")));
        AtomicReference<String> questionPayload = new AtomicReference<>();
        doAnswer(invocation -> {
            questionPayload.set(invocation.getArgument(2, String.class));
            throw new DataIntegrityViolationException("question idempotency race");
        }).when(persistence).createQuestion(
            anyLong(), eq("question-race"), anyString(), eq(null), eq(9L), anyString(), anyString(),
            eq("questions.zip"), anyLong(), eq("application/zip"), anyString(), eq(7L), eq(8L), any(), anyString()
        );
        when(repository.selectIdempotency(ImportProtocol.QUESTION_CREATE_ACTION, "question-race"))
            .thenAnswer(invocation -> questionPayload.get() == null
                ? null
                : new CmIdempotencyRecord(questionPayload.get(), "succeeded", BATCH_ID, 200, "{}"));
        when(repository.selectById(BATCH_ID)).thenReturn(batch("question", "uploaded", null, 7L, 0, 0, 0));
        when(storage.deleteQuietly(anyString())).thenReturn(true);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var replay = service.createQuestion(question, "question-race");
            assertThat(replay.reused()).isTrue();
            assertThat(replay.importType()).isEqualTo("question");
        }

        verify(storage).deleteQuietly(anyString());
    }

    @Test
    void textbookCreationRecoversFromAConcurrentRequestThroughItsPublicContract() throws Exception {
        ImportCreateBo textbook = textbookForm();
        String textbookRequestId = UUID.randomUUID().toString();
        AtomicReference<String> textbookPayload = new AtomicReference<>();
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");
        when(repository.selectSubjectCertification(11L)).thenReturn(9L);
        doAnswer(invocation -> {
            textbookPayload.set(invocation.getArgument(4, String.class));
            throw new DataIntegrityViolationException("textbook idempotency race");
        }).when(persistence).createTextbook(
            anyLong(), eq(null), eq(true), eq(textbookRequestId), anyString(), eq(9L), eq(null), eq("教材"),
            eq("第一版"), anyString(), anyString(), eq("book.jsonl"), anyLong(), eq("application/jsonl"),
            anyString(), eq(7L), eq(8L), any(), anyString()
        );
        when(repository.selectIdempotency(ImportProtocol.TEXTBOOK_CREATE_ACTION, textbookRequestId))
            .thenAnswer(invocation -> textbookPayload.get() == null
                ? null
                : new CmIdempotencyRecord(textbookPayload.get(), "succeeded", BATCH_ID, 200, "{}"));
        when(repository.selectById(BATCH_ID)).thenReturn(textbookBatch("uploaded", 201L));
        when(storage.deleteQuietly(anyString())).thenReturn(true);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var replay = service.createTextbook(textbook, textbookRequestId);
            assertThat(replay.reused()).isTrue();
            assertThat(replay.importType()).isEqualTo("document_chunk");
        }
        verify(storage).deleteQuietly(anyString());
    }

    @Test
    void confirmationMapsUnexpectedFailuresAndPaperOperationsReturnDurableAcceptances() throws Exception {
        CmImportBatch question = batch("question", "waiting_confirm", null, 7L, 1, 0, 0);
        when(repository.selectVisible(BATCH_ID, 7L)).thenReturn(question);
        when(repository.selectIdempotency(ImportProtocol.QUESTION_CONFIRM_ACTION, "question-confirm-failure"))
            .thenReturn(null);
        doThrow(new IllegalStateException("confirmation persistence unavailable"))
            .when(persistence).acceptQuestionConfirmation(
                eq(BATCH_ID), eq("question-confirm-failure"), anyString(), eq(7L), any(), anyString()
            );

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertImportError(() -> service.confirm("101", "question-confirm-failure"), 500, "IMPORT_SYSTEM_FAILURE");
        }

        CmImportBatch uploadedPaper = batch("paper", "uploaded", null, 7L, 0, 0, 0);
        CmImportBatch validatingPaper = batch("paper", "validating", null, 7L, 0, 0, 0);
        CmImportBatch confirmedPaper = batch("paper", "waiting_confirm", null, 7L, 2, 0, 0);
        when(repository.selectVisible(BATCH_ID, 7L)).thenReturn(uploadedPaper, confirmedPaper);
        when(repository.selectIdempotency(ImportProtocol.PAPER_VALIDATE_ACTION, "paper-validate"))
            .thenReturn(null);
        when(repository.selectIdempotency(ImportProtocol.PAPER_CONFIRM_ACTION, "paper-confirm"))
            .thenReturn(null);
        when(repository.selectById(BATCH_ID)).thenReturn(validatingPaper, confirmedPaper);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var validated = service.validatePaper("101", "paper-validate");
            var confirmed = service.confirmPaper("101", "paper-confirm");

            assertThat(validated.accepted()).isTrue();
            assertThat(validated.status()).isEqualTo("validating");
            assertThat(confirmed.accepted()).isTrue();
            assertThat(confirmed.status()).isEqualTo("waiting_confirm");
        }
        verify(persistence).acceptPaperValidation(eq(BATCH_ID), eq("paper-validate"), anyString(), anyString());
        verify(persistence).acceptPaperConfirmation(eq(BATCH_ID), eq("paper-confirm"), anyString(), eq(7L), any(), anyString());
    }

    @Test
    void paperValidationReplaysTheSamePublicRequestAndResolutionReturnsTheDurableSummary() throws Exception {
        CmImportBatch paper = batch("paper", "uploaded", null, 7L, 0, 0, 0);
        String validationHash = confirmationHash(BATCH_ID, ImportProtocol.PAPER_VALIDATE_ACTION);
        when(repository.selectVisible(BATCH_ID, 7L)).thenReturn(paper);
        when(repository.selectIdempotency(ImportProtocol.PAPER_VALIDATE_ACTION, "paper-replay"))
            .thenReturn(new CmIdempotencyRecord(validationHash, "succeeded", BATCH_ID, 200, "{}"));
        when(repository.selectById(BATCH_ID)).thenReturn(batch("paper", "validating", null, 7L, 0, 0, 0));

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var replay = service.validatePaper("101", "paper-replay");
            assertThat(replay.accepted()).isTrue();
            assertThat(replay.status()).isEqualTo("validating");
        }
        verify(persistence, never()).acceptPaperValidation(anyLong(), anyString(), anyString(), anyString());

        CmImportBatch knowledge = batch("knowledge_point", "waiting_confirm", null, 7L, 1, 0, 0);
        KnowledgeImportDiffRow add = diff("add", "pending", null, 201L, 11L);
        when(repository.selectVisible(BATCH_ID, 7L)).thenReturn(knowledge);
        when(repository.selectIdempotency(ImportProtocol.KNOWLEDGE_DIFF_BATCH_RESOLVE_ACTION, "resolution-complete"))
            .thenReturn(null);
        when(repository.selectKnowledgeImportDiffForUpdate(BATCH_ID, 301L)).thenReturn(add);
        when(repository.countConfirmedKnowledgePointUse(BATCH_ID, 201L, 301L)).thenReturn(0L);
        when(repository.selectPendingDeleteDiffForOldForUpdate(BATCH_ID, 201L)).thenReturn(null);
        when(repository.insertKnowledgeImportDiffs(any())).thenReturn(1);
        when(repository.updateKnowledgeImportDiffResolution(BATCH_ID, 301L, "add", null, "approve", 7L)).thenReturn(1);
        when(reconciliation.currentResolutionHash(BATCH_ID)).thenReturn("resolution-hash");
        when(repository.updateKnowledgeImportResolutionHash(BATCH_ID, "resolution-hash")).thenReturn(1);
        when(repository.countPendingKnowledgeImportDiffs(BATCH_ID)).thenReturn(1L);
        when(repository.completeIdempotency(
            eq(ImportProtocol.KNOWLEDGE_DIFF_BATCH_RESOLVE_ACTION), eq("resolution-complete"), eq(BATCH_ID), anyString()
        )).thenReturn(1);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var result = service.resolveKnowledgeDiffBatch("101", resolutions(decision("301", "approve")), "resolution-complete");
            assertThat(result.batchId()).isEqualTo("101");
            assertThat(result.resolvedCount()).isEqualTo(1);
            assertThat(result.pendingCount()).isEqualTo(1L);
            assertThat(result.resolutionHash()).isEqualTo("resolution-hash");
        }
        ArgumentCaptor<List> inserts = ArgumentCaptor.forClass(List.class);
        verify(repository).insertKnowledgeImportDiffs(inserts.capture());
        assertThat(inserts.getValue()).hasSize(1);
    }

    private static ImportCreateBo questionForm(MockMultipartFile file) {
        ImportCreateBo form = new ImportCreateBo();
        form.setImportType("question");
        form.setTemplateVersion("question-zip/1.0");
        form.setCertificationId("9");
        form.setFile(file);
        return form;
    }

    private static ImportCreateBo textbookForm() {
        ImportCreateBo form = new ImportCreateBo();
        form.setImportType("document_chunk");
        form.setTemplateVersion("document_chunk/1.0");
        form.setMode("create");
        form.setCertificationId("9");
        form.setTitle("教材");
        form.setEdition("第一版");
        form.setSubjectMappings("[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]");
        form.setFile(jsonl("book.jsonl", "application/jsonl", "{}\n" + "x".repeat(1_048_577)));
        return form;
    }

    private static MockMultipartFile jsonl(String name, String mime, String content) {
        return new MockMultipartFile("file", name, mime, content.getBytes(StandardCharsets.UTF_8));
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

    private static ExamSubjectOptionVo subject(int no, long id, String label) {
        return new ExamSubjectOptionVo(Long.toString(id), no, label);
    }

    private static CmImportBatch batch(
        String type, String status, Long documentId, long owner, int valid, int warnings, int failed
    ) {
        String template = "document_chunk".equals(type) ? "document_chunk/1.0" : "question-zip/1.0";
        return new CmImportBatch(
            BATCH_ID, "request", documentId, 21L, null, "imports/source", "hash", 100L,
            type, template, "{\"mode\":\"create\"}", status, "stage", BigDecimal.TEN,
            valid, warnings, failed, 0, "trace", OffsetDateTime.parse("2026-08-13T10:00:00+08:00"),
            null, null, owner, 8L, "baseline", "resolution", 9L
        );
    }

    private static CmImportBatch textbookBatch(String status, long documentId) {
        CmImportBatch base = batch("document_chunk", status, documentId, 7L, 0, 0, 0);
        return new CmImportBatch(
            base.id(), base.requestId(), base.documentId(), base.syllabusVersionId(), base.examSubjectId(),
            base.sourceFilePath(), base.sourceFileHash(), base.sourceFileSize(), base.importType(),
            base.templateVersion(), "{\"mode\":\"create\"}", base.status(), base.currentStage(), base.progressPercent(),
            base.validCount(), base.warningCount(), base.failedCount(), base.generatedCount(), base.traceId(),
            base.createTime(), base.startedTime(), base.finishedTime(), base.createBy(), base.createDept(),
            base.baselineHash(), base.resolutionHash(), base.certificationId()
        );
    }

    private static KnowledgeImportDiffRow diff(
        String action, String resolutionStatus, Long oldId, Long suggestedId, long subjectId
    ) {
        KnowledgeImportDiffRow row = new KnowledgeImportDiffRow();
        row.setId(301L);
        row.setImportBatchId(BATCH_ID);
        row.setExamSubjectId(subjectId);
        row.setAction(action);
        row.setResolutionStatus(resolutionStatus);
        row.setOldKnowledgePointId(oldId);
        row.setSuggestedKnowledgePointId(suggestedId);
        return row;
    }

    private static KnowledgeImportDiffResolutionBo decision(String id, String decision) {
        KnowledgeImportDiffResolutionBo command = new KnowledgeImportDiffResolutionBo();
        command.setDiffId(id);
        command.setDecision(decision);
        return command;
    }

    private static KnowledgeImportDiffBatchResolutionBo resolutions(KnowledgeImportDiffResolutionBo... decisions) {
        KnowledgeImportDiffBatchResolutionBo batch = new KnowledgeImportDiffBatchResolutionBo();
        batch.setResolutions(List.of(decisions));
        return batch;
    }

    private static String confirmationHash(long batchId, String action) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
            (action + "\n" + batchId + "\n").getBytes(StandardCharsets.UTF_8)
        ));
    }

    private static MockedStatic<LoginHelper> ordinaryUser() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        login.when(LoginHelper::getUserId).thenReturn(7L);
        login.when(LoginHelper::getDeptId).thenReturn(8L);
        return login;
    }

    private static void assertImportError(ThrowingCall call, int status, String errorCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ImportException.class, error -> {
            assertThat(error.code()).isEqualTo(status);
            assertThat(error.errorCode()).isEqualTo(errorCode);
        });
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
