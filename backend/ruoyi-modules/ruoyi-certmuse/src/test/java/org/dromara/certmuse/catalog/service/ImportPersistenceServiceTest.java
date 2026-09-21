package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.KnowledgePointInsert;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffRow;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.dromara.certmuse.catalog.domain.TextbookChunkInsert;
import org.dromara.certmuse.catalog.domain.TextbookChunkKnowledgeInsert;
import org.dromara.certmuse.catalog.domain.TextbookImportDocument;
import org.dromara.certmuse.catalog.domain.TextbookImportRecordData;
import org.dromara.certmuse.catalog.domain.TextbookKnowledgePointLookup;
import org.dromara.certmuse.catalog.domain.TextbookRecordResultUpdate;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.KnowledgeImportReconciliationService;
import org.dromara.certmuse.question.service.QuestionKnowledgeMaintenanceService;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.catalog.support.ImportJsonDocumentFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportPersistenceServiceTest {

    @Mock
    private ImportMapper repository;
    @Mock
    private KnowledgeImportReconciliationService reconciliationService;
    @Mock
    private QuestionKnowledgeMaintenanceService questionMaintenanceService;

    private ImportPersistenceService service;

    @BeforeEach
    void setUp() {
        JsonMapper objectMapper = JsonMapper.builder().build();
        service = new ImportPersistenceService(
            repository,
            objectMapper,
            new ImportJsonDocumentFactory(objectMapper),
            reconciliationService,
            questionMaintenanceService
        );
    }

    @Test
    void persistsValidatedTreeWithResolvedParentIds() {
        CmImportBatch batch = batch("importing", 2);
        batch = new CmImportBatch(batch.id(), batch.requestId(), batch.documentId(), batch.syllabusVersionId(),
            batch.examSubjectId(), batch.sourceFilePath(), batch.sourceFileHash(), batch.sourceFileSize(),
            batch.importType(), batch.templateVersion(), batch.parseConfig(), batch.status(), batch.currentStage(),
            batch.progressPercent(), batch.validCount(), batch.warningCount(), batch.failedCount(), batch.generatedCount(),
            batch.traceId(), batch.createTime(), batch.startedTime(), batch.finishedTime(), batch.createBy(), batch.createDept(),
            "base", "resolution");
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch);
        when(repository.lockSyllabusVersion(21L)).thenReturn(21L);
        when(repository.selectCurrentKnowledgePoints(21L)).thenReturn(List.of());
        when(reconciliationService.baselineHash(List.of())).thenReturn("base");
        KnowledgeImportPointRow root = point(201L, "1.1", null, 1);
        KnowledgeImportPointRow child = point(202L, "1.1.1", "1.1", 2);
        when(repository.selectImportedKnowledgePoints(101L)).thenReturn(List.of(root, child));
        when(repository.selectAllKnowledgeImportDiffs(101L)).thenReturn(List.of(addDiff(301L, 201L), addDiff(302L, 202L)));
        when(repository.insertKnowledgePoints(anyList())).thenAnswer(invocation ->
            ((List<?>) invocation.getArgument(0)).size()
        );
        when(repository.completeImport(101L, 2)).thenReturn(1);

        service.persistKnowledgeTree(101L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgePointInsert>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertKnowledgePoints(captor.capture());
        List<KnowledgePointInsert> points = captor.getValue();
        assertThat(points).hasSize(2);
        assertThat(points.get(0).parentId()).isNull();
        assertThat(points.get(1).parentId()).isEqualTo(points.get(0).id());
        assertThat(points).allMatch(point -> point.examSubjectId() == 11L);
    }

    @Test
    void persistsAnInitialTreeWithoutDiffDecisions() {
        CmImportBatch batch = batchWithHashes("importing", 2);
        KnowledgeImportPointRow root = point(201L, "1.1", null, 1);
        KnowledgeImportPointRow child = point(202L, "1.1.1", "1.1", 2);

        when(repository.selectByIdForUpdate(101L)).thenReturn(batch);
        when(repository.lockSyllabusVersion(21L)).thenReturn(21L);
        when(repository.selectCurrentKnowledgePoints(21L)).thenReturn(List.of());
        when(reconciliationService.baselineHash(List.of())).thenReturn("base");
        when(repository.selectImportedKnowledgePoints(101L)).thenReturn(List.of(root, child));
        when(repository.selectAllKnowledgeImportDiffs(101L)).thenReturn(List.of());
        when(repository.insertKnowledgePoints(anyList())).thenAnswer(invocation ->
            ((List<?>) invocation.getArgument(0)).size()
        );
        when(repository.completeImport(101L, 2)).thenReturn(1);

        service.persistKnowledgeTree(101L);

        verify(repository).insertKnowledgePoints(anyList());
        verify(repository).completeImport(101L, 2);
    }

    @Test
    void refusesKnowledgePersistenceWhenTheBaselineChanged() {
        CmImportBatch batch = batchWithHashes("importing", 1);
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch);
        when(repository.lockSyllabusVersion(21L)).thenReturn(21L);
        when(repository.selectCurrentKnowledgePoints(21L)).thenReturn(List.of());
        when(reconciliationService.baselineHash(List.of())).thenReturn("different-baseline");

        assertThatThrownBy(() -> service.persistKnowledgeTree(101L))
            .isInstanceOf(ImportException.class)
            .extracting(error -> ((ImportException) error).errorCode())
            .isEqualTo("KNOWLEDGE_TREE_BASELINE_CHANGED");

        verify(repository, never()).softDeleteActiveKnowledgePoints(anyLong(), any());
    }

    @Test
    void refusesKnowledgePersistenceWhenAResolutionHashIsStale() {
        CmImportBatch batch = batchWithHashes("importing", 1);
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch);
        when(repository.lockSyllabusVersion(21L)).thenReturn(21L);
        when(repository.selectCurrentKnowledgePoints(21L)).thenReturn(List.of());
        when(reconciliationService.baselineHash(List.of())).thenReturn("base");
        when(repository.countKnowledgeImportDiffs(101L, null, null, null, false, null, null)).thenReturn(1L);
        when(reconciliationService.currentResolutionHash(101L)).thenReturn("stale-resolution");

        assertThatThrownBy(() -> service.persistKnowledgeTree(101L))
            .isInstanceOf(ImportException.class)
            .extracting(error -> ((ImportException) error).errorCode())
            .isEqualTo("KNOWLEDGE_DIFF_UNRESOLVED");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void updatesConfirmedKnowledgePointsAndInsertsNewOnesAfterAllDecisionsAreResolved() {
        CmImportBatch batch = batchWithHashes("importing", 2);
        KnowledgeImportPointRow existing = point(900L, "1.0", null, 1);
        existing.setKnowledgePointId(900L);
        KnowledgeImportPointRow updated = point(201L, "1.1", null, 1);
        KnowledgeImportPointRow added = point(202L, "1.2", null, 1);
        KnowledgeImportDiffRow update = addDiff(301L, 201L);
        update.setAction("update");
        update.setConfirmedKnowledgePointId(900L);
        KnowledgeImportDiffRow add = addDiff(302L, 202L);

        when(repository.selectByIdForUpdate(101L)).thenReturn(batch);
        when(repository.lockSyllabusVersion(21L)).thenReturn(21L);
        when(repository.selectCurrentKnowledgePoints(21L)).thenReturn(List.of(existing));
        when(reconciliationService.baselineHash(List.of(existing))).thenReturn("base");
        when(repository.countKnowledgeImportDiffs(101L, null, null, null, false, null, null)).thenReturn(2L);
        when(reconciliationService.currentResolutionHash(101L)).thenReturn("resolution");
        when(repository.countPendingKnowledgeImportDiffs(101L)).thenReturn(0L);
        when(repository.selectImportedKnowledgePoints(101L)).thenReturn(List.of(updated, added));
        when(repository.selectAllKnowledgeImportDiffs(101L)).thenReturn(List.of(update, add));
        when(repository.updateKnowledgePoints(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(repository.insertKnowledgePoints(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(repository.completeImport(101L, 2)).thenReturn(1);

        service.persistKnowledgeTree(101L);

        verify(repository).updateKnowledgePoints(anyList());
        verify(repository).insertKnowledgePoints(anyList());
        verify(questionMaintenanceService).maintainAfterKnowledgeDeletion(eq(Set.of()), eq(101L), eq(7L));
        verify(repository).completeImport(101L, 2);
    }

    @Test
    void rejectsConfirmationWhenBatchCannotBeClaimed() {
        when(repository.acceptConfirmation(101L, 7L, OffsetDateTime.parse("2026-07-31T10:00:00+08:00")))
            .thenReturn(0);

        assertThatThrownBy(() -> service.acceptConfirmation(
            101L,
            "confirm-1",
            "payload-hash",
            7L,
            OffsetDateTime.parse("2026-07-31T10:00:00+08:00"),
            "{}"
        )).isInstanceOf(ImportException.class)
            .extracting(error -> ((ImportException) error).errorCode())
            .isEqualTo("IMPORT_BATCH_STATE_INVALID");
    }

    @Test
    void reportsStateConflictWhenConfirmationLosesTheRace() {
        OffsetDateTime confirmedTime = OffsetDateTime.parse("2026-07-31T10:00:00+08:00");
        when(repository.acceptConfirmation(101L, 7L, confirmedTime)).thenReturn(0);
        assertThatThrownBy(() -> service.acceptConfirmation(
            101L, "confirm-1", "payload-hash", 7L, confirmedTime, "{}"
        )).isInstanceOf(ImportException.class)
            .satisfies(error -> {
                ImportException exception = (ImportException) error;
                assertThat(exception.code()).isEqualTo(409);
                assertThat(exception.errorCode()).isEqualTo("IMPORT_BATCH_STATE_INVALID");
            });
    }

    @Test
    void validCreatesBothImportTypesAndCompletesTheirIdempotencyRecords() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-03T10:00:00+08:00");
        when(repository.completeIdempotency(anyString(), eq("r1"), eq(101L), eq("{}"))).thenReturn(1);

        service.create(101L, "r1", "hash", 21L, "object", "file-hash", "tree.jsonl", 3L,
            "application/jsonl", "{}", 7L, 8L, time, "{}");
        service.createQuestion(101L, "r1", "hash", 21L, 9L, "object", "file-hash", "questions.zip", 3L,
            "application/zip", "{}", 7L, 8L, time, "{}");

        verify(repository).insertBatch(eq(101L), eq("r1"), eq(21L), eq("object"), eq("file-hash"),
            eq("tree.jsonl"), eq(3L), eq("application/jsonl"), eq("{}"), eq(7L), eq(8L), eq(time));
        verify(repository).insertQuestionBatch(eq(101L), eq("r1"), eq(21L), eq(9L), eq("object"), eq("file-hash"),
            eq("questions.zip"), eq(3L), eq("application/zip"), eq("{}"), eq(7L), eq(8L), eq(time));
    }

    @Test
    void validCreatesPaperAndTextbookBatchesAndTheirRelatedRecords() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-03T10:00:00+08:00");
        when(repository.completeIdempotency(anyString(), anyString(), anyLong(), eq("{}"))).thenReturn(1);

        service.createPaper(102L, "paper-request", "paper-hash", 21L, 9L, "模拟题集", "PRACTICE", 90,
            "paper/object", "file-hash", "paper.zip", 100L, "application/zip", "{}", 7L, 8L, time, "{}");
        service.createTextbook(103L, 301L, true, "textbook-request", "textbook-hash", 9L, 21L, "教材", "第一版",
            "textbook/object", "file-hash", "book.jsonl", 1024L, "application/jsonl", "{}", 7L, 8L, time, "{}");

        verify(repository).insertPaperBatch(eq(102L), eq("paper-request"), eq(21L), eq(9L), eq("paper/object"),
            eq("file-hash"), eq("paper.zip"), eq(100L), eq("application/zip"), eq("{}"), eq(7L), eq(8L), eq(time));
        verify(repository).insertPaperImport(102L, 9L, "模拟题集", "PRACTICE", 90);
        verify(repository, never()).insertTextbookDocument(anyLong(), anyLong(), any(), anyString(), any(), anyLong(), any(), any());
        verify(repository).insertTextbookBatch(eq(103L), eq("textbook-request"), eq(301L), eq(9L), eq(21L), eq("textbook/object"),
            eq("file-hash"), eq("book.jsonl"), eq(1024L), eq("application/jsonl"), eq("{}"), eq(7L), eq(8L), eq(time));
    }

    @Test
    void schedulesAllImportValidationJobsAndAcceptsSuccessfulConfirmations() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-03T10:00:00+08:00");
        when(repository.start(103L)).thenReturn(1);
        when(repository.start(104L)).thenReturn(1);
        when(repository.acceptConfirmation(106L, 7L, time)).thenReturn(1);
        when(repository.acceptQuestionConfirmation(107L, 7L, time)).thenReturn(1);
        when(repository.acceptPaperConfirmation(108L, 7L, time)).thenReturn(1);
        when(repository.completeIdempotency(anyString(), anyString(), anyLong(), eq("{}"))).thenReturn(1);

        assertThat(service.enqueuePaperValidation(103L)).isTrue();
        assertThat(service.enqueueTextbookValidation(104L)).isTrue();
        service.acceptConfirmation(106L, "knowledge-confirm", "hash", 7L, time, "{}");
        service.acceptQuestionConfirmation(107L, "question-confirm", "hash", 7L, time, "{}");
        service.acceptPaperConfirmation(108L, "paper-confirm", "hash", 7L, time, "{}");

        verify(repository).enqueueJob(anyLong(), eq("paper_precheck"), eq("103"), anyString());
        verify(repository).enqueueJob(anyLong(), eq("document_chunk_precheck"), eq("104"), anyString());
        verify(repository).enqueueJob(anyLong(), eq("knowledge_point_persist"), eq("106"), anyString());
        verify(repository).enqueueJob(anyLong(), eq("question_persist"), eq("107"), anyString());
        verify(repository).enqueueJob(anyLong(), eq("paper_persist"), eq("108"), anyString());
    }

    @Test
    void acceptsTextbookConfirmationOnlyForAVisibleDraftDocument() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-03T10:00:00+08:00");
        CmImportBatch batch = new CmImportBatch(
            109L, "textbook-request", 301L, 21L, null, "book/object", "hash", 1024L,
            "document_chunk", "document_chunk/1.0", "{}", "waiting_confirm", "finalize_counts",
            BigDecimal.ONE, 2, 0, 0, 0, null, OffsetDateTime.now(), null, null, 7L, 8L
        );
        when(repository.selectByIdForUpdate(109L)).thenReturn(batch);
        when(repository.selectTextbookDocumentForUpdate(301L)).thenReturn(
            new org.dromara.certmuse.catalog.domain.TextbookImportDocument(
                301L, 21L, "教材", "第一版", "draft", "0", 7L, 8L));
        when(repository.submitTextbookForAutoPublish(301L, 7L, time)).thenReturn(1);
        when(repository.acceptTextbookConfirmation(109L, 7L, time)).thenReturn(1);
        when(repository.completeIdempotency(anyString(), eq("textbook-request"), eq(109L), eq("{}"))).thenReturn(1);

        service.acceptTextbookConfirmation(109L, "textbook-request", "hash", 7L, time, "{}");

        verify(repository).enqueueJob(anyLong(), eq("document_chunk_persist"), eq("109"), anyString());
        verify(repository).submitTextbookForAutoPublish(301L, 7L, time);
        verify(repository).acceptTextbookConfirmation(109L, 7L, time);
    }

    @Test
    void createsNewTextbookDocumentOnlyWhenConfirmationIsAccepted() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-03T10:00:00+08:00");
        String config = """
            {"mode":"create","title":"待确认教材","edition":"第一版"}
            """;
        CmImportBatch batch = new CmImportBatch(
            109L, "textbook-request", null, 21L, null, "book/object", "hash", 1024L,
            "document_chunk", "document_chunk/1.0", config, "waiting_confirm", "finalize_counts",
            BigDecimal.ONE, 2, 0, 0, 0, null, OffsetDateTime.now(), null, null, 7L, 8L,
            null, null, 9L
        );
        when(repository.selectByIdForUpdate(109L)).thenReturn(batch);
        when(repository.selectTextbookDocumentForUpdate(anyLong())).thenAnswer(invocation ->
            new TextbookImportDocument(
                invocation.getArgument(0), 21L, "待确认教材", "第一版", "draft", "0", 7L, 8L, 9L
            ));
        when(repository.bindTextbookDocument(eq(109L), anyLong())).thenReturn(1);
        when(repository.submitTextbookForAutoPublish(anyLong(), eq(7L), eq(time))).thenReturn(1);
        when(repository.acceptTextbookConfirmation(109L, 7L, time)).thenReturn(1);
        when(repository.completeIdempotency(anyString(), eq("textbook-request"), eq(109L), eq("{}"))).thenReturn(1);

        service.acceptTextbookConfirmation(109L, "textbook-request", "hash", 7L, time, "{}");

        verify(repository).insertTextbookDocument(
            anyLong(), eq(9L), eq(21L), eq("待确认教材"), eq("第一版"), eq(7L), eq(8L), eq(time)
        );
        verify(repository).bindTextbookDocument(eq(109L), anyLong());
    }

    @Test
    void rejectsTextbookConfirmationWhenTheTargetIsNoLongerDraft() {
        OffsetDateTime confirmedTime = OffsetDateTime.parse("2026-08-03T10:00:00+08:00");
        CmImportBatch batch = new CmImportBatch(
            109L, "textbook-request", 301L, 21L, null, "book/object", "hash", 1024L,
            "document_chunk", "document_chunk/1.0", "{}", "waiting_confirm", "finalize_counts",
            BigDecimal.ONE, 2, 0, 0, 0, null, confirmedTime, null, null, 7L, 8L
        );
        when(repository.selectByIdForUpdate(109L)).thenReturn(batch);
        when(repository.selectTextbookDocumentForUpdate(301L)).thenReturn(
            new TextbookImportDocument(301L, 21L, "教材", "第一版", "published", "0", 7L, 8L));

        assertThatThrownBy(() -> service.acceptTextbookConfirmation(
            109L, "textbook-request", "hash", 7L, confirmedTime, "{}"
        )).isInstanceOfSatisfying(ImportException.class, exception ->
            assertThat(exception.errorCode()).isEqualTo("IMPORT_TARGET_NOT_DRAFT"));
        verify(repository, never()).acceptTextbookConfirmation(anyLong(), anyLong(), any());
    }

    @Test
    void rejectsPaperPrecheckWhenTheBatchWasAlreadyClaimed() {
        when(repository.start(110L)).thenReturn(0);

        assertThatThrownBy(() -> service.acceptPaperValidation(110L, "paper-request", "hash", "{}"))
            .isInstanceOf(ImportException.class)
            .satisfies(error -> {
                var exception = (ImportException) error;
                assertThat(exception.code()).isEqualTo(409);
                assertThat(exception.errorCode()).isEqualTo("PAPER_IMPORT_STATE_CONFLICT");
            });

        verify(repository, never()).completeIdempotency(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void replacesDraftTextbookWithValidatedChunksAndKnowledgeRelations() {
        CmImportBatch batch = new CmImportBatch(
            111L, "textbook-request", 301L, 21L, null, "book/object", "hash", 1024L,
            "document_chunk", "document_chunk/1.0",
            "{\"mode\":\"replace_draft\",\"subject_mappings\":[{\"subject_no\":1,\"exam_subject_id\":11}]}",
            "importing", "persist_document_chunks", BigDecimal.ONE, 1, 0, 0, 0, null,
            OffsetDateTime.parse("2026-08-03T10:00:00+08:00"), null, null, 7L, 8L
        );
        String rawRecord = "{\"record\":{\"chunk_no\":1,\"heading\":\"第一章\","
            + "\"heading_path\":[\"第一章\"],\"content\":\"内容\",\"content_hash\":\"chunk-hash\","
            + "\"source_key\":\"book.md\",\"page_start\":3,\"page_end\":4,"
            + "\"source_locator\":{\"source_key\":\"book.md\",\"markdown_line_start\":10,"
            + "\"markdown_line_end\":20},\"knowledge_points\":[{\"subject_no\":1,\"code\":\"1.1\"}]}}";

        when(repository.selectByIdForUpdate(111L)).thenReturn(batch);
        when(repository.selectTextbookDocumentForUpdate(301L)).thenReturn(
            new TextbookImportDocument(301L, 21L, "教材", "第一版", "pending_review", "0", 7L, 8L)
        );
        when(repository.selectPendingTextbookRecords(111L)).thenReturn(List.of(new TextbookImportRecordData(401L, rawRecord)));
        when(repository.selectTextbookKnowledgePointLookups(List.of(11L))).thenReturn(
            List.of(new TextbookKnowledgePointLookup(501L, 21L, 11L, "1.1"))
        );
        when(repository.insertTextbookChunks(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(repository.insertTextbookKnowledgeRelations(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(repository.updateTextbookRecordResults(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(repository.completeTextbookImport(111L, 1)).thenReturn(1);
        when(repository.approveTextbookAfterImport(301L)).thenReturn(1);
        when(repository.publishTextbookAfterImport(301L)).thenReturn(1);

        service.persistTextbook(111L);

        verify(repository).deleteTextbookImages(301L);
        verify(repository).deleteTextbookKnowledgeRelations(301L);
        verify(repository).deleteTextbookChunks(301L);
        ArgumentCaptor<List<TextbookChunkInsert>> chunks = ArgumentCaptor.forClass(List.class);
        verify(repository).insertTextbookChunks(chunks.capture());
        assertThat(chunks.getValue()).singleElement().satisfies(chunk -> {
            assertThat(chunk.documentId()).isEqualTo(301L);
            assertThat(chunk.chunkOrder()).isEqualTo(1);
            assertThat(chunk.heading()).isEqualTo("第一章");
            assertThat(chunk.content()).isEqualTo("内容");
        });
        ArgumentCaptor<List<TextbookChunkKnowledgeInsert>> relations = ArgumentCaptor.forClass(List.class);
        verify(repository).insertTextbookKnowledgeRelations(relations.capture());
        assertThat(relations.getValue()).singleElement().satisfies(relation ->
            assertThat(relation.knowledgePointId()).isEqualTo(501L)
        );
        ArgumentCaptor<List<TextbookRecordResultUpdate>> results = ArgumentCaptor.forClass(List.class);
        verify(repository).updateTextbookRecordResults(results.capture());
        assertThat(results.getValue()).singleElement().extracting(TextbookRecordResultUpdate::id).isEqualTo(401L);
        verify(repository).completeTextbookImport(111L, 1);
        verify(repository).approveTextbookAfterImport(301L);
        verify(repository).publishTextbookAfterImport(301L);
    }

    @Test
    void refusesCreateModePersistenceWhenTheNewDocumentAlreadyHasChunks() {
        CmImportBatch batch = new CmImportBatch(
            112L, "textbook-request", 301L, 21L, null, "book/object", "hash", 1024L,
            "document_chunk", "document_chunk/1.0",
            "{\"mode\":\"create\",\"subject_mappings\":[{\"subject_no\":1,\"exam_subject_id\":11}]}",
            "importing", "persist_document_chunks", BigDecimal.ONE, 1, 0, 0, 0, null,
            OffsetDateTime.parse("2026-08-03T10:00:00+08:00"), null, null, 7L, 8L
        );
        when(repository.selectByIdForUpdate(112L)).thenReturn(batch);
        when(repository.selectTextbookDocumentForUpdate(301L)).thenReturn(
            new TextbookImportDocument(301L, 21L, "教材", "第一版", "pending_review", "0", 7L, 8L));
        when(repository.selectPendingTextbookRecords(112L)).thenReturn(
            List.of(new TextbookImportRecordData(401L, "{\"record\":{\"chunk_no\":1,\"content\":\"内容\",\"knowledge_points\":[]}}")));
        when(repository.countDocumentChunks(301L)).thenReturn(1L);

        assertThatThrownBy(() -> service.persistTextbook(112L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("New textbook document is not empty");
        verify(repository, never()).deleteTextbookChunks(anyLong());
        verify(repository, never()).completeTextbookImport(anyLong(), anyInt());
    }

    @Test
    void invalidFailsWhenCreationCannotCompleteTheIdempotencyRecord() {
        when(repository.completeIdempotency(anyString(), eq("r1"), eq(101L), eq("{}"))).thenReturn(0);

        assertThatThrownBy(() -> service.create(101L, "r1", "hash", 21L, "object", "file-hash", "tree.jsonl", 3L,
            "application/jsonl", "{}", 7L, 8L, OffsetDateTime.now(), "{}"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Idempotency completion failed");
    }

    @Test
    void edgeEnqueuesOnlyWhenTheBatchCanBeClaimedAndSchedulesReferencedImageCleanup() {
        when(repository.start(101L)).thenReturn(0);
        when(repository.start(102L)).thenReturn(1);

        assertThat(service.enqueueValidation(101L)).isFalse();
        assertThat(service.enqueueQuestionValidation(102L)).isTrue();
        verify(repository, never()).enqueueJob(anyLong(), eq("knowledge_point_precheck"), anyString(), anyString());
        verify(repository).enqueueJob(anyLong(), eq("question_precheck"), eq("102"), anyString());

        service.enqueueCleanup("source/a", "hash", "rollback");
        service.enqueueImageCleanup("images/a.png", "hash", "rollback");
        verify(repository).enqueueJob(anyLong(), eq("import_object_cleanup"), anyString(), org.mockito.ArgumentMatchers.contains("\"check_references\":false"));
        verify(repository).enqueueJobAt(anyLong(), eq("import_object_cleanup"), anyString(), org.mockito.ArgumentMatchers.contains("\"check_references\":true"), any());
    }

    private static CmImportBatch batch(String status, int validCount) {
        return new CmImportBatch(
            101L,
            "upload-1",
            21L,
            null,
            "imports/source.jsonl",
            "hash",
            100L,
            "knowledge_point",
            "knowledge_point/1.0",
            "{\"subject_mappings\":[{\"subject_no\":1,\"exam_subject_id\":11}]}",
            status,
            "persist_knowledge_tree",
            BigDecimal.ZERO,
            validCount,
            0,
            0,
            null,
            OffsetDateTime.parse("2026-07-31T09:00:00+08:00"),
            OffsetDateTime.parse("2026-07-31T09:01:00+08:00"),
            null,
            7L,
            8L
        );
    }

    private static CmImportBatch batchWithHashes(String status, int validCount) {
        CmImportBatch batch = batch(status, validCount);
        return new CmImportBatch(
            batch.id(), batch.requestId(), batch.documentId(), batch.syllabusVersionId(), batch.examSubjectId(),
            batch.sourceFilePath(), batch.sourceFileHash(), batch.sourceFileSize(), batch.importType(),
            batch.templateVersion(), batch.parseConfig(), batch.status(), batch.currentStage(), batch.progressPercent(),
            batch.validCount(), batch.warningCount(), batch.failedCount(), batch.generatedCount(), batch.traceId(),
            batch.createTime(), batch.startedTime(), batch.finishedTime(), batch.createBy(), batch.createDept(),
            "base", "resolution"
        );
    }

    private static String row(String number, String parent, int depth, int sortOrder) {
        String parentJson = parent == null ? "null" : "\"" + parent + "\"";
        return "{\"schema_version\":\"knowledge_point_raw/1.0\",\"payload\":{\"subject_no\":1,\"syllabus_number\":\"" + number
            + "\",\"syllabus_title\":\"Title " + number
            + "\",\"parent_syllabus_number\":" + parentJson
            + ",\"tree_depth\":" + depth + ",\"sort_order\":" + sortOrder
            + ",\"description\":null}}";
    }

    private static KnowledgeImportPointRow point(long recordId, String number, String parent, int depth) {
        KnowledgeImportPointRow row = new KnowledgeImportPointRow();
        row.setImportRecordId(recordId); row.setExamSubjectId(11L); row.setSyllabusNumber(number);
        row.setSyllabusTitle("Title " + number); row.setParentSyllabusNumber(parent);
        row.setTreeDepth(depth); row.setSortOrder(1); row.setStatus("0");
        return row;
    }

    private static KnowledgeImportDiffRow addDiff(long id, long recordId) {
        KnowledgeImportDiffRow row = new KnowledgeImportDiffRow();
        row.setId(id); row.setImportRecordId(recordId); row.setExamSubjectId(11L);
        row.setAction("add"); row.setResolutionStatus("manual_confirmed");
        return row;
    }
}
