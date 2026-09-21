package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffInsert;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffRow;
import org.dromara.certmuse.catalog.domain.KnowledgeImportMatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.KnowledgeImportReconciliationService;
import org.dromara.certmuse.catalog.support.KnowledgeImportMatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class KnowledgeImportReconciliationServiceTest {

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
    void reconcilesUnchangedMovedAddedAndDeletedPointsWithExplainableMetadata() {
        CmImportBatch batch = batch();
        KnowledgeImportPointRow unchangedIncoming = point(null, 101L, 11L, null, "1", "根节点", "说明", 0, 1);
        KnowledgeImportPointRow unchangedOld = point(201L, null, 11L, null, "1", "根节点", "说明", 0, 1);
        KnowledgeImportPointRow movedIncoming = point(null, 102L, 11L, "1", "1.1", "移动节点", "旧说明", 1, 1);
        KnowledgeImportPointRow movedOld = point(202L, null, 11L, "9", "1.1", "移动节点", "旧说明", 1, 1);
        KnowledgeImportPointRow addedIncoming = point(null, 103L, 11L, "1", "1.2", "新增节点", "新增说明", 1, 2);
        KnowledgeImportPointRow deletedOld = point(203L, null, 11L, null, "2", "已删除节点", "删除说明", 0, 2);

        when(repository.selectByIdForUpdate(7L)).thenReturn(batch);
        when(repository.selectCurrentKnowledgePoints(21L)).thenReturn(List.of(unchangedOld, movedOld, deletedOld));
        when(repository.selectImportedKnowledgePoints(7L)).thenReturn(
            List.of(unchangedIncoming, movedIncoming, addedIncoming));
        when(matcher.match(any(), any())).thenReturn(List.of(
            new KnowledgeImportMatch(unchangedIncoming, unchangedOld, 4500, 2500, 1500, 1000, 500,
                10000, 2000, false, false),
            new KnowledgeImportMatch(movedIncoming, movedOld, 4500, 0, 1500, 0, 500,
                6500, 1500, true, false),
            new KnowledgeImportMatch(addedIncoming, null, 0, 0, 0, 0, 0,
                0, 0, false, false)
        ));
        when(repository.insertKnowledgeImportDiffs(any())).thenAnswer(invocation ->
            ((List<?>) invocation.getArgument(0)).size());
        when(repository.updateKnowledgeImportHashes(anyLong(), any(), any())).thenReturn(1);

        service.reconcile(7L);

        ArgumentCaptor<List<KnowledgeImportDiffInsert>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertKnowledgeImportDiffs(captor.capture());
        List<KnowledgeImportDiffInsert> rows = captor.getValue();
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(KnowledgeImportDiffInsert::action)
            .containsExactlyInAnyOrder("unchanged", "move", "add", "delete");
        assertThat(rows).filteredOn(row -> "unchanged".equals(row.action())).singleElement()
            .satisfies(row -> {
                assertThat(row.resolutionStatus()).isEqualTo("not_required");
                assertThat(row.confirmedKnowledgePointId()).isEqualTo(201L);
            });
        assertThat(rows).filteredOn(row -> "delete".equals(row.action())).singleElement()
            .satisfies(row -> assertThat(row.changedFields()).contains("deleted"));
        assertThat(rows).filteredOn(row -> "move".equals(row.action())).singleElement()
            .satisfies(row -> assertThat(row.changedFields()).contains("parent"));
        assertThat(rows).filteredOn(row -> "add".equals(row.action())).singleElement()
            .satisfies(row -> assertThat(row.matchEvidence()).contains("knowledge_import_match_evidence/1.0"));
        verify(repository).deleteKnowledgeImportDiffs(7L);
        verify(repository).updateKnowledgeImportHashes(anyLong(), any(), any());
    }

    @Test
    void initializesAndHashesAnEmptyBaselineForTheFirstImport() {
        when(repository.selectByIdForUpdate(7L)).thenReturn(batch());
        when(repository.selectCurrentKnowledgePoints(21L)).thenReturn(List.of());
        when(repository.updateKnowledgeImportHashes(anyLong(), any(), any())).thenReturn(1);

        service.initializeEmptyBaseline(7L);

        verify(repository).deleteKnowledgeImportDiffs(7L);
        verify(repository).updateKnowledgeImportHashes(eq(7L), any(), any());
        assertThat(service.baselineHash(List.of())).hasSize(64);
    }

    @Test
    void computesResolutionHashFromPersistedRowsIndependentOfTheirOrder() {
        KnowledgeImportDiffRow first = diffRow(2L, "add", "pending", 12L, null);
        KnowledgeImportDiffRow second = diffRow(1L, "delete", "manual_confirmed", null, 201L);
        when(repository.selectAllKnowledgeImportDiffs(7L)).thenReturn(List.of(first, second));

        String actual = service.currentResolutionHash(7L);
        when(repository.selectAllKnowledgeImportDiffs(7L)).thenReturn(List.of(second, first));

        assertThat(service.currentResolutionHash(7L)).isEqualTo(actual);
    }

    private static CmImportBatch batch() {
        return new CmImportBatch(
            7L, "request-7", 21L, null, "imports/source.jsonl", "hash", 10L,
            "knowledge_point", "knowledge_point/1.0", "{}", "validating", "reconcile",
            BigDecimal.ZERO, 0, 0, 0, null,
            OffsetDateTime.parse("2026-08-10T09:00:00+08:00"), null, null, 8L, 9L
        );
    }

    private static KnowledgeImportPointRow point(Long id, Long importRecordId, long subjectId,
                                                  String parentNumber, String number, String title,
                                                  String description, int depth, int sortOrder) {
        KnowledgeImportPointRow row = new KnowledgeImportPointRow();
        row.setKnowledgePointId(id);
        row.setImportRecordId(importRecordId);
        row.setExamSubjectId(subjectId);
        row.setParentSyllabusNumber(parentNumber);
        row.setSyllabusNumber(number);
        row.setSyllabusTitle(title);
        row.setDescription(description);
        row.setTreeDepth(depth);
        row.setSortOrder(sortOrder);
        row.setStatus("active");
        row.setRowVersion(1L);
        return row;
    }

    private static KnowledgeImportDiffRow diffRow(long id, String action, String status,
                                                   Long importRecordId, Long confirmedId) {
        KnowledgeImportDiffRow row = new KnowledgeImportDiffRow();
        row.setId(id);
        row.setAction(action);
        row.setResolutionStatus(status);
        row.setImportRecordId(importRecordId);
        row.setConfirmedKnowledgePointId(confirmedId);
        return row;
    }
}
