package org.dromara.certmuse.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;

import org.dromara.certmuse.catalog.domain.bo.SyllabusPublishedDateUpdateBo;
import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeTreeSummaryVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusListVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusOverviewBaseVo;
import org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.KnowledgeTreeServiceImpl;
import org.dromara.certmuse.catalog.support.CatalogQueryException;
import org.dromara.certmuse.catalog.support.SyllabusPublishedDateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.dromara.common.satoken.utils.LoginHelper;

import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class KnowledgeTreeServiceTest {
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
    void returnsPagedSyllabusesUsingTheRequestedFilters() {
        var row = new SyllabusListVo("21", "系统架构设计师 · 第二版", "1", "系统架构设计师", "第二版",
            null, null, null, 0L, "empty");
        when(mapper.selectSyllabuses("架构", 1L, "第二", "available", 20, 0L)).thenReturn(List.of(row));
        when(mapper.countSyllabuses("架构", 1L, "第二", "available")).thenReturn(1L);

        var result = service.syllabuses(" 架构 ", "1", "第二", "available", null, null);

        assertThat(result.getRows()).containsExactly(row);
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    void calculatesLargePageOffsetsWithoutIntegerOverflow() {
        when(mapper.selectSyllabuses(null, null, null, null, 100, 214_748_364_600L)).thenReturn(List.of());
        when(mapper.countSyllabuses(null, null, null, null)).thenReturn(0L);

        var result = service.syllabuses(null, null, null, null, Integer.MAX_VALUE, 100);

        assertThat(result.getRows()).isEmpty();
    }

    @Test
    void rejectsAnInvalidSyllabusIdBeforeQueryingTheDatabase() {
        assertThatThrownBy(() -> service.knowledgeTree("0"))
            .isInstanceOf(CatalogQueryException.class)
            .extracting(error -> ((CatalogQueryException) error).errorCode())
            .isEqualTo("CATALOG_QUERY_INVALID");
    }

    @Test
    void assemblesAnEmptyKnowledgeTreeForAnExistingSyllabus() {
        var overview = new SyllabusOverviewBaseVo("21", "1", "系统架构设计师", "第二版", null,
            OffsetDateTime.parse("2026-07-30T14:47:20+08:00"));
        when(mapper.selectOverview(21L)).thenReturn(overview);
        when(mapper.selectSummary(21L)).thenReturn(new KnowledgeTreeSummaryVo(1, 3, 0L, 0L, 0L));
        when(mapper.selectSubjects(21L)).thenReturn(List.of());
        when(mapper.selectNodes(21L)).thenReturn(List.of());

        var result = service.knowledgeTree("21");

        assertThat(result.syllabus().latestKnowledgeImport()).isNull();
        assertThat(result.summary().knowledgePointCount()).isZero();
        assertThat(result.nodes()).isEmpty();
        verify(mapper).selectLatestKnowledgeImport(21L);
    }

    @Test
    void updatesPublishedDateForAnExistingSyllabus() {
        String requestId = "adf7bbf7-7f3f-4632-9e06-78b6d79b047d";
        SyllabusPublishedDateUpdateBo command = new SyllabusPublishedDateUpdateBo();
        command.setPublishedDate(LocalDate.of(2026, 8, 13));
        when(mapper.lockSyllabusVersion(21L)).thenReturn(21L);
        when(mapper.insertIdempotency(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.updateSyllabusPublishedDate(21L, command.getPublishedDate(), 7L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            service.updateSyllabusPublishedDate("21", requestId, command);
        }

        verify(mapper).updateSyllabusPublishedDate(21L, LocalDate.of(2026, 8, 13), 7L);
        verify(mapper).insertIdempotency(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.eq("syllabus_published_date_update"), org.mockito.ArgumentMatchers.eq(requestId),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(21L), org.mockito.ArgumentMatchers.any());
        verify(mapper).completeIdempotency("syllabus_published_date_update", requestId, 21L,
            "{\"schema_version\":\"syllabus_published_date_response/1.0\",\"data\":null}");
    }

    @Test
    void rejectsMissingPublishedDateBeforeUpdatingTheDatabase() {
        SyllabusPublishedDateUpdateBo command = new SyllabusPublishedDateUpdateBo();

        assertThatThrownBy(() -> service.updateSyllabusPublishedDate("21", "adf7bbf7-7f3f-4632-9e06-78b6d79b047d", command))
            .isInstanceOf(SyllabusPublishedDateException.class)
            .extracting(error -> ((SyllabusPublishedDateException) error).errorCode())
            .isEqualTo("SYLLABUS_PUBLISHED_DATE_INVALID");
        verify(mapper, never()).lockSyllabusVersion(21L);
        verify(mapper, never()).updateSyllabusPublishedDate(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPublishedDateUpdateForAMissingSyllabus() {
        SyllabusPublishedDateUpdateBo command = new SyllabusPublishedDateUpdateBo();
        command.setPublishedDate(LocalDate.of(2026, 8, 13));
        when(mapper.lockSyllabusVersion(21L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateSyllabusPublishedDate("21", "adf7bbf7-7f3f-4632-9e06-78b6d79b047d", command))
            .isInstanceOf(SyllabusPublishedDateException.class)
            .extracting(error -> ((SyllabusPublishedDateException) error).errorCode())
            .isEqualTo("SYLLABUS_VERSION_NOT_FOUND");
        verify(mapper, never()).updateSyllabusPublishedDate(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void replaysASucceededPublishedDateRequestWithoutASecondUpdate() {
        String requestId = "adf7bbf7-7f3f-4632-9e06-78b6d79b047d";
        SyllabusPublishedDateUpdateBo command = new SyllabusPublishedDateUpdateBo();
        command.setPublishedDate(LocalDate.of(2026, 8, 13));
        when(mapper.selectIdempotency("syllabus_published_date_update", requestId)).thenReturn(
            new CmIdempotencyRecord(
                "6dbf077de8b8460597afb49caed9fde396ebb2f6483b758f96b70790aff0f65e",
                "succeeded", 21L, 200, "{\"schema_version\":\"syllabus_published_date_response/1.0\",\"data\":null}"));

        service.updateSyllabusPublishedDate("21", requestId, command);

        verify(mapper, never()).lockSyllabusVersion(21L);
        verify(mapper, never()).updateSyllabusPublishedDate(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMalformedPublishedDateRequestId() {
        SyllabusPublishedDateUpdateBo command = new SyllabusPublishedDateUpdateBo();
        command.setPublishedDate(LocalDate.of(2026, 8, 13));

        assertThatThrownBy(() -> service.updateSyllabusPublishedDate("21", "not-a-uuid", command))
            .isInstanceOf(SyllabusPublishedDateException.class)
            .satisfies(error -> {
                var exception = (SyllabusPublishedDateException) error;
                assertThat(exception.errorCode()).isEqualTo("SYLLABUS_PUBLISHED_DATE_INVALID");
                assertThat(exception.apiFieldErrors()).singleElement().satisfies(field ->
                    assertThat(field.field()).isEqualTo("requestId"));
            });
        verify(mapper, never()).lockSyllabusVersion(21L);
    }

    @Test
    void deletesTheSyllabusAggregateIncludingItsExclusiveQuestionAndKnowledgeImportDiffs() {
        when(mapper.lockSyllabusVersion(21L)).thenReturn(21L);
        when(mapper.countImportingImports(21L)).thenReturn(0L);

        service.deleteKnowledgeTree("21");

        var order = inOrder(mapper);
        order.verify(mapper).setQualificationCascadeDelete(true);
        order.verify(mapper).deleteKnowledgeImportDiffsBySyllabusVersion(21L);
        order.verify(mapper).deleteQuestionsExclusiveToSyllabusVersion(21L);
        order.verify(mapper).deleteSyllabusVersion(21L);
        order.verify(mapper).setQualificationCascadeDelete(false);
    }

    @Test
    void rejectsDeletionWhileAnySyllabusImportIsRunning() {
        when(mapper.lockSyllabusVersion(21L)).thenReturn(21L);
        when(mapper.countImportingImports(21L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteKnowledgeTree("21"))
            .isInstanceOf(CatalogQueryException.class)
            .satisfies(error -> {
                var exception = (CatalogQueryException) error;
                assertThat(exception.status()).isEqualTo(409);
                assertThat(exception.errorCode()).isEqualTo("SYLLABUS_VERSION_IMPORT_IN_PROGRESS");
                assertThat(exception.getMessage()).isEqualTo("该考纲存在正在写入的导入任务，请稍后再删除");
            });
        verify(mapper, never()).deleteSyllabusVersion(21L);
    }

    @Test
    void reportsMissingSyllabusDuringDeletion() {
        when(mapper.lockSyllabusVersion(21L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteKnowledgeTree("21"))
            .isInstanceOf(CatalogQueryException.class)
            .extracting(error -> ((CatalogQueryException) error).errorCode())
            .isEqualTo("SYLLABUS_VERSION_NOT_FOUND");
        verify(mapper, never()).countImportingImports(21L);
        verify(mapper, never()).deleteSyllabusVersion(21L);
    }

    @Test
    void rejectsInvalidSyllabusFiltersBeforeQueryingTheDatabase() {
        assertThatThrownBy(() -> service.syllabuses("x".repeat(101), null, null, null, 1, 20))
            .isInstanceOf(CatalogQueryException.class)
            .extracting(error -> ((CatalogQueryException) error).errorCode())
            .isEqualTo("CATALOG_QUERY_INVALID");
        assertThatThrownBy(() -> service.syllabuses(null, "bad", null, null, 1, 20))
            .isInstanceOf(CatalogQueryException.class)
            .extracting(error -> ((CatalogQueryException) error).errorCode())
            .isEqualTo("CATALOG_QUERY_INVALID");
        assertThatThrownBy(() -> service.syllabuses(null, null, null, "unknown", 1, 20))
            .isInstanceOf(CatalogQueryException.class)
            .extracting(error -> ((CatalogQueryException) error).errorCode())
            .isEqualTo("CATALOG_QUERY_INVALID");
        assertThatThrownBy(() -> service.syllabuses(null, null, null, null, 0, 20))
            .isInstanceOf(CatalogQueryException.class)
            .extracting(error -> ((CatalogQueryException) error).errorCode())
            .isEqualTo("CATALOG_QUERY_INVALID");
        assertThatThrownBy(() -> service.syllabuses(null, null, null, null, 1, 101))
            .isInstanceOf(CatalogQueryException.class)
            .extracting(error -> ((CatalogQueryException) error).errorCode())
            .isEqualTo("CATALOG_QUERY_INVALID");
        verify(mapper, never()).selectSyllabuses(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void distinguishesMissingOverviewFromUnexpectedQueryFailure() {
        when(mapper.selectOverview(21L)).thenReturn(null);
        assertThatThrownBy(() -> service.knowledgeTree("21"))
            .isInstanceOf(CatalogQueryException.class)
            .satisfies(error -> {
                var exception = (CatalogQueryException) error;
                assertThat(exception.status()).isEqualTo(404);
                assertThat(exception.errorCode()).isEqualTo("SYLLABUS_VERSION_NOT_FOUND");
            });

        when(mapper.selectOverview(22L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service.knowledgeTree("22"))
            .isInstanceOf(CatalogQueryException.class)
            .satisfies(error -> {
                var exception = (CatalogQueryException) error;
                assertThat(exception.status()).isEqualTo(500);
                assertThat(exception.errorCode()).isEqualTo("CATALOG_QUERY_FAILURE");
                assertThat(exception.traceId()).isNotBlank();
            });
    }

    @Test
    void deletesTheSyllabusAggregateWhenTextbooksAndQuestionsReferenceTheTree() {
        when(mapper.lockSyllabusVersion(21L)).thenReturn(21L);
        when(mapper.countImportingImports(21L)).thenReturn(0L);

        service.deleteKnowledgeTree("21");

        verify(mapper).deleteQuestionsExclusiveToSyllabusVersion(21L);
        verify(mapper).deleteSyllabusVersion(21L);
    }

    @Test
    void wrapsUnexpectedDeletionFailureWithTraceId() {
        when(mapper.lockSyllabusVersion(21L)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.deleteKnowledgeTree("21"))
            .isInstanceOf(CatalogQueryException.class)
            .satisfies(error -> {
                var exception = (CatalogQueryException) error;
                assertThat(exception.status()).isEqualTo(500);
                assertThat(exception.errorCode()).isEqualTo("CATALOG_QUERY_FAILURE");
                assertThat(exception.traceId()).isNotBlank();
            });
    }
}
