package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.vo.KnowledgeTreeSummaryVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusOverviewBaseVo;
import org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.KnowledgeTreeServiceImpl;
import org.dromara.certmuse.catalog.support.CatalogQueryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class KnowledgeTreeServicePublicBranchTest {

    private static final long SYLLABUS_ID = 21L;

    @Mock
    private KnowledgeTreeMapper mapper;
    @Mock
    private ImportPersistenceService importPersistence;

    private KnowledgeTreeService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeTreeServiceImpl(mapper, importPersistence);
    }

    @Test
    void syllabusesNormalizesBlankCertificationAndEmptyStatus() {
        when(mapper.selectSyllabuses(null, null, null, "empty", 20, 0L)).thenReturn(List.of());
        when(mapper.countSyllabuses(null, null, null, "empty")).thenReturn(0L);

        var result = service.syllabuses(" ", " ", null, " empty ", null, null);

        assertThat(result.getRows()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void syllabusesRejectsBlankPageSizeAndOverlongVersionNameBeforeQuerying() {
        assertCatalogError(
            () -> service.syllabuses(null, null, null, null, 1, 0),
            400,
            "CATALOG_QUERY_INVALID"
        );
        assertCatalogError(
            () -> service.syllabuses(null, null, "x".repeat(101), null, 1, 20),
            400,
            "CATALOG_QUERY_INVALID"
        );

        verify(mapper, never()).selectSyllabuses(any(), any(), any(), any(), anyInt(), anyLong());
    }

    @Test
    void syllabusesPreservesCatalogFailuresAndWrapsUnexpectedDatabaseFailures() {
        CatalogQueryException catalogFailure = new CatalogQueryException(409, "QUERY_CONFLICT", "查询冲突");
        when(mapper.selectSyllabuses(null, null, null, "available", 20, 0L))
            .thenThrow(catalogFailure);

        assertThatThrownBy(() -> service.syllabuses(null, null, null, "available", 1, 20))
            .isSameAs(catalogFailure);

        when(mapper.selectSyllabuses(null, null, null, "empty", 20, 0L))
            .thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service.syllabuses(null, null, null, "empty", 1, 20))
            .isInstanceOfSatisfying(CatalogQueryException.class, error -> {
                assertThat(error.status()).isEqualTo(500);
                assertThat(error.errorCode()).isEqualTo("CATALOG_QUERY_FAILURE");
                assertThat(error.getMessage()).isEqualTo("查询考纲列表失败");
                assertThat(error.traceId()).isNotBlank();
            });
    }

    @Test
    void knowledgeTreePreservesMapperCatalogFailureAndRejectsNullId() {
        CatalogQueryException catalogFailure = new CatalogQueryException(403, "SYLLABUS_FORBIDDEN", "不可访问");
        when(mapper.selectOverview(SYLLABUS_ID)).thenThrow(catalogFailure);

        assertThatThrownBy(() -> service.knowledgeTree("21")).isSameAs(catalogFailure);
        assertCatalogError(() -> service.knowledgeTree(null), 400, "CATALOG_QUERY_INVALID");
    }

    @Test
    void deleteKnowledgeTreeCleansDistinctObjectKeysImmediatelyWithoutSynchronization() {
        when(mapper.lockSyllabusVersion(SYLLABUS_ID)).thenReturn(SYLLABUS_ID);
        when(mapper.countImportingImports(SYLLABUS_ID)).thenReturn(0L);
        when(mapper.selectSourceObjectKeysBySyllabusVersion(SYLLABUS_ID)).thenReturn(
            Arrays.asList(null, "", "   ", "imports/source.jsonl", "imports/source.jsonl")
        );
        when(mapper.selectImageObjectKeysBySyllabusVersion(SYLLABUS_ID)).thenReturn(
            Arrays.asList(null, "images/a.png", "images/a.png")
        );

        service.deleteKnowledgeTree("21");

        verify(importPersistence, times(1)).enqueueCleanup(
            "imports/source.jsonl", "unknown", "syllabus_version_delete"
        );
        verify(importPersistence, times(1)).enqueueImageCleanup(
            "images/a.png", "unknown", "syllabus_version_delete"
        );
    }

    @Test
    void deleteKnowledgeTreeDefersCleanupUntilCommitWhenSynchronizationIsActive() {
        when(mapper.lockSyllabusVersion(SYLLABUS_ID)).thenReturn(SYLLABUS_ID);
        when(mapper.countImportingImports(SYLLABUS_ID)).thenReturn(0L);
        when(mapper.selectSourceObjectKeysBySyllabusVersion(SYLLABUS_ID)).thenReturn(List.of("imports/source.jsonl"));
        when(mapper.selectImageObjectKeysBySyllabusVersion(SYLLABUS_ID)).thenReturn(List.of("images/a.png"));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteKnowledgeTree("21");

            verifyNoInteractions(importPersistence);
            List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(importPersistence).enqueueCleanup(
                "imports/source.jsonl", "unknown", "syllabus_version_delete"
            );
            verify(importPersistence).enqueueImageCleanup(
                "images/a.png", "unknown", "syllabus_version_delete"
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteKnowledgeTreeSkipsCleanupWhenBothStorageQueriesAreNull() {
        when(mapper.lockSyllabusVersion(SYLLABUS_ID)).thenReturn(SYLLABUS_ID);
        when(mapper.countImportingImports(SYLLABUS_ID)).thenReturn(0L);
        when(mapper.selectSourceObjectKeysBySyllabusVersion(SYLLABUS_ID)).thenReturn(null);
        when(mapper.selectImageObjectKeysBySyllabusVersion(SYLLABUS_ID)).thenReturn(null);

        service.deleteKnowledgeTree("21");

        verifyNoInteractions(importPersistence);
    }

    @Test
    void deleteKnowledgeTreeRestoresCascadeFlagWhenDeleteFailsAndWrapsTheFailure() {
        when(mapper.lockSyllabusVersion(SYLLABUS_ID)).thenReturn(SYLLABUS_ID);
        when(mapper.countImportingImports(SYLLABUS_ID)).thenReturn(0L);
        when(mapper.selectSourceObjectKeysBySyllabusVersion(SYLLABUS_ID)).thenReturn(List.of("imports/source.jsonl"));
        when(mapper.selectImageObjectKeysBySyllabusVersion(SYLLABUS_ID)).thenReturn(List.of());
        when(mapper.deleteQuestionsExclusiveToSyllabusVersion(SYLLABUS_ID))
            .thenThrow(new IllegalStateException("delete failed"));

        assertThatThrownBy(() -> service.deleteKnowledgeTree("21"))
            .isInstanceOfSatisfying(CatalogQueryException.class, error -> {
                assertThat(error.status()).isEqualTo(500);
                assertThat(error.errorCode()).isEqualTo("CATALOG_QUERY_FAILURE");
                assertThat(error.getMessage()).isEqualTo("删除知识点树失败");
                assertThat(error.traceId()).isNotBlank();
            });

        InOrder order = inOrder(mapper);
        order.verify(mapper).setQualificationCascadeDelete(true);
        order.verify(mapper).deleteKnowledgeImportDiffsBySyllabusVersion(SYLLABUS_ID);
        order.verify(mapper).deleteQuestionsExclusiveToSyllabusVersion(SYLLABUS_ID);
        order.verify(mapper).setQualificationCascadeDelete(false);
        verify(mapper, never()).deleteSyllabusVersion(SYLLABUS_ID);
        verifyNoInteractions(importPersistence);
    }

    @Test
    void deleteKnowledgeTreeRejectsNonNumericAndOverflowIdsBeforeLocking() {
        for (String id : List.of("", "-1", "+1", "9223372036854775808")) {
            assertCatalogError(() -> service.deleteKnowledgeTree(id), 400, "CATALOG_QUERY_INVALID");
        }

        verify(mapper, never()).lockSyllabusVersion(anyLong());
    }

    @Test
    void knowledgeTreeWrapsFailuresFromLaterAssemblyQueries() {
        SyllabusOverviewBaseVo overview = new SyllabusOverviewBaseVo(
            "21", "1", "系统架构设计师", "第二版", null,
            OffsetDateTime.parse("2026-08-12T09:00:00+08:00")
        );
        when(mapper.selectOverview(SYLLABUS_ID)).thenReturn(overview);
        when(mapper.selectSummary(SYLLABUS_ID)).thenReturn(new KnowledgeTreeSummaryVo(1, 3, 0L, 0L, 0L));
        when(mapper.selectSubjects(SYLLABUS_ID)).thenThrow(new IllegalStateException("subjects unavailable"));

        assertThatThrownBy(() -> service.knowledgeTree("21"))
            .isInstanceOfSatisfying(CatalogQueryException.class, error -> {
                assertThat(error.status()).isEqualTo(500);
                assertThat(error.errorCode()).isEqualTo("CATALOG_QUERY_FAILURE");
                assertThat(error.getMessage()).isEqualTo("查询知识点树失败");
                assertThat(error.traceId()).isNotBlank();
            });
    }

    private static void assertCatalogError(ThrowingCall call, int status, String code) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(CatalogQueryException.class, error -> {
                assertThat(error.status()).isEqualTo(status);
                assertThat(error.errorCode()).isEqualTo(code);
            });
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
