package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.KnowledgeImportReconciliationService;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.catalog.support.ImportJsonDocumentFactory;
import org.dromara.certmuse.question.service.QuestionKnowledgeMaintenanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Public persistence workflows for compatibility entry points and state races. */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportPersistenceServicePublicWorkflowTest {
    private static final long BATCH_ID = 101L;
    private static final long SYLLABUS_ID = 21L;
    private static final long CERTIFICATION_ID = 9L;
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-08-13T10:00:00+08:00");

    @Mock private ImportMapper repository;
    @Mock private KnowledgeImportReconciliationService reconciliation;
    @Mock private QuestionKnowledgeMaintenanceService questionMaintenance;

    private ImportPersistenceService service;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder().build();
        service = new ImportPersistenceService(
            repository, mapper, new ImportJsonDocumentFactory(mapper), reconciliation, questionMaintenance
        );
    }

    @Test
    void createsACertificationScopedKnowledgeImportAndCompletesItsIdempotencyRecord() {
        when(repository.completeIdempotency(eq("knowledge_point_precheck_create"), eq("knowledge-request"), eq(BATCH_ID), eq("{}")))
            .thenReturn(1);

        service.create(
            BATCH_ID, "knowledge-request", "payload-hash", SYLLABUS_ID, "imports/tree/source.jsonl", "file-hash",
            "tree.jsonl", 64L, "application/jsonl", "{}", 7L, 8L, TIME, "{}", CERTIFICATION_ID
        );

        InOrder order = inOrder(repository);
        order.verify(repository).insertIdempotency(anyLong(), eq("knowledge_point_precheck_create"), eq("knowledge-request"), eq("payload-hash"));
        order.verify(repository).insertSyllabus(SYLLABUS_ID, CERTIFICATION_ID, 7L, 8L);
        order.verify(repository).insertBatch(
            BATCH_ID, "knowledge-request", SYLLABUS_ID, "imports/tree/source.jsonl", "file-hash", "tree.jsonl",
            64L, "application/jsonl", "{}", 7L, 8L, TIME
        );
        order.verify(repository).completeIdempotency("knowledge_point_precheck_create", "knowledge-request", BATCH_ID, "{}");
    }

    @Test
    void rejectsCompatibilityQuestionAndTextbookCreationWhenTheirSyllabusNoLongerExists() {
        when(repository.selectSyllabusCertification(SYLLABUS_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.createQuestion(
            BATCH_ID, "question-request", "payload-hash", SYLLABUS_ID, "imports/questions/source.zip", "file-hash",
            "questions.zip", 64L, "application/zip", "{}", 7L, 8L, TIME, "{}"
        )).isInstanceOfSatisfying(IllegalStateException.class, error ->
            assertThat(error).hasMessage("Question import syllabus does not exist")
        );
        assertThatThrownBy(() -> service.createTextbook(
            BATCH_ID, null, true, "textbook-request", "payload-hash", SYLLABUS_ID, "教材", "第一版",
            "imports/textbook/source.jsonl", "file-hash", "book.jsonl", 64L, "application/jsonl", "{}",
            7L, 8L, TIME, "{}"
        )).isInstanceOfSatisfying(IllegalStateException.class, error ->
            assertThat(error).hasMessage("Textbook import syllabus does not exist")
        );

        verify(repository, never()).insertQuestionBatch(anyLong(), anyString(), any(), anyLong(), anyString(), anyString(),
            anyString(), anyLong(), anyString(), anyString(), anyLong(), any(), any());
        verify(repository, never()).insertTextbookBatch(anyLong(), anyString(), any(), anyLong(), any(), anyString(), anyString(),
            anyString(), anyLong(), anyString(), anyString(), anyLong(), any(), any());
    }

    @Test
    void createsCompatibilityQuestionAndTextbookImportsUsingTheResolvedQualification() {
        when(repository.selectSyllabusCertification(SYLLABUS_ID)).thenReturn(CERTIFICATION_ID);
        when(repository.completeIdempotency(anyString(), anyString(), eq(BATCH_ID), eq("{}"))).thenReturn(1);

        service.createQuestion(
            BATCH_ID, "question-request", "question-hash", SYLLABUS_ID, "imports/questions/source.zip", "file-hash",
            "questions.zip", 64L, "application/zip", "{}", 7L, 8L, TIME, "{}"
        );
        service.createTextbook(
            BATCH_ID, null, true, "textbook-request", "textbook-hash", SYLLABUS_ID, "教材", "第一版",
            "imports/textbook/source.jsonl", "file-hash", "book.jsonl", 64L, "application/jsonl", "{}",
            7L, 8L, TIME, "{}"
        );

        verify(repository).insertQuestionBatch(
            BATCH_ID, "question-request", SYLLABUS_ID, CERTIFICATION_ID, "imports/questions/source.zip", "file-hash",
            "questions.zip", 64L, "application/zip", "{}", 7L, 8L, TIME
        );
        verify(repository).insertTextbookBatch(
            BATCH_ID, "textbook-request", null, CERTIFICATION_ID, SYLLABUS_ID, "imports/textbook/source.jsonl", "file-hash",
            "book.jsonl", 64L, "application/jsonl", "{}", 7L, 8L, TIME
        );
    }

    @Test
    void acceptsPaperPrecheckOnlyAfterClaimingTheBatchAndReturnsTheDurableJobContract() throws Exception {
        when(repository.start(BATCH_ID)).thenReturn(1);
        when(repository.completeIdempotency(eq("paper_precheck_validate"), eq("paper-validate"), eq(BATCH_ID), eq("{}")))
            .thenReturn(1);

        service.acceptPaperValidation(BATCH_ID, "paper-validate", "payload-hash", "{}");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(repository).enqueueJob(anyLong(), eq("paper_precheck"), eq("101"), payload.capture());
        var parsed = JsonMapper.builder().build().readTree(payload.getValue());
        assertThat(parsed.path("schema_version").asText()).isEqualTo("paper_precheck_job/1.0");
        assertThat(parsed.path("batch_id").asText()).isEqualTo("101");
        verify(repository).completeIdempotency("paper_precheck_validate", "paper-validate", BATCH_ID, "{}");
    }

    @Test
    void preservesThePaperValidationCompletionFailureAfterTheBatchWasSuccessfullyClaimed() {
        when(repository.start(BATCH_ID)).thenReturn(1);
        when(repository.completeIdempotency(eq("paper_precheck_validate"), eq("paper-validate"), eq(BATCH_ID), eq("{}")))
            .thenReturn(0);

        assertThatThrownBy(() -> service.acceptPaperValidation(BATCH_ID, "paper-validate", "payload-hash", "{}"))
            .isInstanceOfSatisfying(IllegalStateException.class, error ->
                assertThat(error).hasMessage("Paper validation idempotency completion failed")
            );

        verify(repository).enqueueJob(anyLong(), eq("paper_precheck"), eq("101"), anyString());
    }

    @Test
    void distinguishesQuestionAndPaperConfirmationStateFailures() {
        when(repository.acceptQuestionConfirmation(BATCH_ID, 7L, TIME)).thenReturn(0);
        when(repository.acceptPaperConfirmation(BATCH_ID, 7L, TIME)).thenReturn(0);

        assertThatThrownBy(() -> service.acceptQuestionConfirmation(
            BATCH_ID, "question-confirm", "payload-hash", 7L, TIME, "{}"
        )).isInstanceOfSatisfying(ImportException.class, error -> {
            assertThat(error.code()).isEqualTo(409);
            assertThat(error.errorCode()).isEqualTo("IMPORT_BATCH_STATE_INVALID");
        });
        assertThatThrownBy(() -> service.acceptPaperConfirmation(
            BATCH_ID, "paper-confirm", "payload-hash", 7L, TIME, "{}"
        )).isInstanceOfSatisfying(ImportException.class, error -> {
            assertThat(error.code()).isEqualTo(422);
            assertThat(error.errorCode()).isEqualTo("PAPER_IMPORT_PRECHECK_FAILED");
        });

        verify(repository, never()).enqueueJob(anyLong(), eq("question_persist"), anyString(), anyString());
        verify(repository, never()).enqueueJob(anyLong(), eq("paper_persist"), anyString(), anyString());
    }
}
