package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.ImportProgressPublisher;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.QuestionImportPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QuestionImportWorkerPublicBranchTest {
    private static final String DEFAULT_CONFIG = "{\"schema_version\":\"question_zip/2.0\","
        + "\"certification_name\":\"架构师\",\"derived_subjects\":[{\"subject_no\":1,"
        + "\"exam_subject_id\":11,\"label\":\"综合知识\"}]}";
    private static final String KNOWLEDGE = "[{\"subject_no\":1,\"code\":\"K1\"}]";

    @Mock
    private ImportMapper repository;
    @Mock
    private ImportStorage storage;
    @Mock
    private ImportPersistenceService importPersistence;
    @Mock
    private QuestionImportPersistenceService questionPersistence;
    @Mock
    private ImportProgressPublisher progressPublisher;

    private QuestionImportWorker worker;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder().build();
        worker = new QuestionImportWorker(
            repository,
            storage,
            importPersistence,
            questionPersistence,
            mapper,
            new ImportJsonDocumentFactory(JsonMapper.builder().build()),
            progressPublisher
        );
    }

    @Test
    void rejectsAnEmptyValidatedResultSetBeforeReadingTheArchive() {
        when(repository.selectById(101L)).thenReturn(batch(101L, "importing", 0, "question", DEFAULT_CONFIG));
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of());

        assertThatThrownBy(() -> worker.persist(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Validated question records changed");

        verify(storage, never()).read(anyString(), any());
        verify(questionPersistence, never()).persistBatch(anyLong());
    }

    @Test
    void treatsABlankJsonLineAsInvalidWithoutCreatingARecord() {
        arrangeValidation(101L, "question", DEFAULT_CONFIG, zip(Map.of(
            "questions.jsonl", "\n".getBytes(StandardCharsets.UTF_8)
        )));

        worker.validate(101L);

        verify(repository).createIssue(
            eq(101L), eq(null), eq("QUESTION_JSON_INVALID"), eq("$line[1]"), eq("error"), anyString()
        );
        verify(repository, never()).createRecord(anyLong(), anyInt(), any(), anyString(), anyString());
        verify(repository).finishQuestion(101L, "validating");
    }

    @Test
    void rejectsNearBomPrefixesAsInvalidUtf8InsteadOfMistakingThemForABom() {
        arrangeValidation(101L, "question", DEFAULT_CONFIG, zip(Map.of(
            "questions.jsonl", new byte[]{(byte) 0xEF, 'x', 'x'}
        )));
        arrangeValidation(102L, "question", DEFAULT_CONFIG, zip(Map.of(
            "questions.jsonl", new byte[]{(byte) 0xEF, (byte) 0xBB, 'x'}
        )));

        worker.validate(101L);
        worker.validate(102L);

        verify(repository).createIssue(
            eq(101L), eq(null), eq("QUESTION_JSON_ENCODING_INVALID"), eq("$line[1]"), eq("error"), anyString()
        );
        verify(repository).createIssue(
            eq(102L), eq(null), eq("QUESTION_JSON_ENCODING_INVALID"), eq("$line[1]"), eq("error"), anyString()
        );
    }

    @Test
    void normalizesLowercaseChoiceAnswersAndPreservesPrimaryAndSecondaryKnowledgeRoles() throws Exception {
        String source = question(
            "q-normalized",
            "single",
            "{\"B\":\"乙\",\"A\":\"甲\"}",
            "b",
            "[{\"subject_no\":1,\"code\":\"K1\"},{\"subject_no\":1,\"code\":\"K2\"}]",
            "[]"
        ) + "\r\n";
        arrangeValidation(101L, "question", DEFAULT_CONFIG, zip(Map.of(
            "questions.jsonl", source.getBytes(StandardCharsets.UTF_8)
        )));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.findKnowledgePoint(21L, 11L, "K2")).thenReturn(32L);
        when(repository.isKnowledgePointLeaf(anyLong())).thenReturn(true);
        when(repository.createRecord(anyLong(), eq(1), eq("q-normalized"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        ArgumentCaptor<String> result = ArgumentCaptor.forClass(String.class);
        verify(repository).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), result.capture(), eq("success"));
        var normalized = JsonMapper.builder().build().readTree(result.getValue());
        assertThat(normalized.path("answer").path("value").get(0).asText()).isEqualTo("B");
        assertThat(normalized.path("options").get(0).path("label").asText()).isEqualTo("A");
        assertThat(normalized.path("options").get(1).path("label").asText()).isEqualTo("B");
        assertThat(normalized.path("knowledge_points").get(0).path("role").asText()).isEqualTo("primary");
        assertThat(normalized.path("knowledge_points").get(1).path("role").asText()).isEqualTo("secondary");
        verify(repository, never()).markRecord(anyLong(), anyString());
    }

    @Test
    void rejectsMissingNonTextBlankAndOversizedRequiredFields() {
        String missingFields = "{\"source\":7,\"subject\":\"\",\"type\":null,\"options\":{},"
            + "\"answer\":\"参考答案\",\"analysis\":null,\"knowledge_points\":" + KNOWLEDGE + ",\"images\":[]}";
        String oversizedQid = question("q".repeat(201), "single", "{\"A\":\"甲\",\"B\":\"乙\"}",
            "A", KNOWLEDGE, "[]");
        arrangeValidation(101L, "question", DEFAULT_CONFIG, zip(Map.of(
            "questions.jsonl", (missingFields + "\n" + oversizedQid).getBytes(StandardCharsets.UTF_8)
        )));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), anyInt(), any(), anyString(), anyString())).thenReturn(41L, 42L);

        worker.validate(101L);

        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_FIELD_INVALID"),
            eq("qid"), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_FIELD_INVALID"),
            eq("source"), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_FIELD_INVALID"),
            eq("subject"), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_FIELD_INVALID"),
            eq("question"), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(42L), eq("QUESTION_FIELD_INVALID"),
            eq("qid"), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_TYPE_INVALID"),
            eq("type"), eq("error"), anyString());
        verify(repository, times(2)).markRecord(anyLong(), eq("failed"));
    }

    @Test
    void rejectsEveryInvalidChoiceAndNonChoiceOptionShape() {
        String tooMany = optionObject(27);
        List<String> rows = List.of(
            question("q-array", "single", "[]", "A", KNOWLEDGE, "[]"),
            question("q-few", "single", "{\"A\":\"only\"}", "A", KNOWLEDGE, "[]"),
            question("q-many", "single", tooMany, "A", KNOWLEDGE, "[]"),
            question("q-entry", "single", "{\"a\":\"lower\",\"B\":7}", "A", KNOWLEDGE, "[]"),
            question("q-case-array", "subjective", "[]", "参考答案", KNOWLEDGE, "[]"),
            question("q-case-options", "subjective", "{\"A\":\"不应存在\"}", "参考答案", KNOWLEDGE, "[]")
        );
        arrangeValidation(101L, "question", DEFAULT_CONFIG, zip(Map.of(
            "questions.jsonl", String.join("\n", rows).getBytes(StandardCharsets.UTF_8)
        )));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), anyInt(), anyString(), anyString(), anyString()))
            .thenReturn(41L, 42L, 43L, 44L, 45L, 46L);

        worker.validate(101L);

        ArgumentCaptor<String> paths = ArgumentCaptor.forClass(String.class);
        verify(repository, atLeast(7)).createIssue(
            eq(101L), anyLong(), eq("QUESTION_OPTIONS_INVALID"), paths.capture(), eq("error"), anyString()
        );
        assertThat(paths.getAllValues()).contains("options", "options.a", "options.B");
        verify(repository, times(6)).markRecord(anyLong(), eq("failed"));
    }

    @Test
    void rejectsNonArrayMissingAndOutOfRangeKnowledgeSubjectData() {
        List<String> rows = List.of(
            question("q-object", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A", "{}", "[]"),
            question("q-code", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A",
                "[{\"subject_no\":1}]", "[]"),
            question("q-big", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A",
                "[{\"subject_no\":2147483648,\"code\":\"K1\"}]", "[]"),
            question("q-no-subject", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A",
                "[{\"code\":\"K1\"}]", "[]"),
            question("q-outside", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A",
                "[{\"subject_no\":2,\"code\":\"K1\"}]", "[]")
        );
        arrangeValidation(101L, "question", DEFAULT_CONFIG, zip(Map.of(
            "questions.jsonl", String.join("\n", rows).getBytes(StandardCharsets.UTF_8)
        )));
        when(repository.createRecord(anyLong(), anyInt(), anyString(), anyString(), anyString()))
            .thenReturn(41L, 42L, 43L, 44L, 45L);

        worker.validate(101L);

        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_KNOWLEDGE_REQUIRED"),
            eq("knowledge_points"), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(42L), eq("QUESTION_KNOWLEDGE_NOT_FOUND"),
            eq("knowledge_points[0].code"), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(43L), eq("QUESTION_SUBJECT_MISMATCH"),
            eq("knowledge_points[0].subject_no"), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(44L), eq("QUESTION_SUBJECT_MISMATCH"),
            eq("knowledge_points[0].subject_no"), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(45L), eq("QUESTION_SUBJECT_MISMATCH"),
            eq("knowledge_points"), eq("error"), anyString());
        verify(repository, times(5)).markRecord(anyLong(), eq("failed"));
    }

    @Test
    void allowsPaperSourceReuseWithoutReportingAQuestionBatchDuplicate() throws Exception {
        String row = question("q-paper", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A", KNOWLEDGE, "[]");
        arrangeValidation(101L, "paper", DEFAULT_CONFIG, zip(Map.of(
            "questions.jsonl", row.getBytes(StandardCharsets.UTF_8)
        )));
        when(repository.countQuestionSource(anyString())).thenReturn(1);
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.findPaperQuestionReuses(eq(11L), eq(21L), anyString(), eq(7L))).thenReturn(List.of());
        when(repository.createRecord(anyLong(), eq(1), eq("q-paper"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        verify(repository, never()).createIssue(
            eq(101L), eq(41L), eq("QUESTION_QID_DUPLICATE_IN_SYSTEM"), anyString(), anyString(), anyString()
        );
        ArgumentCaptor<String> result = ArgumentCaptor.forClass(String.class);
        verify(repository).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), result.capture(), eq("success"));
        assertThat(JsonMapper.builder().build().readTree(result.getValue()).path("resolution").asText())
            .isEqualTo("CREATE");
        verify(repository).finishPaper(101L, "validating");
    }

    @Test
    void rejectsUnreadableOversizedAndNonTextImageReferences() {
        List<String> rows = List.of(
            imageQuestion("q-wide", "images/wide.png"),
            imageQuestion("q-tall", "images/tall.png"),
            imageQuestion("q-pixels", "images/pixels.png"),
            imageQuestion("q-unreadable", "images/unreadable.png"),
            imageQuestion("q-truncated", "images/truncated.png"),
            question("q-nontext", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A", KNOWLEDGE, "[17]")
        );
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("questions.jsonl", String.join("\n", rows).getBytes(StandardCharsets.UTF_8));
        entries.put("images/wide.png", pngMetadata(10_001, 1));
        entries.put("images/tall.png", pngMetadata(1, 10_001));
        entries.put("images/pixels.png", pngMetadata(5_001, 5_000));
        entries.put("images/unreadable.png", "not-an-image".getBytes(StandardCharsets.UTF_8));
        entries.put("images/truncated.png", pngSignature());
        arrangeValidation(101L, "question", DEFAULT_CONFIG, zip(entries));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), anyInt(), anyString(), anyString(), anyString()))
            .thenReturn(41L, 42L, 43L, 44L, 45L, 46L);

        worker.validate(101L);

        verify(repository, times(3)).createIssue(
            eq(101L), anyLong(), eq("QUESTION_IMAGE_DIMENSIONS_EXCEEDED"),
            eq("images[0]"), eq("error"), anyString()
        );
        verify(repository, times(2)).createIssue(
            eq(101L), anyLong(), eq("QUESTION_IMAGE_INVALID"),
            eq("images[0]"), eq("error"), anyString()
        );
        verify(repository).createIssue(
            eq(101L), eq(46L), eq("QUESTION_IMAGE_MISSING"),
            eq("images[0]"), eq("error"), anyString()
        );
        verify(repository, times(6)).markRecord(anyLong(), eq("failed"));
    }

    @Test
    void reportsAnOversizedLineThatEndsWithANewlineOnlyOnce() {
        arrangeValidation(101L, "question", DEFAULT_CONFIG, zip(Map.of(
            "questions.jsonl", oversizedLineWithNewline()
        )));

        worker.validate(101L);

        verify(repository, times(1)).createIssue(
            eq(101L), eq(null), eq("QUESTION_JSON_LINE_TOO_LARGE"),
            eq("$line[1]"), eq("error"), anyString()
        );
        verify(repository, never()).createRecord(anyLong(), anyInt(), any(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"derived_subjects\":[]}",
        "{\"derived_subjects\":[\"invalid\"]}",
        "{\"derived_subjects\":[{\"exam_subject_id\":11}]}",
        "{\"derived_subjects\":[{\"subject_no\":1}]}"
    })
    void rejectsEveryMalformedDerivedSubjectConfiguration(String config) {
        String row = question("q-config", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A", KNOWLEDGE, "[]");
        arrangeValidation(101L, "question", config, zip(Map.of(
            "questions.jsonl", row.getBytes(StandardCharsets.UTF_8)
        )));

        assertThatThrownBy(() -> worker.validate(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Question ZIP precheck failed")
            .hasRootCauseInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).finishQuestion(anyLong(), anyString());
    }

    private void arrangeValidation(long batchId, String importType, String config, byte[] source) {
        CmImportBatch batch = batch(batchId, "parsing", 0, importType, config);
        when(repository.selectById(batchId)).thenReturn(batch);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<InputStream, Object> reader = invocation.getArgument(1);
            return reader.apply(new ByteArrayInputStream(source));
        }).when(storage).read(eq(batch.sourceFilePath()), any());
    }

    private static CmImportBatch batch(long id, String status, int validCount, String importType, String config) {
        return new CmImportBatch(
            id, "request-" + id, 21L, 11L, "imports/" + importType + "/" + id + "/source.zip",
            "zip-hash", 100L, importType, "question-zip/1.0", config, status, "read_jsonl",
            BigDecimal.ZERO, validCount, 0, 0, null,
            OffsetDateTime.parse("2026-08-03T10:00:00+08:00"), null, null, 7L, 8L
        );
    }

    private static String question(
        String qid,
        String type,
        String options,
        String answer,
        String knowledge,
        String images
    ) {
        String typeValue = type == null ? "null" : "\"" + type + "\"";
        String answerValue = answer == null ? "null" : "\"" + answer + "\"";
        return "{\"qid\":\"" + qid + "\",\"source\":\"source-" + qid + "\",\"subject\":\"架构师\","
            + "\"type\":" + typeValue + ",\"question\":\"题干-" + qid + "\",\"options\":" + options
            + ",\"answer\":" + answerValue + ",\"analysis\":null,\"knowledge_points\":" + knowledge
            + ",\"images\":" + images + "}";
    }

    private static String imageQuestion(String qid, String imagePath) {
        return question(qid, "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A", KNOWLEDGE,
            "[\"" + imagePath + "\"]");
    }

    private static String optionObject(int count) {
        StringBuilder options = new StringBuilder("{");
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                options.append(',');
            }
            String label = index < 26 ? String.valueOf((char) ('A' + index)) : "AA";
            options.append('"').append(label).append("\":\"选项").append(index).append('"');
        }
        return options.append('}').toString();
    }

    private static byte[] oversizedLineWithNewline() {
        byte[] line = new byte[QuestionImportArchive.MAX_LINE_BYTES + 2];
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Random random = new Random(42L);
        for (int index = 0; index < line.length - 1; index++) {
            line[index] = (byte) alphabet.charAt(random.nextInt(alphabet.length()));
        }
        line[line.length - 1] = '\n';
        return line;
    }

    private static byte[] pngMetadata(int width, int height) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(pngSignature());
            byte[] header = ByteBuffer.allocate(13)
                .putInt(width)
                .putInt(height)
                .put((byte) 8)
                .put((byte) 2)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0)
                .array();
            writePngChunk(output, "IHDR", header);
            writePngChunk(output, "IEND", new byte[0]);
            output.flush();
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] pngSignature() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    }

    private static void writePngChunk(DataOutputStream output, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 checksum = new CRC32();
        checksum.update(typeBytes);
        checksum.update(data);
        output.writeInt(data.length);
        output.write(typeBytes);
        output.write(data);
        output.writeInt((int) checksum.getValue());
    }

    private static byte[] zip(Map<String, byte[]> entries) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
