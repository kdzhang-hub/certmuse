package org.dromara.certmuse.catalog.service.impl;

import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffCounts;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffRow;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.dromara.certmuse.catalog.domain.PaperImportRow;
import org.dromara.certmuse.catalog.domain.bo.ImportCreateBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffBatchResolutionBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffQueryBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffResolutionBo;
import org.dromara.certmuse.catalog.domain.bo.PaperImportCreateBo;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.catalog.support.ImportJsonDocumentFactory;
import org.dromara.certmuse.catalog.support.ImportProtocol;
import org.dromara.certmuse.catalog.support.ImportStorage;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportServiceContractBranchTest {

    @Mock
    private ImportMapper repository;
    @Mock
    private ImportStorage storage;
    @Mock
    private ImportPersistenceService persistence;
    @Mock
    private KnowledgeImportReconciliationService reconciliation;

    private ImportServiceImpl service;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder().build();
        service = new ImportServiceImpl(
            repository, storage, persistence, mapper, new ImportJsonDocumentFactory(mapper), reconciliation
        );
    }

    @Test
    void questionHeaderRejectsEveryInvalidPublicContractBeforeStorage() {
        ImportCreateBo wrongType = questionForm(file("questions.zip", "application/zip", new byte[]{1}));
        wrongType.setImportType("paper");
        ImportCreateBo wrongTemplate = questionForm(file("questions.zip", "application/zip", new byte[]{1}));
        wrongTemplate.setTemplateVersion("question-zip/2.0");
        ImportCreateBo clientSubject = questionForm(file("questions.zip", "application/zip", new byte[]{1}));
        clientSubject.setExamSubjectId("11");

        for (Runnable invocation : List.<Runnable>of(
            () -> service.createQuestion(null, "request"),
            () -> service.createQuestion(wrongType, "request"),
            () -> service.createQuestion(wrongTemplate, "request"),
            () -> service.createQuestion(clientSubject, "request"),
            () -> service.createQuestion(questionForm(null), "request"),
            () -> service.createQuestion(questionForm(file("questions.zip", "application/zip", new byte[0])), "request"),
            () -> service.createQuestion(questionForm(file(null, "application/zip", new byte[]{1})), "request"),
            () -> service.createQuestion(questionForm(file("questions.txt", "application/zip", new byte[]{1})), "request")
        )) {
            assertThatThrownBy(invocation::run).isInstanceOf(ImportException.class);
        }
        verify(storage, never()).upload(anyString(), any());
    }

    @Test
    void paperHeaderRejectsEveryInvalidPublicContractBeforeStorage() {
        PaperImportCreateBo valid = paperForm();
        PaperImportCreateBo wrongType = paperForm();
        wrongType.setCollectionType("QUIZ");
        PaperImportCreateBo missingName = paperForm();
        missingName.setCollectionName(null);
        PaperImportCreateBo blankName = paperForm();
        blankName.setCollectionName("  ");
        PaperImportCreateBo longName = paperForm();
        longName.setCollectionName("x".repeat(201));
        PaperImportCreateBo missingDuration = paperForm();
        missingDuration.setDurationMinutes(null);
        PaperImportCreateBo lowDuration = paperForm();
        lowDuration.setDurationMinutes(0);
        PaperImportCreateBo highDuration = paperForm();
        highDuration.setDurationMinutes(1441);

        for (Runnable invocation : List.<Runnable>of(
            () -> service.createPaper(null, "request"),
            () -> service.createPaper(paperWithFile(null), "request"),
            () -> service.createPaper(paperWithFile(file("paper.zip", "application/zip", new byte[0])), "request"),
            () -> service.createPaper(paperWithFile(file(null, "application/zip", new byte[]{1})), "request"),
            () -> service.createPaper(paperWithFile(file("paper.txt", "application/zip", new byte[]{1})), "request"),
            () -> service.createPaper(wrongType, "request"),
            () -> service.createPaper(missingName, "request"),
            () -> service.createPaper(blankName, "request"),
            () -> service.createPaper(longName, "request"),
            () -> service.createPaper(missingDuration, "request"),
            () -> service.createPaper(lowDuration, "request"),
            () -> service.createPaper(highDuration, "request")
        )) {
            assertThatThrownBy(invocation::run).isInstanceOf(ImportException.class);
        }
        assertThat(valid.getCollectionType()).isEqualTo("PRACTICE");
        verify(storage, never()).upload(anyString(), any());
    }

    @Test
    void textbookHeaderAndCreateModeRejectInvalidContractCombinations() {
        String requestId = UUID.randomUUID().toString();
        ImportCreateBo wrongType = textbookForm();
        wrongType.setImportType("question");
        ImportCreateBo wrongTemplate = textbookForm();
        wrongTemplate.setTemplateVersion("document_chunk/2.0");
        ImportCreateBo wrongMode = textbookForm();
        wrongMode.setMode("append");
        ImportCreateBo wrongExtension = textbookForm();
        wrongExtension.setFile(largeFile("book.txt", "application/jsonl"));
        ImportCreateBo tooSmall = textbookForm();
        tooSmall.setFile(file("book.jsonl", "application/jsonl", new byte[]{1}));
        ImportCreateBo missingMime = textbookForm();
        missingMime.setFile(largeFile("book.jsonl", null));
        ImportCreateBo createWithDocument = textbookForm();
        createWithDocument.setDocumentId("201");
        ImportCreateBo missingTitle = textbookForm();
        missingTitle.setTitle("  ");
        ImportCreateBo longTitle = textbookForm();
        longTitle.setTitle("x".repeat(501));
        ImportCreateBo longEdition = textbookForm();
        longEdition.setEdition("x".repeat(101));

        for (Runnable invocation : List.<Runnable>of(
            () -> service.createTextbook(null, requestId),
            () -> service.createTextbook(wrongType, requestId),
            () -> service.createTextbook(wrongTemplate, requestId),
            () -> service.createTextbook(wrongMode, requestId),
            () -> service.createTextbook(wrongExtension, requestId),
            () -> service.createTextbook(tooSmall, requestId),
            () -> service.createTextbook(missingMime, requestId),
            () -> service.createTextbook(createWithDocument, requestId),
            () -> service.createTextbook(missingTitle, requestId),
            () -> service.createTextbook(longTitle, requestId),
            () -> service.createTextbook(longEdition, requestId)
        )) {
            assertThatThrownBy(invocation::run).isInstanceOf(ImportException.class);
        }
        verify(storage, never()).upload(anyString(), any());
    }

    @Test
    void textbookReplaceRejectsMissingNonDraftAndMismatchedTargets() {
        String requestId = UUID.randomUUID().toString();
        ImportCreateBo missingId = textbookForm();
        missingId.setMode("replace_draft");
        ImportCreateBo missingTarget = replacementForm();
        ImportCreateBo published = replacementForm();
        ImportCreateBo certificationMismatch = replacementForm();
        certificationMismatch.setCertificationId("10");
        ImportCreateBo missingSyllabus = replacementForm();
        missingSyllabus.setSyllabusVersionId("22");
        ImportCreateBo syllabusCertificationMismatch = replacementForm();
        syllabusCertificationMismatch.setSyllabusVersionId("23");
        ImportCreateBo targetSyllabusMismatch = replacementForm();
        targetSyllabusMismatch.setSyllabusVersionId("24");

        assertThatThrownBy(() -> service.createTextbook(missingId, requestId)).isInstanceOf(ImportException.class);
        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            when(repository.selectTextbookDocument(201L, 7L))
                .thenReturn(null)
                .thenReturn(document("published", 21L, 9L))
                .thenReturn(document("draft", 21L, 9L))
                .thenReturn(document("draft", 21L, 9L))
                .thenReturn(document("draft", 21L, 9L))
                .thenReturn(document("draft", 21L, 9L));
            when(repository.selectSyllabusCertification(22L)).thenReturn(null);
            when(repository.selectSyllabusCertification(23L)).thenReturn(10L);
            when(repository.selectSyllabusCertification(24L)).thenReturn(9L);

            for (ImportCreateBo form : List.of(
                missingTarget, published, certificationMismatch, missingSyllabus,
                syllabusCertificationMismatch, targetSyllabusMismatch
            )) {
                assertThatThrownBy(() -> service.createTextbook(form, requestId)).isInstanceOf(ImportException.class);
            }
        }
        verify(storage, never()).upload(anyString(), any());
    }

    @Test
    void contextOptionsRejectMissingEntitiesAndMismatchedSelections() {
        when(repository.selectSyllabusCertification(21L)).thenReturn(null);
        assertThatThrownBy(() -> service.contextOptions("knowledge_point", "21", null))
            .isInstanceOf(ImportException.class);
        when(repository.selectCertificationName(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.contextOptions("knowledge_point", null, "9"))
            .isInstanceOf(ImportException.class);

        when(repository.selectSyllabusOptions()).thenReturn(List.of());
        when(repository.selectCertificationOptions()).thenReturn(List.of());
        when(repository.selectSyllabusCertification(22L)).thenReturn(null);
        assertThatThrownBy(() -> service.textbookContextOptions("22", null)).isInstanceOf(ImportException.class);
        when(repository.selectSyllabusCertification(23L)).thenReturn(9L);
        assertThatThrownBy(() -> service.textbookContextOptions("23", "10")).isInstanceOf(ImportException.class);
        when(repository.selectCertificationName(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.textbookContextOptions(null, "9")).isInstanceOf(ImportException.class);
    }

    @Test
    void confirmRejectsEveryKnowledgeAndTextbookStatePrecondition() {
        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            CmImportBatch failed = batch("knowledge_point", "waiting_confirm", 1, 0, 1, null, "resolution", 21L);
            CmImportBatch noBaseline = batch("knowledge_point", "waiting_confirm", 1, 0, 0, null, "resolution", 21L);
            CmImportBatch noResolution = batch("knowledge_point", "waiting_confirm", 1, 0, 0, "baseline", null, 21L);
            CmImportBatch pending = batch("knowledge_point", "waiting_confirm", 1, 0, 0, "baseline", "resolution", 21L);
            when(repository.selectVisible(eq(101L), eq(7L))).thenReturn(failed, noBaseline, noResolution, pending);
            when(repository.countPendingKnowledgeImportDiffs(101L)).thenReturn(1L);
            for (int index = 0; index < 4; index++) {
                String requestId = "confirm-" + index;
                assertThatThrownBy(() -> service.confirm("101", requestId))
                    .isInstanceOf(ImportException.class);
            }

            CmImportBatch notWaiting = batch("question", "uploaded", 1, 0, 0, null, null, 21L);
            CmImportBatch noValid = batch("question", "waiting_confirm", 0, 0, 0, null, null, 21L);
            CmImportBatch textbookFailures = batch("document_chunk", "waiting_confirm", 1, 0, 1, null, null, 21L);
            when(repository.selectVisible(eq(102L), eq(7L))).thenReturn(notWaiting);
            when(repository.selectVisible(eq(103L), eq(7L))).thenReturn(noValid);
            when(repository.selectVisible(eq(104L), eq(7L))).thenReturn(textbookFailures);
            assertThatThrownBy(() -> service.confirm("102", "question-confirm")).isInstanceOf(ImportException.class);
            assertThatThrownBy(() -> service.confirm("103", "question-confirm")).isInstanceOf(ImportException.class);
            assertThatThrownBy(() -> service.confirm("104", UUID.randomUUID().toString())).isInstanceOf(ImportException.class);
        }
    }

    @Test
    void paperOperationsRejectWrongTypesStatesAndPrecheckCounts() {
        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            CmImportBatch question = batch("question", "uploaded", 1, 0, 0, null, null, 21L);
            CmImportBatch paperNotUploaded = batch("paper", "validating", 1, 0, 0, null, null, 21L);
            when(repository.selectVisible(101L, 7L)).thenReturn(question, paperNotUploaded, question);
            assertThatThrownBy(() -> service.validatePaper("101", "validate")).isInstanceOf(ImportException.class);
            assertThatThrownBy(() -> service.validatePaper("101", "validate")).isInstanceOf(ImportException.class);
            assertThatThrownBy(() -> service.confirmPaper("101", "confirm")).isInstanceOf(ImportException.class);

            for (CmImportBatch invalid : List.of(
                batch("paper", "uploaded", 1, 0, 0, null, null, 21L),
                batch("paper", "waiting_confirm", 0, 0, 0, null, null, 21L),
                batch("paper", "waiting_confirm", 1, 1, 0, null, null, 21L),
                batch("paper", "waiting_confirm", 1, 0, 1, null, null, 21L)
            )) {
                when(repository.selectVisible(102L, 7L)).thenReturn(invalid);
                assertThatThrownBy(() -> service.confirmPaper("102", "confirm"))
                    .isInstanceOf(ImportException.class);
            }
            when(repository.selectVisible(103L, 7L)).thenReturn(question);
            assertThatThrownBy(() -> service.paperProgress("103")).isInstanceOf(ImportException.class);
        }
    }

    @Test
    void paperProgressMapsAbsentAndPresentOptionalReferences() {
        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            CmImportBatch batch = batch("paper", "completed", 2, 0, 0, null, null, null);
            when(repository.selectVisible(101L, 7L)).thenReturn(batch);
            when(repository.selectPaperImport(101L)).thenReturn(new PaperImportRow(101L, 9L, "Paper", "PRACTICE", 90, null, null));
            var result = service.paperProgress("101");
            assertThat(result.syllabusVersionId()).isNull();
            assertThat(result.collectionId()).isNull();
            assertThat(result.revisionId()).isNull();
            assertThat(result.failureTraceId()).isNull();
        }
    }

    @Test
    void knowledgeDiffValidatesFiltersAndMapsConfiguredQuery() {
        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            CmImportBatch batch = batch("knowledge_point", "waiting_confirm", 1, 0, 0, "base", "resolution", 21L);
            when(repository.selectVisible(101L, 7L)).thenReturn(batch, batch, batch, batch, batch, batch);

            KnowledgeImportDiffQueryBo badStatus = new KnowledgeImportDiffQueryBo();
            badStatus.setResolutionStatus("done");
            KnowledgeImportDiffQueryBo badAction = new KnowledgeImportDiffQueryBo();
            badAction.setAction("replace");
            KnowledgeImportDiffQueryBo reversed = new KnowledgeImportDiffQueryBo();
            reversed.setMinScore(BigDecimal.TEN);
            reversed.setMaxScore(BigDecimal.ONE);
            for (KnowledgeImportDiffQueryBo query : List.of(badStatus, badAction, reversed)) {
                assertThatThrownBy(() -> service.knowledgeDiff("101", query)).isInstanceOf(ImportException.class);
            }

            when(repository.selectKnowledgeImportDiffs(101L, 11L, "pending", "move", true,
                BigDecimal.ONE, BigDecimal.TEN, 5, 5L)).thenReturn(List.of());
            when(repository.countKnowledgeImportDiffs(101L, 11L, "pending", "move", true,
                BigDecimal.ONE, BigDecimal.TEN)).thenReturn(0L);
            when(repository.selectKnowledgeImportDiffCounts(101L)).thenReturn(new KnowledgeImportDiffCounts(0, 0, 0, 0, 0, 0, 0, 0));
            KnowledgeImportDiffQueryBo query = new KnowledgeImportDiffQueryBo();
            query.setExamSubjectId("11");
            query.setResolutionStatus("pending");
            query.setAction("move");
            query.setExcludeUnchanged(true);
            query.setMinScore(BigDecimal.ONE);
            query.setMaxScore(BigDecimal.TEN);
            query.setPageNum(2);
            query.setPageSize(5);
            assertThat(service.knowledgeDiff("101", query).total()).isZero();

            when(repository.selectVisible(102L, 7L)).thenReturn(batch("question", "waiting_confirm", 1, 0, 0, null, null, 21L));
            assertThatThrownBy(() -> service.knowledgeDiff("102", new KnowledgeImportDiffQueryBo()))
                .isInstanceOf(ImportException.class);
        }
    }

    @Test
    void resolutionRejectsEmptyReplayStateAndMissingDiffContracts() {
        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            CmImportBatch waiting = batch("knowledge_point", "waiting_confirm", 1, 0, 0, "base", "resolution", 21L);
            CmImportBatch completed = batch("knowledge_point", "completed", 1, 0, 0, "base", "resolution", 21L);
            when(repository.selectVisible(101L, 7L)).thenReturn(waiting, waiting, completed, waiting);
            assertThatThrownBy(() -> service.resolveKnowledgeDiffBatch("101", null, "request"))
                .isInstanceOf(ImportException.class);

            KnowledgeImportDiffBatchResolutionBo empty = new KnowledgeImportDiffBatchResolutionBo();
            empty.setResolutions(List.of());
            assertThatThrownBy(() -> service.resolveKnowledgeDiffBatch("101", empty, "request"))
                .isInstanceOf(ImportException.class);

            KnowledgeImportDiffBatchResolutionBo one = resolutions(decision("301", "approve"));
            when(repository.selectIdempotency(ImportProtocol.KNOWLEDGE_DIFF_BATCH_RESOLVE_ACTION, "state"))
                .thenReturn(null);
            assertThatThrownBy(() -> service.resolveKnowledgeDiffBatch("101", one, "state"))
                .isInstanceOf(ImportException.class);

            when(repository.selectIdempotency(ImportProtocol.KNOWLEDGE_DIFF_BATCH_RESOLVE_ACTION, "missing"))
                .thenReturn(null);
            when(repository.selectKnowledgeImportDiffForUpdate(101L, 301L)).thenReturn(null);
            assertThatThrownBy(() -> service.resolveKnowledgeDiffBatch("101", one, "missing"))
                .isInstanceOf(ImportException.class);
        }
    }

    @Test
    void resolutionRejectsImmutableUnsupportedAndInvalidMappingDecisions() {
        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            CmImportBatch waiting = batch("knowledge_point", "waiting_confirm", 1, 0, 0, "base", "resolution", 21L);
            when(repository.selectVisible(101L, 7L)).thenReturn(waiting, waiting, waiting, waiting);
            KnowledgeImportDiffRow immutable = diff("unchanged", "not_required", null, null, 11L);
            KnowledgeImportDiffRow unsupported = diff("add", "pending", null, null, 11L);
            KnowledgeImportDiffRow missingOld = diff("update", "pending", 201L, 201L, 11L);
            KnowledgeImportDiffRow wrongSubject = diff("update", "pending", 202L, 202L, 11L);
            when(repository.selectKnowledgeImportDiffForUpdate(101L, 301L))
                .thenReturn(immutable, unsupported, missingOld, wrongSubject);
            when(repository.selectCurrentKnowledgePoint(21L, 201L)).thenReturn(null);
            KnowledgeImportPointRow old = new KnowledgeImportPointRow();
            old.setKnowledgePointId(202L);
            old.setExamSubjectId(12L);
            when(repository.selectCurrentKnowledgePoint(21L, 202L)).thenReturn(old);
            when(repository.selectIdempotency(anyString(), anyString())).thenReturn(null);

            for (KnowledgeImportDiffResolutionBo command : List.of(
                decision("301", "approve"), decision("301", "other"),
                decision("301", "approve"), decision("301", "approve")
            )) {
                assertThatThrownBy(() -> service.resolveKnowledgeDiffBatch("101", resolutions(command), UUID.randomUUID().toString()))
                    .isInstanceOf(ImportException.class);
            }
        }
    }

    @Test
    void resolutionRejectsDuplicateInheritanceAndFailedWrites() {
        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            CmImportBatch waiting = batch("knowledge_point", "waiting_confirm", 1, 0, 0, "base", "resolution", 21L);
            KnowledgeImportDiffRow update = diff("update", "pending", 201L, 201L, 11L);
            KnowledgeImportPointRow old = new KnowledgeImportPointRow();
            old.setKnowledgePointId(201L);
            old.setExamSubjectId(11L);
            when(repository.selectVisible(101L, 7L)).thenReturn(waiting, waiting);
            when(repository.selectIdempotency(anyString(), anyString())).thenReturn(null);
            when(repository.selectKnowledgeImportDiffForUpdate(101L, 301L)).thenReturn(update, update);
            when(repository.selectCurrentKnowledgePoint(21L, 201L)).thenReturn(old);
            when(repository.countConfirmedKnowledgePointUse(101L, 201L, 301L)).thenReturn(1L, 0L);
            when(repository.selectPendingDeleteDiffForOldForUpdate(101L, 201L)).thenReturn(null);
            when(repository.updateKnowledgeImportDiffResolution(101L, 301L, "update", 201L, "approve", 7L)).thenReturn(0);

            assertThatThrownBy(() -> service.resolveKnowledgeDiffBatch("101", resolutions(decision("301", "approve")), "duplicate"))
                .isInstanceOf(ImportException.class);
            assertThatThrownBy(() -> service.resolveKnowledgeDiffBatch("101", resolutions(decision("301", "approve")), "write"))
                .isInstanceOf(ImportException.class);
        }
    }

    @Test
    void addResolutionExercisesPendingDeleteShortCircuitsAndCascade() {
        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            CmImportBatch waiting = batch("knowledge_point", "waiting_confirm", 1, 0, 0, "base", "resolution", 21L);
            KnowledgeImportDiffRow noSuggestion = diff("add", "pending", null, null, 11L);
            KnowledgeImportDiffRow usedSuggestion = diff("add", "pending", null, 201L, 11L);
            KnowledgeImportDiffRow existingDelete = diff("add", "pending", null, 202L, 11L);
            when(repository.selectVisible(101L, 7L)).thenReturn(waiting, waiting, waiting, waiting);
            when(repository.selectIdempotency(anyString(), anyString())).thenReturn(null);
            when(repository.selectKnowledgeImportDiffForUpdate(101L, 301L))
                .thenReturn(noSuggestion, usedSuggestion, existingDelete, noSuggestion);
            when(repository.countConfirmedKnowledgePointUse(101L, 201L, 301L)).thenReturn(1L);
            when(repository.countConfirmedKnowledgePointUse(101L, 202L, 301L)).thenReturn(0L);
            when(repository.selectPendingDeleteDiffForOldForUpdate(101L, 202L)).thenReturn(diff("delete", "pending", 202L, null, 11L));
            when(repository.updateKnowledgeImportDiffResolution(anyLong(), anyLong(), anyString(), any(), anyString(), anyLong())).thenReturn(1);
            when(reconciliation.currentResolutionHash(101L)).thenReturn("hash");
            when(repository.updateKnowledgeImportResolutionHash(101L, "hash")).thenReturn(1);
            when(repository.completeIdempotency(anyString(), anyString(), eq(101L), anyString())).thenReturn(1);

            for (KnowledgeImportDiffResolutionBo command : List.of(
                decision("301", "approve"), decision("301", "approve"),
                decision("301", "approve"), decision("301", "reject")
            )) {
                assertThat(service.resolveKnowledgeDiffBatch("101", resolutions(command), UUID.randomUUID().toString()).resolvedCount())
                    .isEqualTo(1);
            }
            verify(repository).cascadeKnowledgeImportDiffResolution(101L, 301L, "reject", 7L);
        }
    }

    @Test
    void paperValidationReplayRejectsWrongPayloadMissingResourceTypeAndOwner() throws Exception {
        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            CmImportBatch paper = batch("paper", "uploaded", 1, 0, 0, null, null, 21L);
            String hash = confirmationHash(101L, ImportProtocol.PAPER_VALIDATE_ACTION);
            when(repository.selectVisible(101L, 7L)).thenReturn(paper, paper, paper, paper);
            when(repository.selectIdempotency(ImportProtocol.PAPER_VALIDATE_ACTION, "one"))
                .thenReturn(new CmIdempotencyRecord("different", "succeeded", 101L, 202, "{}"));
            when(repository.selectIdempotency(ImportProtocol.PAPER_VALIDATE_ACTION, "two"))
                .thenReturn(new CmIdempotencyRecord(hash, "succeeded", null, 202, "{}"));
            when(repository.selectIdempotency(ImportProtocol.PAPER_VALIDATE_ACTION, "three"))
                .thenReturn(new CmIdempotencyRecord(hash, "succeeded", 101L, 202, "{}"));
            when(repository.selectById(101L)).thenReturn(
                batch("question", "validating", 1, 0, 0, null, null, 21L),
                batchOwnedBy("paper", "validating", 8L)
            );
            when(repository.selectIdempotency(ImportProtocol.PAPER_VALIDATE_ACTION, "four"))
                .thenReturn(new CmIdempotencyRecord(hash, "succeeded", 101L, 202, "{}"));

            for (String request : List.of("one", "two", "three", "four")) {
                assertThatThrownBy(() -> service.validatePaper("101", request)).isInstanceOf(ImportException.class);
            }
        }
    }

    private static ImportCreateBo questionForm(MultipartFile file) {
        ImportCreateBo form = new ImportCreateBo();
        form.setImportType("question");
        form.setTemplateVersion("question-zip/1.0");
        form.setCertificationId("9");
        form.setFile(file);
        return form;
    }

    private static ImportCreateBo textbookForm() {
        ImportCreateBo form = new ImportCreateBo();
        form.setFile(largeFile("book.jsonl", "application/jsonl"));
        form.setImportType("document_chunk");
        form.setTemplateVersion("document_chunk/1.0");
        form.setMode("create");
        form.setCertificationId("9");
        form.setTitle("Book");
        form.setEdition("Edition");
        form.setSubjectMappings("[{\"subjectNo\":1,\"examSubjectId\":\"11\"}]");
        return form;
    }

    private static ImportCreateBo replacementForm() {
        ImportCreateBo form = textbookForm();
        form.setMode("replace_draft");
        form.setDocumentId("201");
        form.setCertificationId(null);
        form.setSyllabusVersionId(null);
        return form;
    }

    private static PaperImportCreateBo paperForm() {
        return paperWithFile(file("paper.zip", "application/zip", new byte[]{1}));
    }

    private static PaperImportCreateBo paperWithFile(MultipartFile file) {
        PaperImportCreateBo form = new PaperImportCreateBo();
        form.setFile(file);
        form.setCollectionName("Paper");
        form.setCollectionType("PRACTICE");
        form.setDurationMinutes(90);
        form.setCertificationId("9");
        return form;
    }

    private static MockMultipartFile file(String name, String mime, byte[] content) {
        return new MockMultipartFile("file", name, mime, content);
    }

    private static MockMultipartFile largeFile(String name, String mime) {
        return file(name, mime, ("{}\n" + "x".repeat(1_048_577)).getBytes(StandardCharsets.UTF_8));
    }

    private static org.dromara.certmuse.catalog.domain.TextbookImportDocument document(
        String status, Long syllabusId, long certificationId
    ) {
        return new org.dromara.certmuse.catalog.domain.TextbookImportDocument(
            201L, syllabusId, "Book", "Edition", status, "0", 7L, 8L, certificationId
        );
    }

    private static CmImportBatch batch(
        String type, String status, int valid, int warning, int failed,
        String baselineHash, String resolutionHash, Long syllabusId
    ) {
        return new CmImportBatch(
            101L, "request", null, syllabusId, null, "imports/source", "hash", 100L,
            type, "question".equals(type) || "paper".equals(type) ? "question-zip/1.0" : "knowledge_point/1.0",
            "{}", status, "stage", BigDecimal.TEN, valid, warning, failed, 0, "trace",
            OffsetDateTime.parse("2026-08-12T09:00:00+08:00"), null, null, 7L, 8L,
            baselineHash, resolutionHash, 9L
        );
    }

    private static CmImportBatch batchOwnedBy(String type, String status, long owner) {
        CmImportBatch base = batch(type, status, 1, 0, 0, null, null, 21L);
        return new CmImportBatch(
            base.id(), base.requestId(), base.documentId(), base.syllabusVersionId(), base.examSubjectId(),
            base.sourceFilePath(), base.sourceFileHash(), base.sourceFileSize(), base.importType(),
            base.templateVersion(), base.parseConfig(), base.status(), base.currentStage(), base.progressPercent(),
            base.validCount(), base.warningCount(), base.failedCount(), base.generatedCount(), base.traceId(),
            base.createTime(), base.startedTime(), base.finishedTime(), owner, base.createDept(),
            base.baselineHash(), base.resolutionHash(), base.certificationId()
        );
    }

    private static KnowledgeImportDiffRow diff(
        String action, String status, Long oldId, Long suggestedId, long subjectId
    ) {
        KnowledgeImportDiffRow row = new KnowledgeImportDiffRow();
        row.setId(301L);
        row.setImportBatchId(101L);
        row.setExamSubjectId(subjectId);
        row.setAction(action);
        row.setResolutionStatus(status);
        row.setOldKnowledgePointId(oldId);
        row.setSuggestedKnowledgePointId(suggestedId);
        return row;
    }

    private static KnowledgeImportDiffResolutionBo decision(String diffId, String decision) {
        KnowledgeImportDiffResolutionBo command = new KnowledgeImportDiffResolutionBo();
        command.setDiffId(diffId);
        command.setDecision(decision);
        return command;
    }

    private static KnowledgeImportDiffBatchResolutionBo resolutions(KnowledgeImportDiffResolutionBo... commands) {
        KnowledgeImportDiffBatchResolutionBo batch = new KnowledgeImportDiffBatchResolutionBo();
        batch.setResolutions(List.of(commands));
        return batch;
    }

    private static MockedStatic<LoginHelper> ordinaryUser() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        login.when(LoginHelper::getUserId).thenReturn(7L);
        login.when(LoginHelper::getDeptId).thenReturn(8L);
        return login;
    }

    private static String confirmationHash(long batchId, String action) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
            (action + "\n" + batchId + "\n").getBytes(StandardCharsets.UTF_8)
        ));
    }
}
