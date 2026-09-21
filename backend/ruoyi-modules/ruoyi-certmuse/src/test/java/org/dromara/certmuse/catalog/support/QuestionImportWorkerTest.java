package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.service.ImportProgressPublisher;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.QuestionImportPersistenceService;
import org.dromara.certmuse.shared.QuestionSemanticHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QuestionImportWorkerTest {
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
        worker = new QuestionImportWorker(
            repository,
            storage,
            importPersistence,
            questionPersistence,
            JsonMapper.builder().build(),
            new ImportJsonDocumentFactory(JsonMapper.builder().build()),
            progressPublisher
        );
    }

    @Test
    void acceptsNormalTrailingNewlineWithoutCreatingAnEmptyRecord() {
        byte[] zip = zip(Map.of("questions.jsonl", (validQuestion("images", "") + "\n").getBytes(StandardCharsets.UTF_8)));
        arrangeValidation(zip);
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        verify(repository).createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString());
        verify(repository, never()).createRecord(anyLong(), eq(2), any(), anyString(), anyString());
        verify(repository).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), anyString(), eq("success"));
        verify(repository).finishQuestion(101L, "validating");
    }

    @Test
    void validatesQualificationScopedQuestionWithoutResolvingKnowledgePoints() throws Exception {
        byte[] source = zip(Map.of(
            "questions.jsonl", validQuestion("images", "").getBytes(StandardCharsets.UTF_8)
        ));
        when(repository.selectById(101L)).thenReturn(batchWithoutSyllabus());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<InputStream, Object> reader = invocation.getArgument(1);
            return reader.apply(new ByteArrayInputStream(source));
        }).when(storage).read(eq("imports/question/101/source.zip"), any());
        when(repository.createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        ArgumentCaptor<String> result = ArgumentCaptor.forClass(String.class);
        verify(repository).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), result.capture(), eq("success"));
        assertThat(JsonMapper.builder().build().readTree(result.getValue()).path("knowledge_points").isEmpty())
            .isTrue();
        verify(repository, never()).findKnowledgePoint(anyLong(), anyLong(), anyString());
    }

    @Test
    void finishesPaperBatchesThroughThePaperContractAndReportsUnreferencedImages() {
        byte[] source = zip(Map.of(
            "questions.jsonl", validQuestion("images", "").getBytes(StandardCharsets.UTF_8),
            "images/unreferenced.png", png()
        ));
        when(repository.selectById(101L)).thenReturn(
            batch(101L, "imports/paper/101/source.zip", "parsing", 0, "paper")
        );
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<InputStream, Object> reader = invocation.getArgument(1);
            return reader.apply(new ByteArrayInputStream(source));
        }).when(storage).read(eq("imports/paper/101/source.zip"), any());
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        verify(repository).finishPaper(101L, "validating");
        verify(repository).createIssue(
            eq(101L), eq(null), eq("QUESTION_IMAGE_UNREFERENCED"), eq("images/unreferenced.png"),
            eq("warning"), anyString()
        );
        verify(repository).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), anyString(), eq("success"));
    }

    @Test
    void wrapsRawQuestionWithAnAuditSchemaVersion() throws Exception {
        byte[] zip = zip(Map.of("questions.jsonl", validQuestion("images", "").getBytes(StandardCharsets.UTF_8)));
        arrangeValidation(zip);
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        ArgumentCaptor<String> rawRecord = ArgumentCaptor.forClass(String.class);
        verify(repository).createRecord(anyLong(), eq(1), eq("q-1"), rawRecord.capture(), anyString());
        var raw = JsonMapper.builder().build().readTree(rawRecord.getValue());
        assertThat(raw.path("schema_version").asText()).isEqualTo("question_raw/1.0");
        assertThat(raw.path("payload").path("qid").asText()).isEqualTo("q-1");
    }

    @Test
    void wrapsNormalizedQuestionResultWithItsOwnSchemaVersion() throws Exception {
        byte[] zip = zip(Map.of("questions.jsonl", validQuestion("images", "").getBytes(StandardCharsets.UTF_8)));
        arrangeValidation(zip);
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        ArgumentCaptor<String> result = ArgumentCaptor.forClass(String.class);
        verify(repository).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), result.capture(), eq("success"));
        assertThat(JsonMapper.builder().build().readTree(result.getValue()).path("schema_version").asText())
            .isEqualTo("question_import_result/1.0");
    }

    @Test
    void recordsInvalidUtf8AsContentIssueWithoutThrowingForRetry() {
        byte[] invalid = new byte[]{(byte) 0xC3, (byte) 0x28};
        arrangeValidation(zip(Map.of("questions.jsonl", invalid)));

        worker.validate(101L);

        verify(repository).createIssue(
            eq(101L), eq(null), eq("QUESTION_JSON_ENCODING_INVALID"), eq("$line[1]"),
            eq("error"), anyString()
        );
        verify(repository).finishQuestion(101L, "validating");
    }

    @Test
    void recordsMalformedAndNonObjectJsonLinesWithoutCreatingRecords() {
        arrangeValidation(zip(Map.of("questions.jsonl", "{\n[]\n".getBytes(StandardCharsets.UTF_8))));

        worker.validate(101L);

        verify(repository).createIssue(eq(101L), eq(null), eq("QUESTION_JSON_INVALID"), eq("$line[1]"),
            eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(null), eq("QUESTION_JSON_INVALID"), eq("$line[2]"),
            eq("error"), anyString());
        verify(repository, never()).createRecord(anyLong(), anyInt(), anyString(), anyString(), anyString());
        verify(repository).finishQuestion(101L, "validating");
    }

    @Test
    void reportsMissingQuestionsFileAndUtf8BomAsArchiveIssues() {
        arrangeValidation(zip(Map.of("images/orphan.png", png())));
        worker.validate(101L);
        verify(repository).createIssue(eq(101L), eq(null), eq("QUESTION_JSONL_REQUIRED"), eq("file"),
            eq("error"), anyString());

        byte[] json = "{}\n".getBytes(StandardCharsets.UTF_8);
        byte[] bom = new byte[json.length + 3];
        bom[0] = (byte) 0xEF;
        bom[1] = (byte) 0xBB;
        bom[2] = (byte) 0xBF;
        System.arraycopy(json, 0, bom, 3, json.length);
        arrangeValidation(zip(Map.of("questions.jsonl", bom)));
        worker.validate(101L);
        verify(repository).createIssue(eq(101L), eq(null), eq("QUESTION_JSON_ENCODING_INVALID"), eq("file"),
            eq("error"), anyString());
    }

    @Test
    void recordsAQuestionLineThatExceedsTheOneMiBLimit() {
        byte[] longLine = new byte[1_048_577];
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        java.util.Random random = new java.util.Random(42L);
        for (int index = 0; index < longLine.length; index++) {
            longLine[index] = (byte) alphabet.charAt(random.nextInt(alphabet.length()));
        }
        arrangeValidation(zip(Map.of("questions.jsonl", longLine)));

        worker.validate(101L);

        verify(repository).createIssue(eq(101L), eq(null), eq("QUESTION_JSON_LINE_TOO_LARGE"), eq("$line[1]"),
            eq("error"), anyString());
        verify(repository).finishQuestion(101L, "validating");
    }

    @Test
    void rejectsHighlyCompressedEntryBeforeValidation() {
        byte[] repeated = ("x".repeat(300_000)).getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> {
            try (QuestionImportArchive ignored = QuestionImportArchive.extract(
                new ByteArrayInputStream(zip(Map.of("questions.jsonl", repeated)))
            )) {
                // Extraction itself must reject the entry.
            }
        }).isInstanceOf(QuestionImportValidationException.class)
            .extracting(error -> ((QuestionImportValidationException) error).code())
            .isEqualTo("QUESTION_ZIP_COMPRESSION_RATIO");
    }

    @Test
    void rejectsImageWhoseRealFormatDoesNotMatchExtension() {
        byte[] png = png();
        String question = validQuestion("images/fake.jpg", "images/fake.jpg");
        arrangeValidation(zip(Map.of(
            "questions.jsonl", question.getBytes(StandardCharsets.UTF_8),
            "images/fake.jpg", png
        )));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        verify(repository).createIssue(
            eq(101L), eq(41L), eq("QUESTION_IMAGE_INVALID"), eq("images[0]"),
            eq("error"), anyString()
        );
        verify(repository).markRecord(41L, "failed");
    }

    @Test
    void contentHashDoesNotDependOnImagePathInsideZip() throws Exception {
        byte[] first = zip(Map.of(
            "questions.jsonl", validQuestion("images/a.png", "images/a.png").getBytes(StandardCharsets.UTF_8),
            "images/a.png", png()
        ));
        byte[] second = zip(Map.of(
            "questions.jsonl", validQuestion("images/b.png", "images/b.png").getBytes(StandardCharsets.UTF_8),
            "images/b.png", png()
        ));
        when(repository.selectById(101L)).thenReturn(batch(101L, "imports/question/101/source.zip"));
        when(repository.selectById(102L)).thenReturn(batch(102L, "imports/question/102/source.zip"));
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            Function<InputStream, Object> reader = invocation.getArgument(1);
            return reader.apply(new ByteArrayInputStream(key.contains("/101/") ? first : second));
        }).when(storage).read(anyString(), any());
        when(repository.findKnowledgePoint(anyLong(), anyLong(), eq("K1"))).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(eq(101L), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);
        when(repository.createRecord(eq(102L), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(42L);

        worker.validate(101L);
        worker.validate(102L);

        ArgumentCaptor<String> result = ArgumentCaptor.forClass(String.class);
        verify(repository).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), result.capture(), eq("success"));
        verify(repository).updateQuestionRecordResult(eq(42L), eq(1), eq(11L), result.capture(), eq("success"));
        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(mapper.readTree(result.getAllValues().get(0)).path("content_hash").asText())
            .isEqualTo(mapper.readTree(result.getAllValues().get(1)).path("content_hash").asText());
    }

    @Test
    void rejectsExistingSemanticDuplicateWhileKeepingOtherQuestionsImportable() {
        String duplicate = validQuestion("images", "");
        String unique = duplicate.replace("\"qid\":\"q-1\"", "\"qid\":\"q-2\"")
            .replace("\"source\":\"source\"", "\"source\":\"source-2\"")
            .replace("\"question\":\"题干\"", "\"question\":\"不同题干\"");
        arrangeValidation(zip(Map.of("questions.jsonl", (duplicate + "\n" + unique).getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(41L, 42L);
        when(repository.findQuestionSemanticDuplicate(eq(11L), eq(QuestionSemanticHasher.hash("CHOICE", "题干", List.of("甲", "乙")))))
            .thenReturn("Q0000000000000000001");

        worker.validate(101L);

        verify(repository).createIssue(
            eq(101L), eq(41L), eq("QUESTION_CONTENT_DUPLICATE_IN_SYSTEM"), eq("question"),
            eq("error"), org.mockito.ArgumentMatchers.contains("Q0000000000000000001")
        );
        verify(repository).markRecord(41L, "failed");
        verify(repository).updateQuestionRecordResult(eq(42L), eq(1), eq(11L), anyString(), eq("success"));
    }

    @Test
    void rejectsLaterSemanticDuplicateInTheSameArchiveWhenChoiceLabelsAreSwapped() {
        String first = validQuestion("images", "");
        String second = first.replace("\"qid\":\"q-1\"", "\"qid\":\"q-2\"")
            .replace("\"source\":\"source\"", "\"source\":\"source-2\"")
            .replace("\"options\":{\"A\":\"甲\",\"B\":\"乙\"},\"answer\":\"A\"",
                "\"options\":{\"A\":\"乙\",\"B\":\"甲\"},\"answer\":\"B\"");
        arrangeValidation(zip(Map.of("questions.jsonl", (first + "\n" + second).getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(41L, 42L);

        worker.validate(101L);

        verify(repository).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), anyString(), eq("success"));
        verify(repository).createIssue(
            eq(101L), eq(42L), eq("QUESTION_CONTENT_DUPLICATE_IN_FILE"), eq("question"),
            eq("error"), org.mockito.ArgumentMatchers.contains("第 1 行")
        );
        verify(repository).markRecord(42L, "failed");
    }

    @Test
    void acceptsChoiceQuestionsWithTheSameStemAndDifferentOptionTexts() throws Exception {
        String first = validQuestion("images", "");
        String second = first.replace("\"qid\":\"q-1\"", "\"qid\":\"q-2\"")
            .replace("\"source\":\"source\"", "\"source\":\"source-2\"")
            .replace("\"options\":{\"A\":\"甲\",\"B\":\"乙\"},\"answer\":\"A\"",
                "\"options\":{\"A\":\"丙\",\"B\":\"丁\"},\"answer\":\"A\"");
        arrangeValidation(zip(Map.of("questions.jsonl", (first + "\n" + second).getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(41L, 42L);

        worker.validate(101L);

        ArgumentCaptor<String> results = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).updateQuestionRecordResult(
            anyLong(), eq(1), eq(11L), results.capture(), eq("success")
        );
        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(mapper.readTree(results.getAllValues().get(0)).path("semantic_hash").asText())
            .isNotEqualTo(mapper.readTree(results.getAllValues().get(1)).path("semantic_hash").asText());
        verify(repository, never()).markRecord(anyLong(), eq("failed"));
    }

    @Test
    void skipsWarningOnlyQuestionInsteadOfPersistingIt() {
        String questionWithoutAnswer = validQuestion("images", "").replace("\"answer\":\"A\"", "\"answer\":null");
        arrangeValidation(zip(Map.of("questions.jsonl", questionWithoutAnswer.getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        verify(repository).createIssue(
            eq(101L), eq(41L), eq("QUESTION_ANSWER_MISSING"), eq("answer"), eq("warning"), anyString()
        );
        verify(repository).markRecord(41L, "skipped");
        verify(repository, never()).updateQuestionRecordResult(anyLong(), anyInt(), anyLong(), anyString(), eq("success"));
    }

    @Test
    void rejectsCertificationAndSourceDuplicatesBeforePersistence() {
        String question = validQuestion("images", "").replace("\"subject\":\"架构师\"", "\"subject\":\"其他资格\"");
        arrangeValidation(zip(Map.of("questions.jsonl", question.getBytes(StandardCharsets.UTF_8))));
        arrangeKnowledgeLookup();
        when(repository.countQuestionSource(anyString())).thenReturn(1);
        when(repository.createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_CERTIFICATION_MISMATCH"),
            eq("subject"), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_QID_DUPLICATE_IN_SYSTEM"),
            eq("qid"), eq("error"), anyString());
        verify(repository).markRecord(41L, "failed");
    }

    @Test
    void validatesQuestionTypeOptionsAnswerAndCrossSubjectKnowledge() {
        String question = validQuestion("images", "")
            .replace("\"type\":\"single\"", "\"type\":\"unsupported\"")
            .replace("\"options\":{\"A\":\"甲\",\"B\":\"乙\"}", "\"options\":{\"a\":\"\"}")
            .replace("\"knowledge_points\":[{\"subject_no\":1,\"code\":\"K1\"}]",
                "\"knowledge_points\":[{\"subject_no\":1,\"code\":\"K1\"},{\"subject_no\":2,\"code\":\"K2\"}]");
        arrangeValidation(zip(Map.of("questions.jsonl", question.getBytes(StandardCharsets.UTF_8))));
        when(repository.createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_TYPE_INVALID"),
            eq("type"), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_OPTIONS_INVALID"),
            anyString(), eq("error"), anyString());
        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_KNOWLEDGE_CROSS_SUBJECT"),
            anyString(), eq("error"), anyString());
        verify(repository).markRecord(41L, "failed");
    }

    @Test
    void rejectsChoiceAnswerThatDoesNotNameAnAvailableOption() {
        String question = validQuestion("images", "").replace("\"answer\":\"A\"", "\"answer\":\"Z\"");
        arrangeValidation(zip(Map.of("questions.jsonl", question.getBytes(StandardCharsets.UTF_8))));
        arrangeKnowledgeLookup();
        when(repository.createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_ANSWER_INVALID"),
            eq("answer"), eq("error"), anyString());
        verify(repository).markRecord(41L, "failed");
    }

    @Test
    void reportsMissingAndInvalidImageReferences() {
        String missing = validQuestion("images/missing.png", "");
        arrangeValidation(zip(Map.of("questions.jsonl", missing.getBytes(StandardCharsets.UTF_8))));
        arrangeKnowledgeLookup();
        when(repository.createRecord(anyLong(), eq(1), eq("q-1"), anyString(), anyString())).thenReturn(41L);

        worker.validate(101L);

        verify(repository).createIssue(eq(101L), eq(41L), eq("QUESTION_IMAGE_MISSING"),
            eq("images[0]"), eq("error"), anyString());
        verify(repository).markRecord(41L, "failed");
    }

    @Test
    void doesNotUploadOrPersistWhenBatchIsNoLongerImporting() {
        when(repository.selectById(101L)).thenReturn(batch(101L, "imports/question/101/source.zip", "completed"));

        worker.persist(101L);

        verify(storage, never()).read(anyString(), any());
        verify(questionPersistence, never()).persistBatch(anyLong());
    }

    @Test
    void doesNotPersistWhenAnImageUploadFails() {
        byte[] source = zip(Map.of("questions.jsonl", "ignored".getBytes(StandardCharsets.UTF_8), "images/a.png", png()));
        when(repository.selectById(101L)).thenReturn(batch(101L, "imports/question/101/source.zip", "importing"));
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(persistRow("images/a.png")));
        doAnswer(invocation -> ((Function<InputStream, Object>) invocation.getArgument(1))
            .apply(new ByteArrayInputStream(source))).when(storage).read(anyString(), any());
        doThrow(new IllegalStateException("oss unavailable")).when(storage).upload(anyString(), any());

        assertThatThrownBy(() -> worker.persist(101L)).isInstanceOf(IllegalStateException.class);

        verify(questionPersistence, never()).persistBatch(101L);
    }

    @Test
    void schedulesUploadedImagesForCleanupWhenDatabasePersistenceFails() {
        byte[] source = zip(Map.of("questions.jsonl", "ignored".getBytes(StandardCharsets.UTF_8), "images/a.png", png()));
        when(repository.selectById(101L)).thenReturn(batch(101L, "imports/question/101/source.zip", "importing"));
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(persistRow("images/a.png")));
        doAnswer(invocation -> ((Function<InputStream, Object>) invocation.getArgument(1))
            .apply(new ByteArrayInputStream(source))).when(storage).read(anyString(), any());
        doThrow(new IllegalStateException("database failed")).when(questionPersistence).persistBatch(101L);

        assertThatThrownBy(() -> worker.persist(101L)).isInstanceOf(IllegalStateException.class);

        verify(storage).upload(anyString(), any());
        verify(importPersistence).enqueueImageCleanup(
            eq(QuestionImportWorker.imageKey("zip-hash", "image-hash", "png")), eq("zip-hash"), eq("question_persist_failed")
        );
    }

    @Test
    void uploadsSharedImageOnlyOnceAcrossRecords() {
        byte[] source = zip(Map.of("questions.jsonl", "ignored".getBytes(StandardCharsets.UTF_8), "images/a.png", png()));
        when(repository.selectById(101L)).thenReturn(batch(101L, "imports/question/101/source.zip", "importing", 2));
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(persistRow("images/a.png"), persistRow("images/a.png")));
        doAnswer(invocation -> ((Function<InputStream, Object>) invocation.getArgument(1))
            .apply(new ByteArrayInputStream(source))).when(storage).read(anyString(), any());

        worker.persist(101L);

        verify(storage, times(1)).upload(anyString(), any());
        verify(questionPersistence).persistBatch(101L);
    }

    private void arrangeValidation(byte[] zip) {
        when(repository.selectById(101L)).thenReturn(batch(101L, "imports/question/101/source.zip"));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<InputStream, Object> reader = invocation.getArgument(1);
            return reader.apply(new ByteArrayInputStream(zip));
        }).when(storage).read(eq("imports/question/101/source.zip"), any());
    }

    private void arrangeKnowledgeLookup() {
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
    }

    private static CmImportBatch batch(long id, String sourcePath) {
        return batch(id, sourcePath, "parsing");
    }

    private static CmImportBatch batch(long id, String sourcePath, String status) {
        return batch(id, sourcePath, status, status.equals("importing") ? 1 : 0);
    }

    private static CmImportBatch batch(long id, String sourcePath, String status, int validCount) {
        return batch(id, sourcePath, status, validCount, "question");
    }

    private static CmImportBatch batch(long id, String sourcePath, String status, int validCount, String importType) {
        return new CmImportBatch(
            id, "request-" + id, 21L, 11L, sourcePath, "zip-hash", 100L,
            importType, "question-zip/1.0",
            "{\"schema_version\":\"question_zip/2.0\",\"certification_name\":\"架构师\",\"derived_subjects\":[{\"subject_no\":1,\"exam_subject_id\":11,\"label\":\"综合知识\"}]}",
            status, "read_jsonl", BigDecimal.ZERO, validCount, 0, 0, null,
            OffsetDateTime.parse("2026-08-03T10:00:00+08:00"), null, null, 7L, 8L
        );
    }

    private static CmImportBatch batchWithoutSyllabus() {
        return new CmImportBatch(
            101L, "request-101", null, 11L, "imports/question/101/source.zip", "zip-hash", 100L,
            "question", "question-zip/1.0",
            "{\"schema_version\":\"question_zip/2.0\",\"certification_name\":\"架构师\",\"derived_subjects\":[{\"subject_no\":1,\"exam_subject_id\":11,\"label\":\"综合知识\"}]}",
            "parsing", "read_jsonl", BigDecimal.ZERO, 0, 0, 0, null,
            OffsetDateTime.parse("2026-08-03T10:00:00+08:00"), null, null, 7L, 8L
        );
    }

    private static String persistRow(String imagePath) {
        return "{\"images\":[{\"path\":\"" + imagePath
            + "\",\"hash\":\"image-hash\",\"extension\":\"png\"}]}";
    }

    private static byte[] png() {
        return Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Zl7sAAAAASUVORK5CYII="
        );
    }

    private static String validQuestion(String imagePath, String stemImagePath) {
        String images = imagePath.isEmpty() || "images".equals(imagePath) ? "[]" : "[\"" + imagePath + "\"]";
        return "{\"qid\":\"q-1\",\"source\":\"source\",\"subject\":\"架构师\","
            + "\"type\":\"single\",\"question\":\"题干" + stemImagePath + "\","
            + "\"options\":{\"A\":\"甲\",\"B\":\"乙\"},\"answer\":\"A\","
            + "\"analysis\":null,\"knowledge_points\":[{\"subject_no\":1,\"code\":\"K1\"}],"
            + "\"images\":" + images + "}";
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
