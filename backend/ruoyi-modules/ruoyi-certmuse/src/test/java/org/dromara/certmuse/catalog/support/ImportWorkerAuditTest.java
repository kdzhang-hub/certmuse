package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.service.ImportProgressPublisher;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.validation.KnowledgePointValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportWorkerAuditTest {

    @Mock
    private ImportMapper repository;
    @Mock
    private ImportStorage storage;
    @Mock
    private KnowledgePointValidator validator;
    @Mock
    private KnowledgePointValidator.Row row;
    @Mock
    private ImportProgressPublisher progressPublisher;

    @Test
    void wrapsKnowledgePointRawRecordWithoutRequiringInputAuditFields() throws Exception {
        JsonMapper objectMapper = JsonMapper.builder().build();
        ImportWorker worker = new ImportWorker(
            repository,
            storage,
            validator,
            objectMapper,
            new ImportJsonDocumentFactory(objectMapper),
            null,
            progressPublisher
        );
        when(repository.selectById(101L)).thenReturn(batch());
        when(repository.updateStage(anyLong(), anyString(), anyString(), anyString(), any(BigDecimal.class))).thenReturn(1);
        when(repository.finish(101L, "validating")).thenReturn(1);
        when(validator.validateSchema(anyLong(), eq(1), any(), any())).thenReturn(row);
        when(row.safeSourceKey()).thenReturn("knowledge-tree-v7:1:1.1");
        when(row.problems()).thenReturn(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<InputStream, List<KnowledgePointValidator.Row>> reader = invocation.getArgument(1);
            return reader.apply(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        }).when(storage).read(eq("imports/knowledge.jsonl"), any());

        worker.validate(101L);

        ArgumentCaptor<String> rawRecord = ArgumentCaptor.forClass(String.class);
        verify(repository).insertRecord(anyLong(), eq(101L), eq(1), eq("knowledge-tree-v7:1:1.1"), rawRecord.capture(), anyString());
        var raw = objectMapper.readTree(rawRecord.getValue());
        assertThat(raw.path("schema_version").asText()).isEqualTo("knowledge_point_raw/1.0");
        assertThat(raw.path("payload").isObject()).isTrue();
    }

    private static CmImportBatch batch() {
        return new CmImportBatch(
            101L, "request-1", 21L, null, "imports/knowledge.jsonl", "hash", 2L,
            "knowledge_point", "knowledge-point-jsonl/1.0",
            "{\"subject_mappings\":[{\"subject_no\":1}]}", "parsing", "read_jsonl", BigDecimal.ZERO,
            0, 0, 0, null, null, null, null, 1L, 1L
        );
    }
}
