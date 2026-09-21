package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffRow;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.dromara.certmuse.catalog.domain.TextbookImportDocument;
import org.dromara.certmuse.catalog.domain.TextbookImportRecordData;
import org.dromara.certmuse.catalog.domain.TextbookKnowledgePointLookup;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportPersistenceServiceBranchCoverageTest {

    private static final long BATCH_ID = 701L;
    private static final long DOCUMENT_ID = 801L;
    private static final long SYLLABUS_ID = 21L;
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-08-12T10:00:00+08:00");

    @Mock
    private ImportMapper repository;
    @Mock
    private KnowledgeImportReconciliationService reconciliation;
    @Mock
    private QuestionKnowledgeMaintenanceService questionMaintenance;

    private ImportPersistenceService service;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder().build();
        service = new ImportPersistenceService(
            repository,
            mapper,
            new ImportJsonDocumentFactory(mapper),
            reconciliation,
            questionMaintenance
        );
    }

    @Test
    void textbookConfirmationRejectsMissingAndEveryInvalidBatchState() {
        assertImportError(
            () -> service.acceptTextbookConfirmation(BATCH_ID, "request", "hash", 7L, TIME, "{}"),
            "IMPORT_BATCH_NOT_FOUND"
        );

        for (CmImportBatch batch : List.of(
            textbookBatch(DOCUMENT_ID, "question", "waiting_confirm", 1, 0, "{}", SYLLABUS_ID, 9L),
            textbookBatch(DOCUMENT_ID, "document_chunk", "validating", 1, 0, "{}", SYLLABUS_ID, 9L),
            textbookBatch(DOCUMENT_ID, "document_chunk", "waiting_confirm", 1, 1, "{}", SYLLABUS_ID, 9L),
            textbookBatch(DOCUMENT_ID, "document_chunk", "waiting_confirm", 0, 0, "{}", SYLLABUS_ID, 9L)
        )) {
            when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch);
            assertImportError(
                () -> service.acceptTextbookConfirmation(BATCH_ID, "request", "hash", 7L, TIME, "{}"),
                "IMPORT_BATCH_STATE_INVALID"
            );
        }
    }

    @Test
    void textbookConfirmationDefaultsModeAndValidatesCreateBinding() {
        CmImportBatch alreadyBound = textbookBatch(
            DOCUMENT_ID, "document_chunk", "waiting_confirm", 1, 0, "{}", SYLLABUS_ID, 9L
        );
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(alreadyBound);
        assertImportError(
            () -> service.acceptTextbookConfirmation(BATCH_ID, "request", "hash", 7L, TIME, "{}"),
            "TEXTBOOK_NOT_FOUND"
        );

        CmImportBatch create = textbookBatch(
            null, "document_chunk", "waiting_confirm", 1, 0,
            "{\"mode\":true,\"title\":\"新教材\",\"edition\":null}", SYLLABUS_ID, 9L
        );
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(create);
        when(repository.bindTextbookDocument(eq(BATCH_ID), anyLong())).thenReturn(0);

        assertImportError(
            () -> service.acceptTextbookConfirmation(BATCH_ID, "request", "hash", 7L, TIME, "{}"),
            "IMPORT_BATCH_STATE_INVALID"
        );
        verify(repository).insertTextbookDocument(
            anyLong(), eq(9L), eq(SYLLABUS_ID), eq("新教材"), eq(null), eq(7L), eq(8L), eq(TIME)
        );
    }

    @Test
    void textbookConfirmationValidatesReplacementTargetAndCompletionRace() {
        CmImportBatch missingTarget = textbookBatch(
            null, "document_chunk", "waiting_confirm", 1, 0,
            "{\"mode\":\"replace_draft\"}", SYLLABUS_ID, 9L
        );
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(missingTarget);
        assertImportError(
            () -> service.acceptTextbookConfirmation(BATCH_ID, "request", "hash", 7L, TIME, "{}"),
            "IMPORT_BATCH_STATE_INVALID"
        );

        CmImportBatch replacement = textbookBatch(
            DOCUMENT_ID, "document_chunk", "waiting_confirm", 1, 0,
            "{\"mode\":\"replace_draft\"}", SYLLABUS_ID, 9L
        );
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(replacement);
        when(repository.selectTextbookDocumentForUpdate(DOCUMENT_ID)).thenReturn(null);
        assertImportError(
            () -> service.acceptTextbookConfirmation(BATCH_ID, "request", "hash", 7L, TIME, "{}"),
            "TEXTBOOK_NOT_FOUND"
        );

        when(repository.selectTextbookDocumentForUpdate(DOCUMENT_ID)).thenReturn(document("draft", "1", SYLLABUS_ID, 9L));
        assertImportError(
            () -> service.acceptTextbookConfirmation(BATCH_ID, "request", "hash", 7L, TIME, "{}"),
            "IMPORT_TARGET_NOT_DRAFT"
        );

        when(repository.selectTextbookDocumentForUpdate(DOCUMENT_ID)).thenReturn(document("draft", "0", SYLLABUS_ID, 9L));
        when(repository.submitTextbookForAutoPublish(DOCUMENT_ID, 7L, TIME)).thenReturn(1);
        when(repository.acceptTextbookConfirmation(BATCH_ID, 7L, TIME)).thenReturn(0);
        assertImportError(
            () -> service.acceptTextbookConfirmation(BATCH_ID, "request", "hash", 7L, TIME, "{}"),
            "IMPORT_BATCH_STATE_INVALID"
        );

        when(repository.acceptTextbookConfirmation(BATCH_ID, 7L, TIME)).thenReturn(1);
        when(repository.completeIdempotency(anyString(), eq("request"), eq(BATCH_ID), eq("{}"))).thenReturn(0);
        assertThatThrownBy(() -> service.acceptTextbookConfirmation(BATCH_ID, "request", "hash", 7L, TIME, "{}"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Textbook confirmation idempotency completion failed");
        verify(repository).enqueueJob(anyLong(), eq("document_chunk_persist"), eq(Long.toString(BATCH_ID)), anyString());
    }

    @Test
    void textbookPersistenceRejectsMissingWrongStateAndMissingDocument() {
        assertThatThrownBy(() -> service.persistTextbook(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Textbook import batch does not exist");

        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(
            textbookBatch(DOCUMENT_ID, "question", "importing", 1, 0, "{}", SYLLABUS_ID, 9L),
            textbookBatch(DOCUMENT_ID, "document_chunk", "completed", 1, 0, "{}", SYLLABUS_ID, 9L),
            textbookBatch(null, "document_chunk", "importing", 1, 0, "{}", SYLLABUS_ID, 9L)
        );
        service.persistTextbook(BATCH_ID);
        service.persistTextbook(BATCH_ID);
        assertThatThrownBy(() -> service.persistTextbook(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Textbook import batch has no document");
        verify(repository, never()).selectPendingTextbookRecords(anyLong());
    }

    @Test
    void textbookPersistenceValidatesDocumentScopeAndOptionalSyllabusBinding() {
        CmImportBatch batch = textbookBatch(
            DOCUMENT_ID, "document_chunk", "importing", 1, 0,
            "{\"mode\":\"create\",\"subject_mappings\":[]}", SYLLABUS_ID, 9L
        );
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch);

        when(repository.selectTextbookDocumentForUpdate(DOCUMENT_ID)).thenReturn(null);
        assertStateFailure("Textbook document does not exist");

        when(repository.selectTextbookDocumentForUpdate(DOCUMENT_ID)).thenReturn(document("draft", "0", SYLLABUS_ID, 9L));
        assertStateFailure("Textbook document is no longer pending review");

        when(repository.selectTextbookDocumentForUpdate(DOCUMENT_ID)).thenReturn(document("pending_review", "0", SYLLABUS_ID, 10L));
        assertStateFailure("Textbook import certification changed before persistence");

        when(repository.selectTextbookDocumentForUpdate(DOCUMENT_ID)).thenReturn(document("pending_review", "0", 22L, 9L));
        assertStateFailure("Textbook import syllabus changed before persistence");

        when(repository.selectTextbookDocumentForUpdate(DOCUMENT_ID)).thenReturn(document("pending_review", "0", null, 9L));
        when(repository.bindTextbookSyllabus(DOCUMENT_ID, SYLLABUS_ID, 7L)).thenReturn(0);
        assertStateFailure("Textbook syllabus binding failed");

        when(repository.bindTextbookSyllabus(DOCUMENT_ID, SYLLABUS_ID, 7L)).thenReturn(1);
        when(repository.selectPendingTextbookRecords(BATCH_ID)).thenReturn(List.of());
        assertStateFailure("Validated textbook record count changed before persistence");
    }

    @Test
    void textbookPersistenceValidatesRecordCountModeAndKnowledgeResolution() {
        CmImportBatch batch = textbookBatch(
            DOCUMENT_ID, "document_chunk", "importing", 2, 0,
            "{\"mode\":\"unsupported\",\"subject_mappings\":[]}", SYLLABUS_ID, 9L
        );
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch);
        when(repository.selectTextbookDocumentForUpdate(DOCUMENT_ID)).thenReturn(document("pending_review", "0", SYLLABUS_ID, 9L));
        when(repository.selectPendingTextbookRecords(BATCH_ID)).thenReturn(List.of(textbookRecord(1L, "[]")));
        assertStateFailure("Validated textbook record count changed before persistence");

        CmImportBatch unsupported = textbookBatch(
            DOCUMENT_ID, "document_chunk", "importing", 1, 0,
            "{\"mode\":\"unsupported\",\"subject_mappings\":[]}", SYLLABUS_ID, 9L
        );
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(unsupported);
        assertStateFailure("Unsupported textbook import mode");

        CmImportBatch unresolved = textbookBatch(
            DOCUMENT_ID, "document_chunk", "importing", 1, 0,
            "{\"mode\":\"create\",\"subject_mappings\":[{\"subject_no\":1,\"exam_subject_id\":11}]}",
            SYLLABUS_ID, 9L
        );
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(unresolved);
        when(repository.countDocumentChunks(DOCUMENT_ID)).thenReturn(0L);
        when(repository.selectPendingTextbookRecords(BATCH_ID)).thenReturn(List.of(textbookRecord(
            2L, "[{\"subject_no\":1,\"code\":\"1.1\"}]"
        )));
        when(repository.selectTextbookKnowledgePointLookups(List.of(11L))).thenReturn(
            List.of(new TextbookKnowledgePointLookup(501L, 999L, 11L, "1.1"))
        );
        assertStateFailure("Validated textbook knowledge point cannot be resolved");
    }

    @Test
    void textbookPersistenceChecksEveryDatabaseWriteBoundaryAndCompletion() {
        CmImportBatch batch = textbookBatch(
            DOCUMENT_ID, "document_chunk", "importing", 1, 0,
            "{\"mode\":\"create\",\"subject_mappings\":[{\"subject_no\":1,\"exam_subject_id\":11}]}",
            SYLLABUS_ID, 9L
        );
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch);
        when(repository.selectTextbookDocumentForUpdate(DOCUMENT_ID)).thenReturn(document("pending_review", "0", SYLLABUS_ID, 9L));
        when(repository.selectPendingTextbookRecords(BATCH_ID)).thenReturn(List.of(textbookRecord(
            1L, "[{\"subject_no\":1,\"code\":\"1.1\"}]"
        )));
        when(repository.selectTextbookKnowledgePointLookups(List.of(11L))).thenReturn(
            List.of(new TextbookKnowledgePointLookup(501L, SYLLABUS_ID, 11L, "1.1"))
        );
        when(repository.countDocumentChunks(DOCUMENT_ID)).thenReturn(0L);

        when(repository.insertTextbookChunks(anyList())).thenReturn(0);
        assertStateFailure("Textbook chunk insert was incomplete");

        when(repository.insertTextbookChunks(anyList())).thenReturn(1);
        when(repository.insertTextbookKnowledgeRelations(anyList())).thenReturn(0);
        assertStateFailure("Textbook knowledge insert was incomplete");

        when(repository.insertTextbookKnowledgeRelations(anyList())).thenReturn(1);
        when(repository.updateTextbookRecordResults(anyList())).thenReturn(0);
        assertStateFailure("Textbook import record update was incomplete");

        when(repository.updateTextbookRecordResults(anyList())).thenReturn(1);
        when(repository.completeTextbookImport(BATCH_ID, 1)).thenReturn(0);
        assertStateFailure("Textbook import batch completion failed");

        when(repository.completeTextbookImport(BATCH_ID, 1)).thenReturn(1);
        when(repository.approveTextbookAfterImport(DOCUMENT_ID)).thenReturn(1);
        when(repository.publishTextbookAfterImport(DOCUMENT_ID)).thenReturn(1);
        service.persistTextbook(BATCH_ID);
        verify(repository, atLeastOnce()).completeTextbookImport(BATCH_ID, 1);
    }

    @Test
    void textbookPersistenceAcceptsUnscopedRecordsAndNormalizesSparseMetadata() {
        CmImportBatch batch = textbookBatch(
            DOCUMENT_ID, "document_chunk", "importing", 1, 0,
            "{\"mode\":\"create\",\"subject_mappings\":[]}", null, 9L
        );
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch);
        when(repository.selectTextbookDocumentForUpdate(DOCUMENT_ID)).thenReturn(document("pending_review", "0", null, 9L));
        when(repository.selectPendingTextbookRecords(BATCH_ID)).thenReturn(List.of(new TextbookImportRecordData(
            1L,
            "{\"record\":{\"chunk_no\":1,\"heading\":null,\"heading_path\":[],\"content\":\"正文\","
                + "\"source_key\":\"fallback.md\",\"content_hash\":\"hash\",\"source_locator\":{},"
                + "\"page_start\":3,\"page_end\":null,\"knowledge_points\":[]}}"
        )));
        when(repository.countDocumentChunks(DOCUMENT_ID)).thenReturn(0L);
        when(repository.insertTextbookChunks(anyList())).thenReturn(1);
        when(repository.updateTextbookRecordResults(anyList())).thenReturn(1);
        when(repository.completeTextbookImport(BATCH_ID, 1)).thenReturn(1);
        when(repository.approveTextbookAfterImport(DOCUMENT_ID)).thenReturn(1);
        when(repository.publishTextbookAfterImport(DOCUMENT_ID)).thenReturn(1);

        service.persistTextbook(BATCH_ID);

        verify(repository, never()).selectTextbookKnowledgePointLookups(anyList());
        verify(repository, never()).insertTextbookKnowledgeRelations(anyList());
        verify(repository).completeTextbookImport(BATCH_ID, 1);
        verify(repository).approveTextbookAfterImport(DOCUMENT_ID);
        verify(repository).publishTextbookAfterImport(DOCUMENT_ID);
    }

    @Test
    void knowledgePersistenceRejectsMissingStateScopeAndTarget() {
        assertStateKnowledgeFailure("Import batch does not exist");

        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(knowledgeBatch("completed", 1, SYLLABUS_ID, "base", "resolution"));
        service.persistKnowledgeTree(BATCH_ID);
        verify(repository, never()).lockSyllabusVersion(anyLong());

        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(knowledgeBatch("importing", 1, null, "base", "resolution"));
        assertStateKnowledgeFailure("Import batch has no syllabus version");

        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(knowledgeBatch("importing", 1, SYLLABUS_ID, "base", "resolution"));
        when(repository.lockSyllabusVersion(SYLLABUS_ID)).thenReturn(null);
        assertStateKnowledgeFailure("Target syllabus does not exist");
    }

    @Test
    void knowledgePersistenceValidatesBaselineAndEveryResolutionGate() {
        CmImportBatch noBaseline = knowledgeBatch("importing", 1, SYLLABUS_ID, null, "resolution");
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(noBaseline);
        when(repository.lockSyllabusVersion(SYLLABUS_ID)).thenReturn(SYLLABUS_ID);
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(reconciliation.baselineHash(List.of())).thenReturn("base");
        assertImportKnowledgeError("KNOWLEDGE_TREE_BASELINE_CHANGED");

        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(knowledgeBatch("importing", 1, SYLLABUS_ID, "base", null));
        when(repository.countKnowledgeImportDiffs(BATCH_ID, null, null, null, false, null, null)).thenReturn(1L);
        assertImportKnowledgeError("KNOWLEDGE_DIFF_UNRESOLVED");

        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(knowledgeBatch("importing", 1, SYLLABUS_ID, "base", "resolution"));
        when(reconciliation.currentResolutionHash(BATCH_ID)).thenReturn("resolution");
        when(repository.countPendingKnowledgeImportDiffs(BATCH_ID)).thenReturn(1L);
        assertImportKnowledgeError("KNOWLEDGE_DIFF_UNRESOLVED");

        when(repository.countPendingKnowledgeImportDiffs(BATCH_ID)).thenReturn(0L);
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of());
        assertStateKnowledgeFailure("Validated record count changed before persistence");
    }

    @Test
    void knowledgePersistenceRequiresOneResolvedDecisionPerIncomingRecord() {
        stubResolvedKnowledgeBatch(2);
        KnowledgeImportPointRow root = point(1L, "1.1", null);
        KnowledgeImportPointRow child = point(2L, "1.1.1", "1.1");
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of(root, child));
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(List.of(addDecision(11L, 1L)));

        assertImportKnowledgeError("KNOWLEDGE_DIFF_UNRESOLVED");

        KnowledgeImportDiffRow withoutRecord = addDecision(12L, 2L);
        withoutRecord.setImportRecordId(null);
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(List.of(addDecision(11L, 1L), withoutRecord));
        assertImportKnowledgeError("KNOWLEDGE_DIFF_UNRESOLVED");
    }

    @Test
    void rejectedAddCascadesToDescendantsAndPreservesRejectedOldPoints() {
        stubResolvedKnowledgeBatch(3);
        KnowledgeImportPointRow current = point(null, "9.1", null);
        current.setKnowledgePointId(900L);
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(List.of(current));
        when(reconciliation.baselineHash(List.of(current))).thenReturn("base");

        KnowledgeImportPointRow root = point(1L, "1.1", null);
        KnowledgeImportPointRow child = point(2L, "1.1.1", "1.1");
        KnowledgeImportPointRow grandchild = point(3L, "1.1.1.1", "1.1.1");
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of(root, child, grandchild));
        KnowledgeImportDiffRow rejectAdd = addDecision(11L, 1L);
        rejectAdd.setResolutionDecision("reject");
        KnowledgeImportDiffRow childDecision = addDecision(12L, 2L);
        KnowledgeImportDiffRow grandchildDecision = addDecision(13L, 3L);
        KnowledgeImportDiffRow preserveOld = addDecision(14L, 99L);
        preserveOld.setAction("delete");
        preserveOld.setImportRecordId(null);
        preserveOld.setOldKnowledgePointId(900L);
        preserveOld.setResolutionDecision("reject");
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(
            List.of(rejectAdd, childDecision, grandchildDecision, preserveOld)
        );
        when(repository.updateKnowledgePoints(anyList())).thenReturn(1);
        when(repository.completeImport(BATCH_ID, 3)).thenReturn(1);

        service.persistKnowledgeTree(BATCH_ID);

        verify(repository, never()).insertKnowledgePoints(anyList());
        verify(repository).updateKnowledgePoints(anyList());
        verify(questionMaintenance).maintainAfterKnowledgeDeletion(eq(Set.of()), eq(BATCH_ID), eq(7L));
    }

    @Test
    void knowledgePersistenceRejectsPendingMappingDuplicateAndMissingParent() {
        stubResolvedKnowledgeBatch(1);
        KnowledgeImportPointRow root = point(1L, "1.1", null);
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of(root));
        KnowledgeImportDiffRow pending = addDecision(11L, 1L);
        pending.setResolutionStatus("pending");
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(List.of(pending));
        assertImportKnowledgeError("KNOWLEDGE_DIFF_UNRESOLVED");

        KnowledgeImportDiffRow updateWithoutTarget = addDecision(11L, 1L);
        updateWithoutTarget.setAction("update");
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(List.of(updateWithoutTarget));
        assertImportKnowledgeError("KNOWLEDGE_DIFF_MAPPING_CONFLICT");

        KnowledgeImportPointRow duplicate = point(2L, "1.1", null);
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(knowledgeBatch("importing", 2, SYLLABUS_ID, "base", "resolution"));
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of(root, duplicate));
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(List.of(addDecision(11L, 1L), addDecision(12L, 2L)));
        assertStateKnowledgeFailure("Duplicate validated syllabus number");

        KnowledgeImportPointRow orphan = point(1L, "1.1.1", "1.1");
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(knowledgeBatch("importing", 1, SYLLABUS_ID, "base", "resolution"));
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of(orphan));
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(List.of(addDecision(11L, 1L)));
        assertStateKnowledgeFailure("Validated parent knowledge point is missing");
    }

    @Test
    void knowledgePersistenceRejectsDoubleInheritanceAndDeleteConflict() {
        stubResolvedKnowledgeBatch(2);
        KnowledgeImportPointRow first = point(1L, "1.1", null);
        KnowledgeImportPointRow second = point(2L, "1.2", null);
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of(first, second));
        KnowledgeImportDiffRow firstUpdate = updateDecision(11L, 1L, 900L);
        KnowledgeImportDiffRow secondUpdate = updateDecision(12L, 2L, 900L);
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(List.of(firstUpdate, secondUpdate));
        assertImportKnowledgeError("KNOWLEDGE_DIFF_MAPPING_CONFLICT");

        KnowledgeImportPointRow old = point(null, "9.1", null);
        old.setKnowledgePointId(900L);
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(List.of(old));
        when(reconciliation.baselineHash(List.of(old))).thenReturn("base");
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(knowledgeBatch("importing", 1, SYLLABUS_ID, "base", "resolution"));
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of(first));
        KnowledgeImportDiffRow inherit = updateDecision(11L, 1L, 900L);
        KnowledgeImportDiffRow delete = addDecision(12L, 99L);
        delete.setImportRecordId(null);
        delete.setAction("delete");
        delete.setResolutionDecision("approve");
        delete.setOldKnowledgePointId(900L);
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(List.of(inherit, delete));
        assertImportKnowledgeError("KNOWLEDGE_DIFF_MAPPING_CONFLICT");
    }

    @Test
    void knowledgePersistenceChecksBulkWritesAndCompletion() {
        stubResolvedKnowledgeBatch(1);
        KnowledgeImportPointRow point = point(1L, "1.1", null);
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of(point));
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(List.of(addDecision(11L, 1L)));
        when(repository.insertKnowledgePoints(anyList())).thenReturn(0);
        assertStateKnowledgeFailure("Knowledge point bulk insert was incomplete");

        KnowledgeImportPointRow old = point(null, "9.1", null);
        old.setKnowledgePointId(900L);
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(List.of(old));
        when(reconciliation.baselineHash(List.of(old))).thenReturn("base");
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of(point));
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(List.of(updateDecision(11L, 1L, 900L)));
        when(repository.updateKnowledgePoints(anyList())).thenReturn(0);
        assertStateKnowledgeFailure("Knowledge point bulk update was incomplete");

        when(repository.updateKnowledgePoints(anyList())).thenReturn(1);
        when(repository.completeImport(BATCH_ID, 1)).thenReturn(0);
        assertStateKnowledgeFailure("Import batch completion failed");

        when(repository.completeImport(BATCH_ID, 1)).thenReturn(1);
        service.persistKnowledgeTree(BATCH_ID);
        verify(repository, atLeastOnce()).completeImport(BATCH_ID, 1);
    }

    private void stubResolvedKnowledgeBatch(int validCount) {
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(
            knowledgeBatch("importing", validCount, SYLLABUS_ID, "base", "resolution")
        );
        when(repository.lockSyllabusVersion(SYLLABUS_ID)).thenReturn(SYLLABUS_ID);
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(reconciliation.baselineHash(List.of())).thenReturn("base");
        when(repository.countKnowledgeImportDiffs(BATCH_ID, null, null, null, false, null, null)).thenReturn(1L);
        when(reconciliation.currentResolutionHash(BATCH_ID)).thenReturn("resolution");
        org.mockito.Mockito.lenient().when(repository.countPendingKnowledgeImportDiffs(BATCH_ID)).thenReturn(0L);
    }

    private void assertStateFailure(String message) {
        assertThatThrownBy(() -> service.persistTextbook(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(message);
    }

    private void assertStateKnowledgeFailure(String message) {
        assertThatThrownBy(() -> service.persistKnowledgeTree(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(message);
    }

    private void assertImportKnowledgeError(String code) {
        assertImportError(() -> service.persistKnowledgeTree(BATCH_ID), code);
    }

    private static void assertImportError(ThrowingCall call, String code) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(ImportException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo(code)
            );
    }

    private static CmImportBatch textbookBatch(
        Long documentId,
        String importType,
        String status,
        int validCount,
        int failedCount,
        String config,
        Long syllabusId,
        Long certificationId
    ) {
        return new CmImportBatch(
            BATCH_ID, "request", documentId, syllabusId, null, "book/object", "hash", 100L,
            importType, "document_chunk/1.0", config, status, "stage", BigDecimal.ONE,
            validCount, 0, failedCount, 0, null, TIME, TIME, null, 7L, 8L, null, null, certificationId
        );
    }

    private static CmImportBatch knowledgeBatch(
        String status,
        int validCount,
        Long syllabusId,
        String baseline,
        String resolution
    ) {
        return new CmImportBatch(
            BATCH_ID, "request", null, syllabusId, null, "tree/object", "hash", 100L,
            "knowledge_point", "knowledge_point/1.0", "{}", status, "stage", BigDecimal.ONE,
            validCount, 0, 0, 0, null, TIME, TIME, null, 7L, 8L, baseline, resolution, null
        );
    }

    private static TextbookImportDocument document(String status, String delFlag, Long syllabusId, long certificationId) {
        return new TextbookImportDocument(
            DOCUMENT_ID, syllabusId, "教材", "第一版", status, delFlag, 7L, 8L, certificationId
        );
    }

    private static TextbookImportRecordData textbookRecord(long id, String knowledgePoints) {
        return new TextbookImportRecordData(
            id,
            "{\"record\":{\"chunk_no\":1,\"heading\":\"章节\",\"heading_path\":[\"章\"],"
                + "\"content\":\"正文\",\"source_key\":\"book.md\",\"content_hash\":\"hash\","
                + "\"source_locator\":{\"source_key\":\"book.md\",\"markdown_line_start\":1,"
                + "\"markdown_line_end\":2,\"source_page_start\":3,\"source_page_end\":4,"
                + "\"printed_page_start\":5,\"printed_page_end\":6},\"knowledge_points\":"
                + knowledgePoints + "}}"
        );
    }

    private static KnowledgeImportPointRow point(Long recordId, String number, String parent) {
        KnowledgeImportPointRow point = new KnowledgeImportPointRow();
        point.setImportRecordId(recordId);
        point.setExamSubjectId(11L);
        point.setSyllabusNumber(number);
        point.setSyllabusTitle("Title " + number);
        point.setParentSyllabusNumber(parent);
        point.setTreeDepth(parent == null ? 1 : 2);
        point.setSortOrder(1);
        point.setStatus("0");
        return point;
    }

    private static KnowledgeImportDiffRow addDecision(long id, long recordId) {
        KnowledgeImportDiffRow decision = new KnowledgeImportDiffRow();
        decision.setId(id);
        decision.setImportRecordId(recordId);
        decision.setAction("add");
        decision.setResolutionStatus("manual_confirmed");
        decision.setResolutionDecision("approve");
        return decision;
    }

    private static KnowledgeImportDiffRow updateDecision(long id, long recordId, long oldId) {
        KnowledgeImportDiffRow decision = addDecision(id, recordId);
        decision.setAction("update");
        decision.setOldKnowledgePointId(oldId);
        decision.setConfirmedKnowledgePointId(oldId);
        return decision;
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
