package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.service.ImportProgressPublisher;
import org.dromara.certmuse.catalog.service.impl.KnowledgeImportReconciliationService;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.validation.KnowledgePointValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportWorkerTest {
    @Mock ImportMapper repository;
    @Mock ImportStorage storage;
    @Mock ImportProgressPublisher progressPublisher;
    @Mock KnowledgeImportReconciliationService reconciliationService;
    private ImportWorker worker;

    @BeforeEach
    void setUp() {
        JsonMapper objectMapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
        worker = new ImportWorker(
            repository, storage, new KnowledgePointValidator(), objectMapper, new ImportJsonDocumentFactory(objectMapper),
            null,
            progressPublisher
        );
    }

    @Test
    void validStreamsJsonlValidatesRowsAndFinishesTheBatch() {
        arrange(validJson() + "\n");
        when(repository.updateStage(101L, "parsing", "validating", "validate_schema", new BigDecimal("30.00"))).thenReturn(1);

        worker.validate(101L);

        verify(repository).insertRecord(anyLong(), eq(101L), eq(1), eq("knowledge-tree-v7:1:1.1"), any(), any());
        verify(repository).markRecord(anyLong(), eq("success"));
        verify(repository).finish(101L, "validating");
    }

    @Test
    void invalidRecordsMalformedDuplicateAndNonObjectJsonWithoutPersistingRows() {
        arrange("{bad}\n{\"a\":1,\"a\":2}\n[]\n");
        when(repository.updateStage(101L, "parsing", "validating", "validate_schema", new BigDecimal("30.00"))).thenReturn(1);

        worker.validate(101L);

        verify(repository, never()).insertRecord(anyLong(), anyLong(), any(Integer.class), any(), any(), any());
        verify(repository, org.mockito.Mockito.atLeast(3)).createIssue(eq(101L), eq(null), any(), any(), eq("error"), any());
        verify(repository).finish(101L, "validating");
    }

    @Test
    void invalidWrapsMissingBatchAndStorageFailuresForDispatcherRetry() {
        when(repository.selectById(101L)).thenReturn(null);

        assertThatThrownBy(() -> worker.validate(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Knowledge point precheck failed");
    }

    @Test
    void retriesInsteadOfLeavingTheBatchAtNinetyNinePercentWhenCompletionLosesItsStateGuard() {
        arrange(validJson() + "\n");
        when(repository.updateStage(101L, "parsing", "validating", "validate_schema", new BigDecimal("30.00"))).thenReturn(1);
        when(repository.finish(101L, "validating")).thenReturn(0);

        assertThatThrownBy(() -> worker.validate(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Knowledge point precheck failed")
            .hasRootCauseMessage("Knowledge point precheck completion failed");
    }

    @Test
    void edgeFinishesAnEmptyFileAtAllStageUpperBounds() {
        arrange("");
        when(repository.updateStage(101L, "parsing", "validating", "validate_schema", new BigDecimal("30.00"))).thenReturn(1);

        worker.validate(101L);

        verify(repository).updateProgress(101L, "validating", "validate_schema", BigDecimal.valueOf(50));
        verify(repository).updateProgress(101L, "validating", "validate_mapping", BigDecimal.valueOf(65));
        verify(repository).finish(101L, "validating");
    }

    @Test
    void edgeStopsWhenAnotherWorkerAlreadyMovedTheBatchStage() {
        arrange(validJson());
        when(repository.updateStage(101L, "parsing", "validating", "validate_schema", new BigDecimal("30.00"))).thenReturn(0);

        worker.validate(101L);

        verify(repository, never()).finish(anyLong(), any());
        verify(repository, never()).markRecord(anyLong(), any());
    }

    @Test
    void firstImportInitializesAnEmptyBaselineWithoutCreatingDiffs() {
        arrange(validJson());
        when(repository.updateStage(101L, "parsing", "validating", "validate_schema", new BigDecimal("30.00"))).thenReturn(1);
        when(repository.countIssues(101L, "error", null)).thenReturn(0L);
        when(repository.countKnowledgePoints(21L)).thenReturn(0L);
        JsonMapper objectMapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        worker = new ImportWorker(
            repository, storage, new KnowledgePointValidator(), objectMapper, new ImportJsonDocumentFactory(objectMapper),
            reconciliationService, progressPublisher
        );

        worker.validate(101L);

        verify(reconciliationService).initializeEmptyBaseline(101L);
        verify(reconciliationService, never()).reconcile(101L);
    }

    @SuppressWarnings("unchecked")
    private void arrange(String content) {
        when(repository.selectById(101L)).thenReturn(batch(content.getBytes(StandardCharsets.UTF_8).length));
        org.mockito.Mockito.lenient().when(repository.finish(101L, "validating")).thenReturn(1);
        doAnswer(invocation -> {
            Function<java.io.InputStream, Object> reader = invocation.getArgument(1);
            return reader.apply(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        }).when(storage).read(eq("imports/source.jsonl"), any());
    }

    private static CmImportBatch batch(long size) {
        return new CmImportBatch(101L, "r1", 21L, null, "imports/source.jsonl", "hash", Math.max(1, size),
            "knowledge_point", "knowledge_point/1.0", "{\"subject_mappings\":[{\"subject_no\":1}]}",
            "parsing", "read_jsonl", BigDecimal.ZERO, 0, 0, 0, null,
            OffsetDateTime.parse("2026-08-03T09:00:00+08:00"), null, null, 7L, 8L);
    }

    private static String validJson() {
        return "{\"schema_version\":\"1.0\",\"source_key\":\"knowledge-tree-v7:1:1.1\",\"subject_no\":1,"
            + "\"syllabus_number\":\"1.1\",\"syllabus_title\":\"标题\",\"parent_syllabus_number\":null,"
            + "\"tree_depth\":1,\"sort_order\":1,\"description\":null,\"importance\":2,"
            + "\"diagnostic_enabled\":false,\"recommendation_enabled\":false,\"status\":\"0\"}";
    }
}
