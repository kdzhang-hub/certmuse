package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.service.ImportProgressPublisher;

import org.dromara.certmuse.catalog.config.ImportSchedulingConfiguration;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.TextbookImportDocument;
import org.dromara.certmuse.catalog.domain.TextbookKnowledgePointLookup;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.validation.TextbookImportValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TextbookImportWorkerTest {

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
        when(repository.selectById(101L)).thenReturn(batch());
        when(repository.selectTextbookDocument(201L, null)).thenReturn(
            new TextbookImportDocument(201L, 21L, "Book", null, "draft", "0", 7L, 8L)
        );
        when(repository.selectTextbookKnowledgePointLookups(List.of(11L))).thenReturn(
            List.of(new TextbookKnowledgePointLookup(401L, 21L, 11L, "1.1"))
        );
        when(repository.updateStage(101L, "parsing", "validating", "validate_schema", new BigDecimal("55.00")))
            .thenReturn(1);
        when(repository.finishTextbook(101L, "validating")).thenReturn(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesPhysicalLinesEnvelopeRawHashAndGapWarning() throws Exception {
        String first = row(1, "[]");
        String third = row(3, "[{\"subject_no\":1,\"code\":\"1.1\"}]");
        byte[] payload = (first + "\r\n\r\n" + third + "\r\n").getBytes(StandardCharsets.UTF_8);
        when(storage.read(eq("imports/document-chunk/101/source.jsonl"), any())).thenAnswer(call -> {
            Function<InputStream, ?> reader = call.getArgument(1);
            return reader.apply(new ByteArrayInputStream(payload));
        });

        worker.validate(101L);

        ArgumentCaptor<Integer> lines = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> envelopes = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).insertRecord(
            anyLong(), eq(101L), lines.capture(), any(), envelopes.capture(), hashes.capture()
        );
        assertThat(lines.getAllValues()).containsExactly(1, 3);
        assertThat(envelopes.getAllValues()).allSatisfy(envelope -> {
            var parsed = new ImportSchedulingConfiguration().importStrictJsonMapper().readTree(envelope);
            assertThat(parsed.path("schema_version").textValue())
                .isEqualTo("document_chunk_raw_record/1.0");
            assertThat(parsed.path("record").isObject()).isTrue();
        });
        assertThat(hashes.getAllValues().getFirst()).isEqualTo(sha256(first + "\r"));
        ArgumentCaptor<String> issueCodes = ArgumentCaptor.forClass(String.class);
        verify(repository, atLeastOnce()).createIssue(
            eq(101L), any(), issueCodes.capture(), anyString(), anyString(), anyString()
        );
        assertThat(issueCodes.getAllValues()).contains("UNMAPPED_CHUNK", "IMPORT_CHUNK_NO_GAP");
        verify(repository).finishTextbook(101L, "validating");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsDuplicateChunkNumberAndStrictDuplicateJsonKey() throws Exception {
        String first = row(1, "[]");
        String duplicateChunk = row(1, "[]");
        String duplicateKey = first.substring(0, first.length() - 1) + ",\"chunk_no\":2}";
        byte[] payload = (first + "\n" + duplicateChunk + "\n" + duplicateKey + "\n")
            .getBytes(StandardCharsets.UTF_8);
        when(storage.read(eq("imports/document-chunk/101/source.jsonl"), any())).thenAnswer(call -> {
            Function<InputStream, ?> reader = call.getArgument(1);
            return reader.apply(new ByteArrayInputStream(payload));
        });

        worker.validate(101L);

        verify(repository).markRecord(anyLong(), eq("failed"));
        ArgumentCaptor<String> issueCodes = ArgumentCaptor.forClass(String.class);
        verify(repository, atLeastOnce()).createIssue(
            eq(101L), any(), issueCodes.capture(), anyString(), anyString(), anyString()
        );
        assertThat(issueCodes.getAllValues()).contains(
            "IMPORT_CHUNK_NO_DUPLICATE", "IMPORT_JSON_LINE_INVALID"
        );
        verify(repository).finishTextbook(101L, "validating");
    }

    private static CmImportBatch batch() {
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
            "{\"schema_version\":\"import_parse_config/1.0\",\"import_type\":\"document_chunk\","
                + "\"mode\":\"create\",\"title\":\"Book\",\"edition\":null,"
                + "\"subject_mappings\":[{\"subject_no\":1,\"exam_subject_id\":11}]}",
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
            8L
        );
    }

    private static String row(int chunkNo, String knowledgePoints) throws Exception {
        String content = "Body " + chunkNo;
        return "{\"source_key\":\"chunk-" + chunkNo + "\",\"chunk_no\":" + chunkNo
            + ",\"heading\":null,\"heading_path\":[\"Chapter 1\"],"
            + "\"content\":\"" + content + "\",\"page_start\":1,\"page_end\":1,"
            + "\"source_locator\":{},\"content_hash\":\"" + sha256(content) + "\","
            + "\"knowledge_points\":" + knowledgePoints + "}";
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}
