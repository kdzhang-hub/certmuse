package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.bo.CompletedImportBatchQueryBo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchDetailVo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchSyllabusOptionVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchTypeCountsVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchUploaderOptionVo;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.ImportServiceImpl;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.catalog.support.ImportStorage;
import org.dromara.certmuse.catalog.support.ImportJsonDocumentFactory;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class CompletedImportBatchServiceTest {

    @Mock
    private ImportMapper repository;
    @Mock
    private ImportStorage storage;
    @Mock
    private ImportPersistenceService persistence;
    private ImportService service;

    @BeforeEach
    void setUp() {
        JsonMapper objectMapper = JsonMapper.builder().build();
        service = new ImportServiceImpl(
            repository,
            storage,
            persistence,
            objectMapper,
            new ImportJsonDocumentFactory(objectMapper)
        );
    }

    @Test
    void appliesAllFiltersBusinessDatesPaginationAndOrdinaryUserVisibility() {
        var query = new CompletedImportBatchQueryBo();
        query.setKeyword("  架构  ");
        query.setImportType("knowledge_point");
        query.setSyllabusVersionId("21");
        query.setUploaderId("7");
        query.setCompletedStartDate(LocalDate.parse("2026-07-01"));
        query.setCompletedEndDate(LocalDate.parse("2026-07-31"));
        query.setPageNum(2);
        query.setPageSize(10);
        query.setOrderByColumn("completedTime");
        query.setIsAsc("asc");
        var row = completedRow();
        var counts = new ImportBatchTypeCountsVo(4, 2, 1, 1);
        when(repository.selectCompletedBatches(
            "架构",
            "knowledge_point",
            21L,
            7L,
            OffsetDateTime.parse("2026-07-01T00:00:00+08:00"),
            OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
            7L,
            true,
            10,
            10L
        )).thenReturn(List.of(row));
        when(repository.countCompletedBatches(
            "架构",
            "knowledge_point",
            21L,
            7L,
            OffsetDateTime.parse("2026-07-01T00:00:00+08:00"),
            OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
            7L
        )).thenReturn(1L);
        when(repository.selectCompletedTypeCounts(7L)).thenReturn(counts);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var result = service.completedBatches(query);

            assertThat(result.getRows()).containsExactly(row);
            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getTypeCounts()).isEqualTo(counts);
        }
        verify(repository).selectCompletedTypeCounts(7L);
    }

    @Test
    void superAdminSeesAllCompletedBatchesWithDefaultQueryValues() {
        var query = new CompletedImportBatchQueryBo();
        when(repository.selectCompletedBatches(
            null, null, null, null, null, null, null, false, 20, 0L
        )).thenReturn(List.of());
        when(repository.countCompletedBatches(
            null, null, null, null, null, null, null
        )).thenReturn(0L);
        when(repository.selectCompletedTypeCounts(null)).thenReturn(null);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);

            var result = service.completedBatches(query);

            assertThat(result.getRows()).isEmpty();
            assertThat(result.getTotal()).isZero();
            assertThat(result.getTypeCounts()).isEqualTo(new ImportBatchTypeCountsVo(0, 0, 0, 0));
        }
    }

    @Test
    void returnsOnlyVisibleCompletedBatchFilterOptions() {
        var syllabuses = List.of(new ImportBatchSyllabusOptionVo("21", "系统架构设计师 · 2026 考纲"));
        var uploaders = List.of(new ImportBatchUploaderOptionVo("7", "管理员"));
        when(repository.selectCompletedSyllabusOptions(7L)).thenReturn(syllabuses);
        when(repository.selectCompletedUploaderOptions(7L)).thenReturn(uploaders);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var result = service.filterOptions();

            assertThat(result.syllabusVersions()).containsExactlyElementsOf(syllabuses);
            assertThat(result.uploaders()).containsExactlyElementsOf(uploaders);
        }
    }

    @Test
    void calculatesDetailTotalFromValidAndFailedCounts() {
        var detail = new CompletedImportBatchDetailVo(
            "101",
            "source.jsonl",
            "knowledge_point",
            "21",
            "系统架构设计师 · 2026 考纲",
            "7",
            "管理员",
            "completed",
            OffsetDateTime.parse("2026-07-31T10:32:00+08:00"),
            95,
            3,
            5
        );
        when(repository.selectCompletedBatchDetail(101L, 7L)).thenReturn(detail);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var result = service.completedBatch("101");

            assertThat(result.totalCount()).isEqualTo(100);
            assertThat(result.validCount()).isEqualTo(95);
            assertThat(result.warningCount()).isEqualTo(3);
            assertThat(result.failedCount()).isEqualTo(5);
        }
    }

    @Test
    void returnsNotFoundForMissingOrUnauthorizedCompletedDetail() {
        when(repository.selectCompletedBatchDetail(101L, 7L)).thenReturn(null);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            assertThatThrownBy(() -> service.completedBatch("101"))
                .isInstanceOf(ImportException.class)
                .satisfies(error -> {
                    ImportException exception = (ImportException) error;
                    assertThat(exception.code()).isEqualTo(404);
                    assertThat(exception.errorCode()).isEqualTo("IMPORT_BATCH_NOT_FOUND");
                });
        }
    }

    @Test
    void rejectsUnsupportedTypeSortDirectionAndReversedDates() {
        var query = new CompletedImportBatchQueryBo();
        query.setImportType("document_chunk");
        assertFieldError(query, "importType");

        query = new CompletedImportBatchQueryBo();
        query.setOrderByColumn("fileName");
        assertFieldError(query, "orderByColumn");

        query = new CompletedImportBatchQueryBo();
        query.setIsAsc("ascending");
        assertFieldError(query, "isAsc");

        query = new CompletedImportBatchQueryBo();
        query.setCompletedStartDate(LocalDate.parse("2026-08-01"));
        query.setCompletedEndDate(LocalDate.parse("2026-07-31"));
        assertFieldError(query, "completedStartDate");
    }

    @Test
    void rejectsInvalidIdsAndPaginationWhenCalledOutsideMvcValidation() {
        var query = new CompletedImportBatchQueryBo();
        query.setSyllabusVersionId("0");
        assertFieldError(query, "syllabusVersionId");

        query = new CompletedImportBatchQueryBo();
        query.setUploaderId("abc");
        assertFieldError(query, "uploaderId");

        query = new CompletedImportBatchQueryBo();
        query.setPageNum(0);
        assertFieldError(query, "pageNum");

        query = new CompletedImportBatchQueryBo();
        query.setPageSize(101);
        assertFieldError(query, "pageSize");
    }

    private void assertFieldError(CompletedImportBatchQueryBo query, String field) {
        assertThatThrownBy(() -> service.completedBatches(query))
            .isInstanceOf(ImportException.class)
            .satisfies(error -> {
                ImportException exception = (ImportException) error;
                assertThat(exception.code()).isEqualTo(400);
                assertThat(exception.fieldErrors()).extracting("field").containsExactly(field);
            });
    }

    private static CompletedImportBatchVo completedRow() {
        return new CompletedImportBatchVo(
            "101",
            "source.jsonl",
            "knowledge_point",
            "21",
            "系统架构设计师 · 2026 考纲",
            "7",
            "管理员",
            "completed",
            OffsetDateTime.parse("2026-07-31T10:32:00+08:00")
        );
    }
}
