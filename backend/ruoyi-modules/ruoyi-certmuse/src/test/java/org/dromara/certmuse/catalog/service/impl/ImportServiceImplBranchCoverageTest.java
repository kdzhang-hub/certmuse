package org.dromara.certmuse.catalog.service.impl;

import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportServiceImplBranchCoverageTest {

    @Mock
    private ImportMapper repository;
    @Mock
    private ImportStorage storage;
    @Mock
    private ImportPersistenceService persistence;
    @Mock
    private KnowledgeImportReconciliationService reconciliationService;

    private ImportServiceImpl service;

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
    void validateRejectsFailedBatchesWithoutSchedulingWork() {
        when(repository.selectVisible(101L, 7L)).thenReturn(batch("knowledge_point", "failed", 1, 0));

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            assertThatThrownBy(() -> service.validate("101"))
                .isInstanceOfSatisfying(ImportException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(409);
                    assertThat(exception.errorCode()).isEqualTo("IMPORT_BATCH_STATE_INVALID");
                });
        }

        verify(persistence, never()).enqueueValidation(101L);
        verify(persistence, never()).enqueueQuestionValidation(101L);
        verify(persistence, never()).enqueueTextbookValidation(101L);
    }

    @Test
    void validateMapsSchedulingFailuresToARetryableSystemContract() {
        when(repository.selectVisible(101L, 7L)).thenReturn(batch("knowledge_point", "uploaded", 0, 0));
        when(persistence.enqueueValidation(101L)).thenThrow(new IllegalStateException("queue unavailable"));

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            assertThatThrownBy(() -> service.validate("101"))
                .isInstanceOfSatisfying(ImportException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(500);
                    assertThat(exception.errorCode()).isEqualTo("IMPORT_SYSTEM_FAILURE");
                    assertThat(exception.retryable()).isTrue();
                    assertThat(exception.traceId()).isNotBlank();
                });
        }
    }

    @Test
    void confirmReplaysAnIdenticalQuestionConfirmationWithoutPersistingAgain() throws Exception {
        String requestId = "confirm-question-1";
        CmImportBatch waiting = batch("question", "waiting_confirm", 2, 0);
        String payloadHash = confirmationHash(101L, ImportProtocol.QUESTION_CONFIRM_ACTION, null);
        when(repository.selectVisible(101L, 7L)).thenReturn(waiting);
        when(repository.selectIdempotency(ImportProtocol.QUESTION_CONFIRM_ACTION, requestId))
            .thenReturn(new CmIdempotencyRecord(payloadHash, "succeeded", 101L, 202, "{}"));
        when(repository.selectById(101L)).thenReturn(waiting);

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            var result = service.confirm("101", requestId);

            assertThat(result.id()).isEqualTo("101");
            assertThat(result.status()).isEqualTo("waiting_confirm");
            assertThat(result.accepted()).isFalse();
        }

        verify(persistence, never()).acceptQuestionConfirmation(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void confirmRecoversWhenAConcurrentQuestionConfirmationCreatedTheSameRecord() throws Exception {
        String requestId = "confirm-question-race";
        CmImportBatch waiting = batch("question", "waiting_confirm", 2, 0);
        String payloadHash = confirmationHash(101L, ImportProtocol.QUESTION_CONFIRM_ACTION, null);
        when(repository.selectVisible(101L, 7L)).thenReturn(waiting);
        when(repository.selectIdempotency(ImportProtocol.QUESTION_CONFIRM_ACTION, requestId))
            .thenReturn(null, new CmIdempotencyRecord(payloadHash, "succeeded", 101L, 202, "{}"));
        when(repository.selectById(101L)).thenReturn(waiting);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate request"))
            .when(persistence).acceptQuestionConfirmation(
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(requestId),
                org.mockito.ArgumentMatchers.eq(payloadHash),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
            );

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            var result = service.confirm("101", requestId);

            assertThat(result.id()).isEqualTo("101");
            assertThat(result.accepted()).isFalse();
        }
    }

    @Test
    void confirmRejectsAConcurrentRecordWithDifferentPayload() {
        String requestId = "confirm-question-conflict";
        CmImportBatch waiting = batch("question", "waiting_confirm", 2, 0);
        when(repository.selectVisible(101L, 7L)).thenReturn(waiting);
        when(repository.selectIdempotency(ImportProtocol.QUESTION_CONFIRM_ACTION, requestId))
            .thenReturn(null, new CmIdempotencyRecord("different", "succeeded", 101L, 202, "{}"));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate request"))
            .when(persistence).acceptQuestionConfirmation(
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(requestId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
            );

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            assertThatThrownBy(() -> service.confirm("101", requestId))
                .isInstanceOfSatisfying(ImportException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(409);
                    assertThat(exception.errorCode()).isEqualTo("IMPORT_REQUEST_ID_CONFLICT");
                });
        }
    }

    private static MockedStatic<LoginHelper> ordinaryUser() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        login.when(LoginHelper::getUserId).thenReturn(7L);
        return login;
    }

    private static CmImportBatch batch(String importType, String status, int validCount, int failedCount) {
        return new CmImportBatch(
            101L,
            "upload-1",
            null,
            21L,
            null,
            "imports/source",
            "file-hash",
            100L,
            importType,
            "question".equals(importType) ? "question-zip/1.0" : "knowledge_point/1.0",
            "{}",
            status,
            "precheck",
            BigDecimal.ZERO,
            validCount,
            0,
            failedCount,
            0,
            null,
            OffsetDateTime.parse("2026-08-12T09:00:00+08:00"),
            null,
            null,
            7L,
            8L,
            "baseline",
            "resolution",
            9L
        );
    }

    private static String confirmationHash(long batchId, String action, String resolutionHash) throws Exception {
        String payload = action + "\n" + batchId + "\n" + (resolutionHash == null ? "" : resolutionHash);
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))
        );
    }
}
