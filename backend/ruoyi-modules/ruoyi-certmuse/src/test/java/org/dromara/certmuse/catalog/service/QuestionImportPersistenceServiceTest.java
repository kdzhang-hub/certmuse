package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.PaperImportRow;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.QuestionImportPersistenceService;
import org.dromara.certmuse.question.service.PaperDraftCreationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QuestionImportPersistenceServiceTest {
    @Mock
    private ImportMapper repository;
    @Mock
    private PaperDraftCreationService paperDraftCreationService;

    private QuestionImportPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new QuestionImportPersistenceService(repository, JsonMapper.builder().build(), paperDraftCreationService);
    }

    @Test
    void persistsWholeBatchBehindLockedBatchRow() throws Exception {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(normalizedQuestion()));
        when(repository.completeQuestionImport(101L, 1)).thenReturn(1);

        service.persistBatch(101L);

        verify(repository).selectByIdForUpdate(101L);
        verify(repository).insertQuestion(anyLong(), anyString(), eq(11L), eq("source-hash"), eq(7L), eq(8L));
        verify(repository).insertQuestionRevision(
            anyLong(), anyLong(), eq("CHOICE"), eq("题干"), anyString(), eq("解析"), eq("架构师"),
            eq("q-1"), eq("source"), eq("content-hash"), eq("semantic-hash"), eq(7L)
        );
        verify(repository).completeQuestionImport(101L, 1);
        assertThat(QuestionImportPersistenceService.class.getMethod("persistBatch", long.class)
            .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void rejectsExistingSourceWithoutCompletingBatch() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(normalizedQuestion()));
        when(repository.countQuestionSource("source-hash")).thenReturn(1);

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Question source already exists");

        verify(repository, never()).insertQuestion(anyLong(), anyString(), anyLong(), anyString(), anyLong(), any());
        verify(repository, never()).completeQuestionImport(anyLong(), anyInt());
    }

    @Test
    void rejectsMismatchBetweenValidatedRowsAndBatchCount() {
        CmImportBatch batch = batchWithValidCount(2);
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch);
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(normalizedQuestion()));

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Validated question records changed");

        verify(repository, never()).completeQuestionImport(anyLong(), anyInt());
    }

    @Test
    void rejectsMissingBatchCreator() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batchWithCreator(null));
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(normalizedQuestion()));

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Question import batch has no creator");
    }

    @Test
    void rejectsFailureToCompleteBatchAfterInserts() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(normalizedQuestion()));
        when(repository.completeQuestionImport(101L, 1)).thenReturn(0);

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Question batch completion failed");
    }

    @Test
    void ignoresBatchesThatHaveAlreadyLeftTheImportingState() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batchWithStatus("completed", 1, 7L));

        service.persistBatch(101L);

        verify(repository, never()).selectSuccessfulQuestionResult(101L);
        verify(repository, never()).completeQuestionImport(anyLong(), anyInt());
    }

    @Test
    void rejectsMissingImportBatchAndMalformedNormalizedRows() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(null);
        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Import batch not found");

        when(repository.selectByIdForUpdate(102L)).thenReturn(batchWithStatus("importing", 1, 7L));
        when(repository.selectSuccessfulQuestionResult(102L)).thenReturn(List.of("not-json"));
        assertThatThrownBy(() -> service.persistBatch(102L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Invalid normalized question");
    }

    @Test
    void reusesAnExistingQuestionRevisionWithoutGeneratingANewQuestion() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batchWithStatus("importing", 1, 7L));
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(
            "{\"resolution\":\"REUSE\",\"reused_question_revision_id\":99}"
        ));
        when(repository.completeQuestionImport(101L, 0)).thenReturn(1);

        service.persistBatch(101L);

        verify(repository).completeQuestionImport(101L, 0);
        verify(repository, never()).countQuestionSource(anyString());
        verify(repository, never()).insertQuestion(anyLong(), anyString(), anyLong(), anyString(), anyLong(), any());
    }

    @Test
    void persistsQuestionOptionsImagesAndKnowledgeBindings() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batchWithStatus("importing", 1, 7L));
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(fullQuestion()));
        when(repository.completeQuestionImport(101L, 1)).thenReturn(1);

        service.persistBatch(101L);

        verify(repository).insertQuestionOption(anyLong(), anyLong(), org.mockito.ArgumentMatchers.eq("A"),
            org.mockito.ArgumentMatchers.eq("甲"), org.mockito.ArgumentMatchers.eq(1), eq(7L));
        verify(repository).insertQuestionImage(anyLong(), anyLong(), eq(1),
            org.mockito.ArgumentMatchers.contains("question-imports/zip-hash/images/"), eq("图片1"), eq(7L));
        verify(repository).insertQuestionKnowledge(anyLong(), anyLong(), anyLong(), eq(31L), eq(11L),
            eq("primary"), eq(0));
    }

    @Test
    void createsDraftCollectionForPaperImportsAndCompletesBothRecords() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(paperBatch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(fullQuestion()));
        when(repository.selectPaperImportForUpdate(101L))
            .thenReturn(new PaperImportRow(101L, 9L, "模拟题集", "PRACTICE", 90, null, null));
        when(paperDraftCreationService.create(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new PaperDraftCreationService.PaperDraftCreationResult(301L, 302L));
        when(repository.completePaperImport(101L, 1, 301L, 302L)).thenReturn(1);
        when(repository.updatePaperImportResult(101L, 301L, 302L)).thenReturn(1);

        service.persistBatch(101L);

        ArgumentCaptor<PaperDraftCreationService.PaperDraftCreationCommand> captor =
            ArgumentCaptor.forClass(PaperDraftCreationService.PaperDraftCreationCommand.class);
        verify(paperDraftCreationService).create(captor.capture());
        assertThat(captor.getValue().collectionName()).isEqualTo("模拟题集");
        assertThat(captor.getValue().questionRevisionIds()).hasSize(1);
        verify(repository).completePaperImport(101L, 1, 301L, 302L);
        verify(repository).updatePaperImportResult(101L, 301L, 302L);
    }

    @Test
    void rejectsPaperImportWhenMetadataAlreadyContainsCollectionReferences() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(paperBatch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(fullQuestion()));
        when(repository.selectPaperImportForUpdate(101L))
            .thenReturn(new PaperImportRow(101L, 9L, "模拟题集", "PRACTICE", 90, 301L, null));

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Paper import was already persisted");
        verify(paperDraftCreationService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    private static CmImportBatch batch() {
        return batchWithCreator(7L);
    }

    private static CmImportBatch batchWithValidCount(int validCount) {
        return new CmImportBatch(
            101L, "request-1", 21L, null, "imports/question/101/source.zip", "zip-hash", 100L,
            "question", "question-zip/1.0", "{\"derived_subjects\":[{\"subject_no\":1,\"exam_subject_id\":11}]}", "importing",
            "persist_questions", BigDecimal.ZERO, validCount, 0, 0, null,
            OffsetDateTime.parse("2026-08-03T10:00:00+08:00"), null, null, 7L, 8L
        );
    }

    private static CmImportBatch batchWithCreator(Long creator) {
        return batchWithStatus("importing", 1, creator);
    }

    private static CmImportBatch batchWithStatus(String status, int validCount, Long creator) {
        return new CmImportBatch(
            101L, "request-1", 21L, null, "imports/question/101/source.zip", "zip-hash", 100L,
            "question", "question-zip/1.0", "{\"derived_subjects\":[{\"subject_no\":1,\"exam_subject_id\":11}]}", status,
            "persist_questions", BigDecimal.ZERO, validCount, 0, 0, null,
            OffsetDateTime.parse("2026-08-03T10:00:00+08:00"), null, null, creator, 8L
        );
    }

    private static CmImportBatch paperBatch() {
        return new CmImportBatch(
            101L, "paper-request", 21L, 21L, null, "imports/paper/101/source.zip", "zip-hash", 100L,
            "paper", "question-zip/1.0", "{}", "importing", "persist_questions", BigDecimal.ZERO,
            1, 0, 0, 0, null, OffsetDateTime.parse("2026-08-03T10:00:00+08:00"), null, null, 7L, 8L
        );
    }

    private static String normalizedQuestion() {
        return "{"
            + "\"qid\":\"q-1\",\"source\":\"source\",\"subject\":\"架构师\","
            + "\"source_hash\":\"source-hash\",\"question_type\":\"CHOICE\",\"stem\":\"题干\","
            + "\"options\":[{\"label\":\"A\",\"text\":\"甲\"},{\"label\":\"B\",\"text\":\"乙\"}],"
            + "\"answer\":{\"answer_type\":\"option_keys\",\"value\":[\"A\"]},\"analysis\":\"解析\","
            + "\"images\":[],\"subject_no\":1,\"exam_subject_id\":11,\"knowledge_points\":[{\"id\":31,\"role\":\"primary\",\"sort_order\":0}],"
            + "\"content_hash\":\"content-hash\",\"semantic_hash\":\"semantic-hash\"}";
    }

    private static String fullQuestion() {
        return "{"
            + "\"qid\":\"q-1\",\"source\":\"source\",\"subject\":\"架构师\","
            + "\"source_hash\":\"source-hash\",\"question_type\":\"CHOICE\",\"stem\":\"题干\","
            + "\"options\":[{\"label\":\"A\",\"text\":\"甲\"},{\"label\":\"B\",\"text\":\"乙\"}],"
            + "\"answer\":{\"answer_type\":\"option_keys\",\"value\":[\"A\"]},\"analysis\":\"解析\","
            + "\"images\":[{\"hash\":\"image-hash\",\"extension\":\"png\"}],"
            + "\"subject_no\":1,\"exam_subject_id\":11,\"knowledge_points\":[{\"id\":31,\"role\":\"primary\",\"sort_order\":0}],"
            + "\"content_hash\":\"content-hash\",\"semantic_hash\":\"semantic-hash\"}";
    }
}
