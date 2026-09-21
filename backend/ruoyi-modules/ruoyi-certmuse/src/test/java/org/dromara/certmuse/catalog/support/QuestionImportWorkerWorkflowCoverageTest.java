package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.PaperQuestionReuse;
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
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Observable import-worker workflows not covered by the baseline validation suite. */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QuestionImportWorkerWorkflowCoverageTest {
    private static final long BATCH_ID = 101L;
    private static final String CONFIG = "{\"schema_version\":\"question_zip/2.0\","
        + "\"certification_name\":\"架构师\",\"derived_subjects\":[{\"subject_no\":1,"
        + "\"exam_subject_id\":11,\"label\":\"综合知识\"}]}";

    @Mock private ImportMapper repository;
    @Mock private ImportStorage storage;
    @Mock private ImportPersistenceService importPersistence;
    @Mock private QuestionImportPersistenceService questionPersistence;
    @Mock private ImportProgressPublisher progressPublisher;

    private QuestionImportWorker worker;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder().build();
        worker = new QuestionImportWorker(
            repository, storage, importPersistence, questionPersistence, mapper,
            new ImportJsonDocumentFactory(mapper), progressPublisher
        );
    }

    @Test
    void exposesTheMatchedRevisionWhenAPaperQuestionCanReuseExactlyOneExistingQuestion() throws Exception {
        String row = question("q-reuse", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A", KNOWLEDGE, "[]");
        arrangeValidation("paper", zip(Map.of("questions.jsonl", row.getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.findPaperQuestionReuses(eq(11L), eq(21L), anyString(), eq(7L)))
            .thenReturn(List.of(new PaperQuestionReuse(501L, 601L, "answer_schema/1.0")));
        when(repository.createRecord(anyLong(), eq(1), eq("q-reuse"), anyString(), anyString())).thenReturn(41L);

        worker.validate(BATCH_ID);

        ArgumentCaptor<String> result = ArgumentCaptor.forClass(String.class);
        verify(repository).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), result.capture(), eq("success"));
        var normalized = JsonMapper.builder().build().readTree(result.getValue());
        assertThat(normalized.path("resolution").asText()).isEqualTo("REUSE");
        assertThat(normalized.path("reused_question_revision_id").asLong()).isEqualTo(601L);
        assertThat(normalized.path("reused_answer_schema").asText()).isEqualTo("answer_schema/1.0");
    }

    @Test
    void makesAnAmbiguousPaperReuseAnActionableValidationError() {
        String row = question("q-ambiguous", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A", KNOWLEDGE, "[]");
        arrangeValidation("paper", zip(Map.of("questions.jsonl", row.getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.findPaperQuestionReuses(eq(11L), eq(21L), anyString(), eq(7L))).thenReturn(List.of(
            new PaperQuestionReuse(501L, 601L, "answer_schema/1.0"),
            new PaperQuestionReuse(502L, 602L, "answer_schema/1.0")
        ));
        when(repository.createRecord(anyLong(), eq(1), eq("q-ambiguous"), anyString(), anyString())).thenReturn(41L);

        worker.validate(BATCH_ID);

        verify(repository).createIssue(
            eq(BATCH_ID), eq(41L), eq("PAPER_DUPLICATE_AMBIGUOUS"), eq("question"), eq("error"), anyString()
        );
        verify(repository).markRecord(41L, "failed");
        verify(repository, never()).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), anyString(), eq("success"));
    }

    @Test
    void rejectsPaperValidationWhenTheDurableBatchHasNoSyllabus() {
        String row = question("q-no-syllabus", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A", KNOWLEDGE, "[]");
        CmImportBatch batch = batch("paper", null, "parsing", 0);
        when(repository.selectById(BATCH_ID)).thenReturn(batch);
        readSource(batch, zip(Map.of("questions.jsonl", row.getBytes(StandardCharsets.UTF_8))));

        assertThatThrownBy(() -> worker.validate(BATCH_ID))
            .isInstanceOfSatisfying(IllegalStateException.class, error -> {
                assertThat(error).hasMessage("Question ZIP precheck failed");
                assertThat(error.getCause()).hasMessage("Paper import batch has no syllabus version");
            });

        verify(repository, never()).finishPaper(anyLong(), anyString());
        verifyNoInteractions(progressPublisher);
    }

    @Test
    void rejectsAValidatedImageThatIsNoLongerPresentBeforeWritingQuestions() {
        CmImportBatch batch = batch("question", 21L, "importing", 1);
        when(repository.selectById(BATCH_ID)).thenReturn(batch);
        when(repository.selectSuccessfulQuestionResult(BATCH_ID)).thenReturn(List.of(persistRow("images/gone.png")));
        readSource(batch, zip(Map.of("questions.jsonl", "ignored".getBytes(StandardCharsets.UTF_8))));

        assertThatThrownBy(() -> worker.persist(BATCH_ID))
            .isInstanceOfSatisfying(IllegalStateException.class, error ->
                assertThat(error).hasMessage("Validated image disappeared: images/gone.png")
            );

        verify(questionPersistence, never()).persistBatch(BATCH_ID);
        verify(importPersistence, never()).enqueueImageCleanup(anyString(), anyString(), anyString());
    }

    @Test
    void keepsTheOriginalPersistFailureWhenCleanupSchedulingAlsoFails() {
        CmImportBatch batch = batch("question", 21L, "importing", 1);
        when(repository.selectById(BATCH_ID)).thenReturn(batch);
        when(repository.selectSuccessfulQuestionResult(BATCH_ID)).thenReturn(List.of(persistRow("images/a.png")));
        readSource(batch, zip(Map.of(
            "questions.jsonl", "ignored".getBytes(StandardCharsets.UTF_8),
            "images/a.png", png()
        )));
        doThrow(new IllegalStateException("database unavailable")).when(questionPersistence).persistBatch(BATCH_ID);
        doThrow(new IllegalStateException("cleanup queue unavailable"))
            .when(importPersistence).enqueueImageCleanup(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> worker.persist(BATCH_ID))
            .isInstanceOfSatisfying(IllegalStateException.class, error ->
                assertThat(error).hasMessage("database unavailable")
            );

        verify(importPersistence).enqueueImageCleanup(
            eq(QuestionImportWorker.imageKey("zip-hash", "image-hash", "png")), eq("zip-hash"), eq("question_persist_failed")
        );
    }

    @Test
    void rejectsCaseQuestionsThatReferenceANonLeafKnowledgePoint() throws Exception {
        String row = question("q-case", "subjective", "{}", "参考答案", KNOWLEDGE, "[]");
        arrangeValidation("question", zip(Map.of("questions.jsonl", row.getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(false);
        when(repository.createRecord(anyLong(), eq(1), eq("q-case"), anyString(), anyString())).thenReturn(41L);

        worker.validate(BATCH_ID);

        verify(repository).createIssue(
            eq(BATCH_ID), eq(41L), eq("QUESTION_KNOWLEDGE_NOT_LEAF"), eq("knowledge_points[0].code"), eq("error"), anyString()
        );
        verify(repository).markRecord(41L, "failed");
        verify(repository, never()).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), anyString(), eq("success"));
    }

    @Test
    void reportsARepeatedKnowledgeCodeAsAnImportError() {
        String repeatedKnowledge = "[{\"subject_no\":1,\"code\":\"K1\"},{\"subject_no\":1,\"code\":\"K1\"}]";
        String row = question("q-duplicate-knowledge", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A", repeatedKnowledge, "[]");
        arrangeValidation("question", zip(Map.of("questions.jsonl", row.getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), eq(1), eq("q-duplicate-knowledge"), anyString(), anyString())).thenReturn(41L);

        worker.validate(BATCH_ID);

        verify(repository).createIssue(
            eq(BATCH_ID), eq(41L), eq("QUESTION_KNOWLEDGE_DUPLICATE"), eq("knowledge_points[1].code"), eq("error"), anyString()
        );
        verify(repository).markRecord(41L, "failed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"subjective", "essay"})
    void rejectsInlineScoringPointsForEachSubjectiveQuestionType(String type) {
        String scores = "[{\"scoring_code\":\"SP1\",\"description\":\"说明关键职责\",\"max_score\":3.00,"
            + "\"sort_order\":1,\"knowledge_points\":[{\"subject_no\":1,\"code\":\"K1\"}]}]";
        String row = question("q-score-" + type, type, "{}", "参考答案", KNOWLEDGE, "[]", scores);
        arrangeValidation("question", zip(Map.of("questions.jsonl", row.getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.createRecord(anyLong(), eq(1), eq("q-score-" + type), anyString(), anyString())).thenReturn(41L);

        worker.validate(BATCH_ID);

        verify(repository).createIssue(eq(BATCH_ID), eq(41L), eq("QUESTION_SCORING_POINTS_UNSUPPORTED"),
            eq("scoring_points"), eq("error"), anyString());
        verify(repository).markRecord(41L, "failed");
    }

    @Test
    void acceptsAnExplicitlyEmptyScoringPointArrayForSubjectiveQuestions() {
        String row = question("q-empty-scores", "subjective", "{}", "参考答案", KNOWLEDGE, "[]", "[]");
        arrangeValidation("question", zip(Map.of("questions.jsonl", row.getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), eq(1), eq("q-empty-scores"), anyString(), anyString())).thenReturn(41L);

        worker.validate(BATCH_ID);

        verify(repository, never()).createIssue(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString());
        verify(repository).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), anyString(), eq("success"));
    }

    @Test
    void rejectsScoringPointsForChoiceQuestions() {
        String scores = "[{\"scoring_code\":\"SP1\",\"description\":\"错误的评分点\",\"max_score\":1,"
            + "\"sort_order\":1,\"knowledge_points\":[{\"subject_no\":1,\"code\":\"K1\"}]}]";
        String row = question("q-choice-scores", "single", "{\"A\":\"甲\",\"B\":\"乙\"}", "A", KNOWLEDGE, "[]", scores);
        arrangeValidation("question", zip(Map.of("questions.jsonl", row.getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), eq(1), eq("q-choice-scores"), anyString(), anyString())).thenReturn(41L);

        worker.validate(BATCH_ID);

        verify(repository).createIssue(
            eq(BATCH_ID), eq(41L), eq("QUESTION_SCORING_POINTS_UNSUPPORTED"), eq("scoring_points"), eq("error"), anyString()
        );
        verify(repository).markRecord(41L, "failed");
    }

    @Test
    void rejectsScoringPointKnowledgeThatIsNotBoundToTheQuestion() {
        String scores = "[{\"scoring_code\":\"SP1\",\"description\":\"错误的知识点\",\"max_score\":1,"
            + "\"sort_order\":1,\"knowledge_points\":[{\"subject_no\":1,\"code\":\"K2\"}]}]";
        String row = question("q-score-knowledge", "subjective", "{}", "参考答案", KNOWLEDGE, "[]", scores);
        arrangeValidation("question", zip(Map.of("questions.jsonl", row.getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.createRecord(anyLong(), eq(1), eq("q-score-knowledge"), anyString(), anyString())).thenReturn(41L);

        worker.validate(BATCH_ID);

        verify(repository).createIssue(
            eq(BATCH_ID), eq(41L), eq("QUESTION_SCORING_POINTS_UNSUPPORTED"),
            eq("scoring_points"), eq("error"), anyString()
        );
        verify(repository).markRecord(41L, "failed");
    }

    @Test
    void emptyAndOmittedScoringPointsProduceTheSameQuestionContentHash() throws Exception {
        arrangeValidation(BATCH_ID, "question", zip(Map.of(
            "questions.jsonl", question("q-hash", "essay", "{}", "参考答案", KNOWLEDGE, "[]").getBytes(StandardCharsets.UTF_8)
        )));
        arrangeValidation(102L, "question", zip(Map.of(
            "questions.jsonl", question("q-hash", "essay", "{}", "参考答案", KNOWLEDGE, "[]", "[]").getBytes(StandardCharsets.UTF_8)
        )));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(eq(BATCH_ID), eq(1), eq("q-hash"), anyString(), anyString())).thenReturn(41L);
        when(repository.createRecord(eq(102L), eq(1), eq("q-hash"), anyString(), anyString())).thenReturn(42L);

        worker.validate(BATCH_ID);
        worker.validate(102L);

        ArgumentCaptor<String> firstResult = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> secondResult = ArgumentCaptor.forClass(String.class);
        verify(repository).updateQuestionRecordResult(eq(41L), eq(1), eq(11L), firstResult.capture(), eq("success"));
        verify(repository).updateQuestionRecordResult(eq(42L), eq(1), eq(11L), secondResult.capture(), eq("success"));
        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(mapper.readTree(firstResult.getValue()).path("content_hash").asText())
            .isEqualTo(mapper.readTree(secondResult.getValue()).path("content_hash").asText());
    }

    @Test
    void rejectsInlineScoringPointsDuringPaperImport() {
        String scores = "[{\"scoring_code\":\"SP1\",\"description\":\"说明关键职责\",\"max_score\":3.00,"
            + "\"sort_order\":1,\"knowledge_points\":[{\"subject_no\":1,\"code\":\"K1\"}]}]";
        String row = question("q-paper-scores", "essay", "{}", "参考答案", KNOWLEDGE, "[]", scores);
        arrangeValidation("paper", zip(Map.of("questions.jsonl", row.getBytes(StandardCharsets.UTF_8))));
        when(repository.findKnowledgePoint(21L, 11L, "K1")).thenReturn(31L);
        when(repository.isKnowledgePointLeaf(31L)).thenReturn(true);
        when(repository.createRecord(anyLong(), eq(1), eq("q-paper-scores"), anyString(), anyString())).thenReturn(41L);

        worker.validate(BATCH_ID);

        verify(repository).createIssue(eq(BATCH_ID), eq(41L), eq("QUESTION_SCORING_POINTS_UNSUPPORTED"),
            eq("scoring_points"), eq("error"), anyString());
        verify(repository).markRecord(41L, "failed");
    }

    private void arrangeValidation(String importType, byte[] source) {
        arrangeValidation(BATCH_ID, importType, source);
    }

    private void arrangeValidation(long batchId, String importType, byte[] source) {
        CmImportBatch batch = batch(batchId, importType, 21L, "parsing", 0);
        when(repository.selectById(batchId)).thenReturn(batch);
        readSource(batch, source);
    }

    @SuppressWarnings("unchecked")
    private void readSource(CmImportBatch batch, byte[] source) {
        doAnswer(invocation -> {
            Function<InputStream, Object> reader = invocation.getArgument(1);
            return reader.apply(new ByteArrayInputStream(source));
        }).when(storage).read(eq(batch.sourceFilePath()), any());
    }

    private static CmImportBatch batch(String importType, Long syllabusVersionId, String status, int validCount) {
        return batch(BATCH_ID, importType, syllabusVersionId, status, validCount);
    }

    private static CmImportBatch batch(long batchId, String importType, Long syllabusVersionId, String status, int validCount) {
        return new CmImportBatch(
            batchId, "request-" + batchId, null, syllabusVersionId, 11L,
            "imports/" + importType + "/" + batchId + "/source.zip", "zip-hash", 100L,
            importType, "question-zip/1.0", CONFIG, status, "read_jsonl", BigDecimal.ZERO,
            validCount, 0, 0, 0, null, OffsetDateTime.parse("2026-08-13T10:00:00+08:00"),
            null, null, 7L, 8L, null, null, 9L
        );
    }

    private static String question(String qid, String type, String options, String answer, String knowledge, String images) {
        return question(qid, type, options, answer, knowledge, images, null);
    }

    private static String question(
        String qid, String type, String options, String answer, String knowledge, String images, String scoringPoints
    ) {
        return "{\"qid\":\"" + qid + "\",\"source\":\"source-" + qid + "\",\"subject\":\"架构师\","
            + "\"type\":\"" + type + "\",\"question\":\"题干-" + qid + "\",\"options\":" + options
            + ",\"answer\":\"" + answer + "\",\"analysis\":null,\"knowledge_points\":" + knowledge
            + ",\"images\":" + images + (scoringPoints == null ? "" : ",\"scoring_points\":" + scoringPoints) + "}";
    }

    private static final String KNOWLEDGE = "[{\"subject_no\":1,\"code\":\"K1\"}]";

    private static String persistRow(String imagePath) {
        return "{\"images\":[{\"path\":\"" + imagePath
            + "\",\"hash\":\"image-hash\",\"extension\":\"png\"}]}";
    }

    private static byte[] png() {
        return java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Zl7sAAAAASUVORK5CYII="
        );
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
