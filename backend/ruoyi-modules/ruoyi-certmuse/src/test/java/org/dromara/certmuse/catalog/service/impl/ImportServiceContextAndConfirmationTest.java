package org.dromara.certmuse.catalog.service.impl;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.bo.ImportCreateBo;
import org.dromara.certmuse.catalog.domain.vo.ExamSubjectOptionVo;
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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

/** Public context selection and confirmation contracts for content imports. */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportServiceContextAndConfirmationTest {
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
        JsonMapper json = JsonMapper.builder().build();
        service = new ImportServiceImpl(repository, storage, persistence, json,
            new ImportJsonDocumentFactory(json), reconciliation);
    }

    @Test
    void textbookContextUsesCertificationToOfferSubjectsAndSyllabusToOfferReplaceableDrafts() {
        when(repository.selectSyllabusOptions()).thenReturn(List.of());
        when(repository.selectCertificationOptions()).thenReturn(List.of());
        when(repository.selectExamSubjectOptionsByCertification(9L)).thenReturn(List.of(
            new ExamSubjectOptionVo("11", 1, "基础知识"), new ExamSubjectOptionVo("12", 2, "案例分析")
        ));
        when(repository.selectCertificationName(9L)).thenReturn("系统架构设计师");

        var certificationOnly = service.textbookContextOptions(null, "9");

        assertThat(certificationOnly.examSubjects()).hasSize(2);
        assertThat(certificationOnly.defaultSubjectMappings()).extracting(mapping -> mapping.subjectNo())
            .containsExactly(1, 2);
        assertThat(certificationOnly.replaceableDrafts()).isEmpty();

        when(repository.selectSyllabusCertification(21L)).thenReturn(9L);
        when(repository.selectReplaceableTextbookDrafts(21L, 7L)).thenReturn(List.of());
        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            var syllabusSelected = service.textbookContextOptions("21", null);
            assertThat(syllabusSelected.examSubjects()).hasSize(2);
            verify(repository).selectReplaceableTextbookDrafts(21L, 7L);
        }
    }

    @Test
    void textbookContextReturnsEmptyDependentChoicesUntilTheUserSelectsAScope() {
        when(repository.selectSyllabusOptions()).thenReturn(List.of());
        when(repository.selectCertificationOptions()).thenReturn(List.of());

        var result = service.textbookContextOptions(null, null);

        assertThat(result.examSubjects()).isEmpty();
        assertThat(result.defaultSubjectMappings()).isEmpty();
        verify(repository, never()).selectExamSubjectOptionsByCertification(anyLong());
    }

    @Test
    void confirmAcceptsKnowledgeQuestionAndTextbookBatchesThroughTheirOwnPublicWorkflows() {
        CmImportBatch knowledge = batch("knowledge_point", "waiting_confirm", 2, 0, 0, "base", "resolution");
        CmImportBatch question = batch("question", "waiting_confirm", 2, 0, 0, null, null);
        CmImportBatch textbook = batch("document_chunk", "waiting_confirm", 2, 0, 0, null, null);
        when(repository.selectVisible(eq(101L), eq(7L))).thenReturn(knowledge, question, textbook);
        when(repository.countPendingKnowledgeImportDiffs(101L)).thenReturn(0L);
        when(repository.selectIdempotency(anyString(), anyString())).thenReturn(null);
        when(repository.selectById(101L)).thenReturn(knowledge, question, textbook);

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertThat(service.confirm("101", "knowledge-confirm").accepted()).isTrue();
            assertThat(service.confirm("101", "question-confirm").accepted()).isTrue();
            assertThat(service.confirm("101", UUID.randomUUID().toString()).accepted()).isTrue();
        }

        verify(persistence).acceptConfirmation(eq(101L), eq("knowledge-confirm"), anyString(), eq(7L), any(), anyString());
        verify(persistence).acceptQuestionConfirmation(eq(101L), eq("question-confirm"), anyString(), eq(7L), any(), anyString());
        verify(persistence).acceptTextbookConfirmation(eq(101L), anyString(), anyString(), eq(7L), any(), anyString());
    }

    @Test
    void confirmRejectsNonUuidTextbookConfirmationBeforePersistingIt() {
        when(repository.selectVisible(101L, 7L)).thenReturn(batch("document_chunk", "waiting_confirm", 1, 0, 0, null, null));
        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertImportError(() -> service.confirm("101", "not-a-uuid"), "IMPORT_CONTEXT_INVALID");
        }
        verify(persistence, never()).acceptTextbookConfirmation(anyLong(), anyString(), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void createTextbookRejectsAReplacementWhenSuppliedScopeDoesNotMatchTheDraft() {
        ImportCreateBo form = new ImportCreateBo();
        form.setImportType("document_chunk");
        form.setTemplateVersion("document_chunk/1.0");
        form.setMode("replace_draft");
        form.setDocumentId("201");
        form.setCertificationId("10");
        form.setFile(new org.springframework.mock.web.MockMultipartFile("file", "book.jsonl", "application/jsonl",
            ("{}\n" + "x".repeat(1_048_577)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(repository.selectTextbookDocument(201L, 7L)).thenReturn(new org.dromara.certmuse.catalog.domain.TextbookImportDocument(
            201L, 21L, "教材", "第一版", "draft", "0", 7L, 8L, 9L
        ));

        try (MockedStatic<LoginHelper> ignored = ordinaryUser()) {
            assertImportError(() -> service.createTextbook(form, UUID.randomUUID().toString()), "IMPORT_CONTEXT_INVALID");
        }
        verify(storage, never()).upload(anyString(), any());
    }

    private static CmImportBatch batch(String type, String status, int valid, int warning, int failed,
                                       String baseline, String resolution) {
        return new CmImportBatch(101L, "request", null, 21L, null, "imports/source", "hash", 100L,
            type, "document_chunk".equals(type) ? "document_chunk/1.0" : "question-zip/1.0", "{}", status,
            "stage", BigDecimal.TEN, valid, warning, failed, 0, "trace",
            OffsetDateTime.parse("2026-08-13T10:00:00+08:00"), null, null, 7L, 8L, baseline, resolution, 9L);
    }

    private static MockedStatic<LoginHelper> ordinaryUser() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        login.when(LoginHelper::getUserId).thenReturn(7L);
        login.when(LoginHelper::getDeptId).thenReturn(8L);
        return login;
    }

    private static void assertImportError(ThrowingCall call, String errorCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ImportException.class,
            error -> assertThat(error.errorCode()).isEqualTo(errorCode));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
