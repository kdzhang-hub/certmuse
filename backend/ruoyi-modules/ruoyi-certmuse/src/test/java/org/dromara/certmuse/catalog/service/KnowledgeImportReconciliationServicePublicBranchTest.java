package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffInsert;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffRow;
import org.dromara.certmuse.catalog.domain.KnowledgeImportMatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.KnowledgeImportReconciliationService;
import org.dromara.certmuse.catalog.support.KnowledgeImportMatcher;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class KnowledgeImportReconciliationServicePublicBranchTest {

    private static final long BATCH_ID = 7L;
    private static final long SYLLABUS_ID = 21L;

    @Mock
    private ImportMapper repository;
    @Mock
    private KnowledgeImportMatcher matcher;

    private KnowledgeImportReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeImportReconciliationService(repository, matcher, JsonMapper.builder().build());
    }

    @Test
    void reconcileRejectsWrongImportTypeAndWrongStatusBeforeReadingTrees() {
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(
            batch(BATCH_ID, "question", "validating"),
            batch(BATCH_ID, "knowledge_point", "completed")
        );

        assertThatThrownBy(() -> service.reconcile(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Knowledge import batch is not ready for reconciliation");
        assertThatThrownBy(() -> service.reconcile(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Knowledge import batch is not ready for reconciliation");

        verify(repository, never()).selectCurrentKnowledgePoints(anyLong());
        verifyNoInteractions(matcher);
    }

    @Test
    void reconcilePersistsFieldUpdatesDepthMoveAndAmbiguousEvidence() {
        KnowledgeImportPointRow numberOld = point(201L, null, "1", "1.1", "编号", "说明", 1, 1, "active");
        KnowledgeImportPointRow numberNext = point(null, 101L, "1", "1.1a", "编号", "说明", 1, 1, "active");
        KnowledgeImportPointRow titleOld = point(202L, null, "1", "1.2", "旧标题", "说明", 1, 2, "active");
        KnowledgeImportPointRow titleNext = point(null, 102L, "1", "1.2", "新标题", "说明", 1, 2, "active");
        KnowledgeImportPointRow descriptionOld = point(203L, null, "1", "1.3", "描述", "旧说明", 1, 3, "active");
        KnowledgeImportPointRow descriptionNext = point(null, 103L, "1", "1.3", "描述", "新说明", 1, 3, "active");
        KnowledgeImportPointRow depthOld = point(204L, null, "1", "1.4", "深度", "说明", 1, 4, "active");
        KnowledgeImportPointRow depthNext = point(null, 104L, "1", "1.4", "深度", "说明", 2, 4, "active");
        KnowledgeImportPointRow sortOld = point(205L, null, "1", "1.5", "排序", "说明", 1, 5, "active");
        KnowledgeImportPointRow sortNext = point(null, 105L, "1", "1.5", "排序", "说明", 1, 6, "active");
        KnowledgeImportPointRow statusOld = point(206L, null, "1", "1.6", "状态", "说明", 1, 6, "active");
        KnowledgeImportPointRow statusNext = point(null, 106L, "1", "1.6", "状态", "说明", 1, 6, "disabled");
        List<KnowledgeImportPointRow> current = List.of(
            numberOld, titleOld, descriptionOld, depthOld, sortOld, statusOld
        );
        List<KnowledgeImportPointRow> incoming = List.of(
            numberNext, titleNext, descriptionNext, depthNext, sortNext, statusNext
        );

        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch(BATCH_ID, "knowledge_point", "validating"));
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(current);
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(incoming);
        when(matcher.match(incoming, current)).thenReturn(List.of(
            match(numberNext, numberOld, true),
            match(titleNext, titleOld, false),
            match(descriptionNext, descriptionOld, false),
            match(depthNext, depthOld, false),
            match(sortNext, sortOld, false),
            match(statusNext, statusOld, false)
        ));
        when(repository.insertKnowledgeImportDiffs(any())).thenAnswer(invocation ->
            ((List<?>) invocation.getArgument(0)).size()
        );
        when(repository.updateKnowledgeImportHashes(anyLong(), any(), any())).thenReturn(1);

        service.reconcile(BATCH_ID);

        ArgumentCaptor<List<KnowledgeImportDiffInsert>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertKnowledgeImportDiffs(rowsCaptor.capture());
        List<KnowledgeImportDiffInsert> rows = rowsCaptor.getValue();
        assertThat(rows).hasSize(6);
        assertThat(rows).filteredOn(row -> "update".equals(row.action())).hasSize(5);
        assertThat(rows).filteredOn(row -> "move".equals(row.action())).hasSize(1);
        assertThat(row(rows, 101L).changedFields()).contains("syllabusNumber");
        assertThat(row(rows, 102L).changedFields()).contains("syllabusTitle");
        assertThat(row(rows, 103L).changedFields()).contains("description");
        assertThat(row(rows, 104L).changedFields()).contains("treeDepth");
        assertThat(row(rows, 105L).changedFields()).contains("sortOrder");
        assertThat(row(rows, 106L).changedFields()).contains("status");
        assertThat(row(rows, 101L).matchEvidence())
            .contains("\"ambiguous\":true", "\"split_merge_conflict\":true");
    }

    @Test
    void reconcileSkipsBulkInsertForAnEmptyDifferenceSet() {
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch(BATCH_ID, "knowledge_point", "validating"));
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(List.of());
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of());
        when(matcher.match(List.of(), List.of())).thenReturn(List.of());
        when(repository.updateKnowledgeImportHashes(anyLong(), any(), any())).thenReturn(1);

        service.reconcile(BATCH_ID);

        verify(repository).deleteKnowledgeImportDiffs(BATCH_ID);
        verify(repository, never()).insertKnowledgeImportDiffs(any());
        verify(repository).updateKnowledgeImportHashes(anyLong(), any(), any());
    }

    @Test
    void reconcileRejectsAnIncompleteDifferenceInsert() {
        KnowledgeImportPointRow incoming = point(null, 101L, null, "1", "新增", null, 0, 1, "active");
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch(BATCH_ID, "knowledge_point", "validating"));
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(List.of());
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of(incoming));
        when(matcher.match(List.of(incoming), List.of())).thenReturn(List.of(match(incoming, null, false)));
        when(repository.insertKnowledgeImportDiffs(any())).thenReturn(0);

        assertThatThrownBy(() -> service.reconcile(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Knowledge import difference insert was incomplete");

        verify(repository, never()).updateKnowledgeImportHashes(anyLong(), any(), any());
    }

    @Test
    void reconcileRejectsMissingHashPersistence() {
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch(BATCH_ID, "knowledge_point", "validating"));
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(List.of());
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of());
        when(matcher.match(List.of(), List.of())).thenReturn(List.of());
        when(repository.updateKnowledgeImportHashes(anyLong(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.reconcile(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Knowledge import hashes were not persisted");
    }

    @Test
    void initializeEmptyBaselineRejectsWrongTypeAndWrongStatus() {
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(
            batch(BATCH_ID, "question", "validating"),
            batch(BATCH_ID, "knowledge_point", "completed")
        );

        assertThatThrownBy(() -> service.initializeEmptyBaseline(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Knowledge import batch is not ready for baseline initialization");
        assertThatThrownBy(() -> service.initializeEmptyBaseline(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Knowledge import batch is not ready for baseline initialization");

        verify(repository, never()).selectCurrentKnowledgePoints(anyLong());
    }

    @Test
    void initializeEmptyBaselineRejectsAConcurrentTreeCreation() {
        KnowledgeImportPointRow current = point(201L, null, null, "1", "已存在", null, 0, 1, "active");
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch(BATCH_ID, "knowledge_point", "validating"));
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(List.of(current));

        assertThatThrownBy(() -> service.initializeEmptyBaseline(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Knowledge tree is no longer empty and must be reconciled");

        verify(repository, never()).deleteKnowledgeImportDiffs(anyLong());
        verify(repository, never()).updateKnowledgeImportHashes(anyLong(), any(), any());
    }

    @Test
    void initializeEmptyBaselineRejectsMissingHashPersistence() {
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch(BATCH_ID, "knowledge_point", "validating"));
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(List.of());
        when(repository.updateKnowledgeImportHashes(anyLong(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.initializeEmptyBaseline(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Knowledge import baseline was not persisted");
    }

    @Test
    void publicHashActionsNormalizeNullValuesAndIgnoreInputOrder() {
        KnowledgeImportPointRow first = point(2L, null, null, null, null, null, 0, 1, null);
        KnowledgeImportPointRow second = point(1L, null, null, "1", "标题", "说明", 0, 2, "active");

        assertThat(service.baselineHash(List.of(first, second)))
            .isEqualTo(service.baselineHash(List.of(second, first)))
            .hasSize(64);

        KnowledgeImportDiffRow diff = new KnowledgeImportDiffRow();
        diff.setId(1L);
        diff.setAction(null);
        diff.setResolutionStatus(null);
        when(repository.selectAllKnowledgeImportDiffs(BATCH_ID)).thenReturn(List.of(diff));

        assertThat(service.currentResolutionHash(BATCH_ID)).hasSize(64);
    }

    @Test
    void reconcileTranslatesMetadataEncodingFailure() throws Exception {
        KnowledgeImportPointRow incoming = point(null, 101L, null, "1", "新增", null, 0, 1, "active");
        JsonMapper brokenMapper = mock(JsonMapper.class);
        KnowledgeImportReconciliationService brokenService =
            new KnowledgeImportReconciliationService(repository, matcher, brokenMapper);
        when(repository.selectByIdForUpdate(BATCH_ID)).thenReturn(batch(BATCH_ID, "knowledge_point", "validating"));
        when(repository.selectCurrentKnowledgePoints(SYLLABUS_ID)).thenReturn(List.of());
        when(repository.selectImportedKnowledgePoints(BATCH_ID)).thenReturn(List.of(incoming));
        when(matcher.match(List.of(incoming), List.of())).thenReturn(List.of(match(incoming, null, false)));
        when(brokenMapper.writeValueAsString(any())).thenThrow(new IllegalArgumentException("encoding failed"));

        assertThatThrownBy(() -> brokenService.reconcile(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unable to encode knowledge import metadata")
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    private static KnowledgeImportDiffInsert row(List<KnowledgeImportDiffInsert> rows, long importRecordId) {
        return rows.stream()
            .filter(row -> Long.valueOf(importRecordId).equals(row.importRecordId()))
            .findFirst()
            .orElseThrow();
    }

    private static KnowledgeImportMatch match(
        KnowledgeImportPointRow incoming,
        KnowledgeImportPointRow existing,
        boolean ambiguous
    ) {
        return new KnowledgeImportMatch(
            incoming, existing, 3500, 1500, 1000, 500, 500, 7000, 1000, false, ambiguous
        );
    }

    private static CmImportBatch batch(long id, String importType, String status) {
        return new CmImportBatch(
            id, "request-" + id, SYLLABUS_ID, null, "imports/source.jsonl", "hash", 10L,
            importType, "knowledge_point/1.0", "{}", status, "reconcile", BigDecimal.ZERO,
            0, 0, 0, null, OffsetDateTime.parse("2026-08-12T09:00:00+08:00"),
            null, null, 8L, 9L
        );
    }

    private static KnowledgeImportPointRow point(
        Long id,
        Long importRecordId,
        String parentNumber,
        String number,
        String title,
        String description,
        int depth,
        int sortOrder,
        String status
    ) {
        KnowledgeImportPointRow row = new KnowledgeImportPointRow();
        row.setKnowledgePointId(id);
        row.setImportRecordId(importRecordId);
        row.setExamSubjectId(11L);
        row.setParentSyllabusNumber(parentNumber);
        row.setSyllabusNumber(number);
        row.setSyllabusTitle(title);
        row.setDescription(description);
        row.setTreeDepth(depth);
        row.setSortOrder(sortOrder);
        row.setStatus(status);
        row.setRowVersion(1L);
        return row;
    }
}
