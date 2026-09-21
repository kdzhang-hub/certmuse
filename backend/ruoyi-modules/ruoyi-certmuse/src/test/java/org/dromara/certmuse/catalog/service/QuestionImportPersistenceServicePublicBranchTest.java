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
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QuestionImportPersistenceServicePublicBranchTest {
    @Mock
    private ImportMapper repository;
    @Mock
    private PaperDraftCreationService paperDraftCreationService;

    private QuestionImportPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new QuestionImportPersistenceService(
            repository,
            JsonMapper.builder().build(),
            paperDraftCreationService
        );
    }

    @Test
    void rejectsAnEmptySetOfValidatedQuestions() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(questionBatch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Validated question records changed");

        verify(repository, never()).completeQuestionImport(anyLong(), anyInt());
    }

    @Test
    void persistsNullAnswerAndMissingOptionalTextAsNull() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(questionBatch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(questionWithNullOptionalValues()));
        when(repository.completeQuestionImport(101L, 1)).thenReturn(1);

        service.persistBatch(101L);

        verify(repository).insertQuestionRevision(
            anyLong(), anyLong(), eq("CHOICE"), eq("题干"), isNull(), isNull(), isNull(),
            eq("q-null"), eq("source"), eq("content-hash"), eq("semantic-hash"), eq(7L)
        );
    }

    @Test
    void defaultsKnowledgeRolesAccordingToTheirOrder() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(questionBatch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(questionWithImplicitKnowledgeRoles()));
        when(repository.completeQuestionImport(101L, 1)).thenReturn(1);

        service.persistBatch(101L);

        verify(repository).insertQuestionKnowledge(
            anyLong(), anyLong(), anyLong(), eq(31L), eq(11L), eq("primary"), eq(0)
        );
        verify(repository).insertQuestionKnowledge(
            anyLong(), anyLong(), anyLong(), eq(32L), eq(11L), eq("secondary"), eq(1)
        );
    }

    @Test
    void rejectsReuseWithoutARevisionId() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(questionBatch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of("{\"resolution\":\"REUSE\"}"));

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Validated question is missing reused_question_revision_id");

        verify(repository, never()).completeQuestionImport(anyLong(), anyInt());
    }

    @Test
    void rejectsReuseWithANonIntegralRevisionId() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(questionBatch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(
            "{\"resolution\":\"REUSE\",\"reused_question_revision_id\":\"99\"}"
        ));

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Validated question is missing reused_question_revision_id");

        verify(repository, never()).completeQuestionImport(anyLong(), anyInt());
    }

    @Test
    void rejectsANonPositiveExamSubjectId() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(questionBatch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(
            "{\"source_hash\":\"source-hash\",\"exam_subject_id\":0}"
        ));

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Validated question is missing exam_subject_id");

        verify(repository, never()).insertQuestion(anyLong(), any(), anyLong(), any(), anyLong(), any());
        verify(repository, never()).completeQuestionImport(anyLong(), anyInt());
    }

    @Test
    void rejectsPaperImportWhenOnlyTheCollectionRevisionAlreadyExists() {
        prepareReusablePaperImport();
        when(repository.selectPaperImportForUpdate(101L)).thenReturn(paperImport(null, 302L));

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Paper import was already persisted");

        verify(paperDraftCreationService, never()).create(any());
    }

    @Test
    void doesNotUpdatePaperMetadataWhenBatchCompletionFails() {
        prepareReusablePaperImport();
        when(repository.selectPaperImportForUpdate(101L)).thenReturn(paperImport(null, null));
        when(paperDraftCreationService.create(any()))
            .thenReturn(new PaperDraftCreationService.PaperDraftCreationResult(301L, 302L));
        when(repository.completePaperImport(101L, 0, 301L, 302L)).thenReturn(0);

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Paper import completion failed");

        verify(repository, never()).updatePaperImportResult(anyLong(), anyLong(), anyLong());
    }

    @Test
    void rejectsPaperImportWhenItsResultMetadataCannotBeUpdated() {
        prepareReusablePaperImport();
        when(repository.selectPaperImportForUpdate(101L)).thenReturn(paperImport(null, null));
        when(paperDraftCreationService.create(any()))
            .thenReturn(new PaperDraftCreationService.PaperDraftCreationResult(301L, 302L));
        when(repository.completePaperImport(101L, 0, 301L, 302L)).thenReturn(1);
        when(repository.updatePaperImportResult(101L, 301L, 302L)).thenReturn(0);

        assertThatThrownBy(() -> service.persistBatch(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Paper import completion failed");

        verify(repository).updatePaperImportResult(101L, 301L, 302L);
    }

    private void prepareReusablePaperImport() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(paperBatch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(
            "{\"resolution\":\"REUSE\",\"reused_question_revision_id\":99}"
        ));
    }

    private static CmImportBatch questionBatch() {
        return new CmImportBatch(
            101L, "request-1", 21L, null, "imports/question/101/source.zip", "zip-hash", 100L,
            "question", "question-zip/1.0", "{}", "importing", "persist_questions", BigDecimal.ZERO,
            1, 0, 0, null, OffsetDateTime.parse("2026-08-03T10:00:00+08:00"), null, null, 7L, 8L
        );
    }

    private static CmImportBatch paperBatch() {
        return new CmImportBatch(
            101L, "paper-request", 21L, 21L, null, "imports/paper/101/source.zip", "zip-hash", 100L,
            "paper", "question-zip/1.0", "{}", "importing", "persist_questions", BigDecimal.ZERO,
            1, 0, 0, 0, null, OffsetDateTime.parse("2026-08-03T10:00:00+08:00"), null, null, 7L, 8L
        );
    }

    private static PaperImportRow paperImport(Long collectionId, Long collectionRevisionId) {
        return new PaperImportRow(
            101L, 9L, "模拟题集", "PRACTICE", 90, collectionId, collectionRevisionId
        );
    }

    private static String questionWithNullOptionalValues() {
        return "{"
            + "\"qid\":\"q-null\",\"source\":\"source\",\"subject\":null,"
            + "\"source_hash\":\"source-hash\",\"question_type\":\"CHOICE\",\"stem\":\"题干\","
            + "\"answer\":null,\"exam_subject_id\":11,"
            + "\"content_hash\":\"content-hash\",\"semantic_hash\":\"semantic-hash\"}";
    }

    private static String questionWithImplicitKnowledgeRoles() {
        return "{"
            + "\"qid\":\"q-roles\",\"source\":\"source\","
            + "\"source_hash\":\"source-hash\",\"question_type\":\"CHOICE\",\"stem\":\"题干\","
            + "\"answer\":null,\"exam_subject_id\":11,"
            + "\"knowledge_points\":[{\"id\":31},{\"id\":32}],"
            + "\"content_hash\":\"content-hash\",\"semantic_hash\":\"semantic-hash\"}";
    }
}
