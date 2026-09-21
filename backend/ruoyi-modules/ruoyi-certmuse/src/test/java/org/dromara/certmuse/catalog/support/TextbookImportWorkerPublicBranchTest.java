package org.dromara.certmuse.catalog.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import org.dromara.certmuse.catalog.config.ImportSchedulingConfiguration;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.TextbookImportDocument;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.ImportProgressPublisher;
import org.dromara.certmuse.catalog.validation.TextbookImportValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TextbookImportWorkerPublicBranchTest {

    private static final long BATCH_ID = 101L;
    private static final long DOCUMENT_ID = 201L;

    @Mock
    private ImportMapper repository;
    @Mock
    private ImportStorage storage;
    @Mock
    private ImportProgressPublisher progressPublisher;

    private TextbookImportWorker worker;

    @BeforeEach
    void setUp() {
        worker = new TextbookImportWorker(
            repository,
            storage,
            new TextbookImportValidator(),
            new ImportSchedulingConfiguration().importStrictJsonMapper(),
            progressPublisher
        );
    }

    @Test
    void wrapsMissingAndWrongTypeBatchesBeforeReadingStorage() {
        when(repository.selectById(BATCH_ID)).thenReturn(null);
        assertPrecheckFailure();

        when(repository.selectById(BATCH_ID)).thenReturn(batch(
            DOCUMENT_ID, 21L, 31L, "question", 100L, parseConfig()
        ));
        assertPrecheckFailure();

        verifyNoInteractions(storage);
        verifyNoInteractions(progressPublisher);
    }

    @Test
    void wrapsMissingDocumentsAndCertificationMismatchesBeforeReadingStorage() {
        when(repository.selectById(BATCH_ID)).thenReturn(batch(
            DOCUMENT_ID, 21L, 31L, "document_chunk", 100L, parseConfig()
        ));
        when(repository.selectTextbookDocument(DOCUMENT_ID, null)).thenReturn(null);
        assertPrecheckFailure();

        when(repository.selectTextbookDocument(DOCUMENT_ID, null)).thenReturn(
            document(21L, 99L)
        );
        assertPrecheckFailure();

        verifyNoInteractions(storage);
        verifyNoInteractions(progressPublisher);
    }

    @Test
    void validatesWithoutADocumentOrSyllabusAndStopsWhenAnotherWorkerWonTheStageTransition() throws Exception {
        CmImportBatch batch = batch(null, null, null, "document_chunk", 200L, emptyParseConfig());
        when(repository.selectById(BATCH_ID)).thenReturn(batch);
        readPayload(row(1, "[]") + "\n");
        when(repository.updateStage(
            BATCH_ID, "parsing", "validating", "validate_schema", new BigDecimal("55.00")
        )).thenReturn(0);

        worker.validate(BATCH_ID);

        verify(repository, never()).selectTextbookDocument(anyLong(), any());
        verify(repository, never()).selectTextbookKnowledgePointLookups(any());
        verify(repository).insertRecord(anyLong(), eq(BATCH_ID), eq(1), eq("chunk-1"), anyString(), anyString());
        verify(repository, never()).updateProgress(anyLong(), anyString(), anyString(), any());
        verify(repository, never()).finishTextbook(anyLong(), anyString());
        verifyNoInteractions(progressPublisher);
    }

    @Test
    void fallsBackToTheBatchSyllabusWhenTheMatchingDocumentHasNoSyllabus() {
        when(repository.selectById(BATCH_ID)).thenReturn(batch(
            DOCUMENT_ID, 21L, 31L, "document_chunk", 100L, parseConfig()
        ));
        when(repository.selectTextbookDocument(DOCUMENT_ID, null)).thenReturn(document(null, 31L));
        when(repository.selectTextbookKnowledgePointLookups(List.of(11L))).thenReturn(List.of());
        readPayload("");
        successfulCompletion();

        worker.validate(BATCH_ID);

        verify(repository).selectTextbookKnowledgePointLookups(List.of(11L));
        verify(repository).finishTextbook(BATCH_ID, "validating");
        verify(progressPublisher, times(4)).publish(BATCH_ID);
    }

    @Test
    void recordsOversizedBlankMalformedAndNonObjectLinesWithoutInsertingRows() {
        when(repository.selectById(BATCH_ID)).thenReturn(batch(
            null, 21L, null, "document_chunk", 2_000_000L, emptyParseConfig()
        ));
        String oversized = "x".repeat(1_048_577);
        String payload = oversized + "\n   \nnot-json\n[]\nnull\n";
        readPayload(payload);
        successfulCompletion();

        worker.validate(BATCH_ID);

        ArgumentCaptor<String> codes = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fields = ArgumentCaptor.forClass(String.class);
        verify(repository, times(4)).createIssue(
            eq(BATCH_ID), isNull(), codes.capture(), fields.capture(), eq("error"), anyString()
        );
        assertThat(codes.getAllValues()).containsOnly("IMPORT_JSON_LINE_INVALID");
        assertThat(fields.getAllValues()).containsExactly("$line[1]", "$line[3]", "$line[4]", "$line[5]");
        verify(repository, never()).insertRecord(anyLong(), anyLong(), anyInt(), any(), anyString(), anyString());
        verify(repository).finishTextbook(BATCH_ID, "validating");
    }

    @Test
    void persistsValidationProblemsMarksFailedRowsAndReportsGapRanges() throws Exception {
        when(repository.selectById(BATCH_ID)).thenReturn(batch(
            null, null, null, "document_chunk", 5_000L, emptyParseConfig()
        ));
        String payload = row(2, "[]") + "\n" + invalidRow(5) + "\n";
        readPayload(payload);
        successfulCompletion();

        worker.validate(BATCH_ID);

        ArgumentCaptor<String> codes = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> severities = ArgumentCaptor.forClass(String.class);
        verify(repository, atLeastOnce()).createIssue(
            eq(BATCH_ID), any(), codes.capture(), anyString(), severities.capture(), anyString()
        );
        assertThat(codes.getAllValues()).contains("IMPORT_CHUNK_NO_GAP", "IMPORT_JSON_LINE_INVALID");
        assertThat(severities.getAllValues()).contains("warning", "error");
        verify(repository).markRecord(anyLong(), eq("failed"));
        verify(repository, times(2)).insertRecord(
            anyLong(), eq(BATCH_ID), anyInt(), any(), anyString(), anyString()
        );
    }

    @Test
    void reportsReadProgressAtEachHundredthPhysicalLineAndCapsItAtFiftyPercent() throws Exception {
        when(repository.selectById(BATCH_ID)).thenReturn(batch(
            null, null, null, "document_chunk", 1L, emptyParseConfig()
        ));
        String payload = "   \n".repeat(99) + row(1, "[]") + "\n"
            + "   \n".repeat(99) + row(2, "[]") + "\n";
        readPayload(payload);
        successfulCompletion();

        worker.validate(BATCH_ID);

        verify(repository, times(2)).updateProgress(
            BATCH_ID, "parsing", "read_jsonl", new BigDecimal("50.00")
        );
        verify(progressPublisher, times(6)).publish(BATCH_ID);
        verify(repository, times(2)).insertRecord(
            anyLong(), eq(BATCH_ID), anyInt(), any(), anyString(), anyString()
        );
    }

    @Test
    void reportsZeroReadProgressWhenTheSourceSizeIsUnknown() throws Exception {
        when(repository.selectById(BATCH_ID)).thenReturn(batch(
            null, null, null, "document_chunk", 0L, emptyParseConfig()
        ));
        String payload = "\t\n".repeat(99) + row(1, "[]") + "\n";
        readPayload(payload);
        successfulCompletion();

        worker.validate(BATCH_ID);

        verify(repository).updateProgress(BATCH_ID, "parsing", "read_jsonl", BigDecimal.ZERO);
    }

    @Test
    void wrapsStorageFailuresWithTheOriginalCause() {
        when(repository.selectById(BATCH_ID)).thenReturn(batch(
            null, null, null, "document_chunk", 100L, emptyParseConfig()
        ));
        when(storage.read(eq("imports/document-chunk/101/source.jsonl"), any()))
            .thenThrow(new IllegalStateException("storage unavailable"));

        assertThatThrownBy(() -> worker.validate(BATCH_ID))
            .isInstanceOfSatisfying(IllegalStateException.class, error -> {
                assertThat(error).hasMessage("Textbook precheck failed");
                assertThat(error.getCause()).hasMessage("storage unavailable");
            });
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrapsInputStreamReadFailuresFromTheStorageCallback() {
        when(repository.selectById(BATCH_ID)).thenReturn(batch(
            null, null, null, "document_chunk", 100L, emptyParseConfig()
        ));
        when(storage.read(eq("imports/document-chunk/101/source.jsonl"), any())).thenAnswer(invocation -> {
            Function<InputStream, ?> reader = invocation.getArgument(1);
            return reader.apply(new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("broken stream");
                }
            });
        });

        assertThatThrownBy(() -> worker.validate(BATCH_ID))
            .isInstanceOfSatisfying(IllegalStateException.class, error -> {
                assertThat(error).hasMessage("Textbook precheck failed");
                assertThat(error.getCause()).hasMessage("Unable to stream textbook JSONL");
                assertThat(error.getCause().getCause()).hasMessage("broken stream");
            });
    }

    @Test
    void wrapsCompletionConflictsWithTheOriginalCause() {
        when(repository.selectById(BATCH_ID)).thenReturn(batch(
            null, null, null, "document_chunk", 100L, emptyParseConfig()
        ));
        readPayload("");
        when(repository.updateStage(
            BATCH_ID, "parsing", "validating", "validate_schema", new BigDecimal("55.00")
        )).thenReturn(1);
        when(repository.finishTextbook(BATCH_ID, "validating")).thenReturn(0);

        assertThatThrownBy(() -> worker.validate(BATCH_ID))
            .isInstanceOfSatisfying(IllegalStateException.class, error -> {
                assertThat(error).hasMessage("Textbook precheck failed");
                assertThat(error.getCause()).hasMessage("Textbook precheck completion failed");
            });
    }

    private void assertPrecheckFailure() {
        assertThatThrownBy(() -> worker.validate(BATCH_ID))
            .isInstanceOfSatisfying(IllegalStateException.class, error ->
                assertThat(error).hasMessage("Textbook precheck failed")
            );
    }

    @SuppressWarnings("unchecked")
    private void readPayload(String payload) {
        when(storage.read(eq("imports/document-chunk/101/source.jsonl"), any())).thenAnswer(invocation -> {
            Function<InputStream, ?> reader = invocation.getArgument(1);
            return reader.apply(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
        });
    }

    private void successfulCompletion() {
        when(repository.updateStage(
            BATCH_ID, "parsing", "validating", "validate_schema", new BigDecimal("55.00")
        )).thenReturn(1);
        when(repository.finishTextbook(BATCH_ID, "validating")).thenReturn(1);
    }

    private static CmImportBatch batch(
        Long documentId,
        Long syllabusVersionId,
        Long certificationId,
        String importType,
        long sourceFileSize,
        String parseConfig
    ) {
        return new CmImportBatch(
            BATCH_ID,
            "upload-request",
            documentId,
            syllabusVersionId,
            null,
            "imports/document-chunk/101/source.jsonl",
            "file-hash",
            sourceFileSize,
            importType,
            "document_chunk/1.0",
            parseConfig,
            "parsing",
            "read_jsonl",
            BigDecimal.ZERO,
            0,
            0,
            0,
            0,
            null,
            OffsetDateTime.parse("2026-08-03T10:00:00+08:00"),
            null,
            null,
            7L,
            8L,
            null,
            null,
            certificationId
        );
    }

    private static TextbookImportDocument document(Long syllabusVersionId, long certificationId) {
        return new TextbookImportDocument(
            DOCUMENT_ID, syllabusVersionId, "Book", null, "draft", "0", 7L, 8L, certificationId
        );
    }

    private static String parseConfig() {
        return "{\"schema_version\":\"import_parse_config/1.0\",\"import_type\":\"document_chunk\","
            + "\"subject_mappings\":[{\"subject_no\":1,\"exam_subject_id\":11}]}";
    }

    private static String emptyParseConfig() {
        return "{\"schema_version\":\"import_parse_config/1.0\",\"import_type\":\"document_chunk\","
            + "\"subject_mappings\":[]}";
    }

    private static String row(int chunkNo, String knowledgePoints) throws Exception {
        String content = "Body " + chunkNo;
        return "{\"source_key\":\"chunk-" + chunkNo + "\",\"chunk_no\":" + chunkNo
            + ",\"heading\":null,\"heading_path\":[\"Chapter 1\"],"
            + "\"content\":\"" + content + "\",\"page_start\":1,\"page_end\":1,"
            + "\"source_locator\":{},\"content_hash\":\"" + sha256(content) + "\","
            + "\"knowledge_points\":" + knowledgePoints + "}";
    }

    private static String invalidRow(int chunkNo) {
        return "{\"source_key\":\"broken\",\"chunk_no\":" + chunkNo
            + ",\"heading\":null,\"heading_path\":{},\"content\":\"\","
            + "\"page_start\":2,\"page_end\":1,\"source_locator\":[],"
            + "\"content_hash\":\"bad\",\"knowledge_points\":[]}";
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}
