package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.TextbookChunkInsert;
import org.dromara.certmuse.catalog.domain.TextbookImportDocument;
import org.dromara.certmuse.catalog.domain.TextbookImportRecordData;
import org.dromara.certmuse.catalog.domain.TextbookKnowledgePointLookup;
import org.dromara.certmuse.catalog.domain.TextbookRecordResultUpdate;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.support.ImportJsonDocumentFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TextbookImportPersistenceTest {

    @Mock
    private ImportMapper repository;

    private JsonMapper mapper;
    private ImportPersistenceService service;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder().build();
        service = new ImportPersistenceService(repository, mapper, new ImportJsonDocumentFactory(mapper));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void persistsCreateModeAndWritesPerRowResult() throws Exception {
        arrangePersistence("create");

        service.persistTextbook(101L);

        verify(repository, never()).deleteTextbookImages(201L);
        verify(repository, never()).deleteTextbookKnowledgeRelations(201L);
        verify(repository, never()).deleteTextbookChunks(201L);
        ArgumentCaptor<List> chunkCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertTextbookChunks(chunkCaptor.capture());
        verify(repository).updateTextbookRecordResults(resultCaptor.capture());
        TextbookChunkInsert chunk = (TextbookChunkInsert) chunkCaptor.getValue().getFirst();
        TextbookRecordResultUpdate result = (TextbookRecordResultUpdate) resultCaptor.getValue().getFirst();
        assertThat(mapper.readTree(chunk.headingPath()).path("headings").get(0).textValue())
            .isEqualTo("Chapter 1");
        assertThat(mapper.readTree(chunk.sourceLocator()).path("source_page_start").intValue()).isEqualTo(13);
        assertThat(mapper.readTree(result.resultData()).path("object_type").textValue())
            .isEqualTo("document_chunk");
        assertThat(mapper.readTree(result.resultData()).path("root_ids").get(0).isTextual()).isTrue();
        verify(repository).completeTextbookImport(101L, 1);
        verify(repository).approveTextbookAfterImport(201L);
        verify(repository).publishTextbookAfterImport(201L);
        assertThat(ImportPersistenceService.class.getMethod("persistTextbook", long.class)
            .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void replacesDraftChildrenInRequiredOrder() {
        arrangePersistence("replace_draft");

        service.persistTextbook(101L);

        InOrder order = inOrder(repository);
        order.verify(repository).deleteTextbookImages(201L);
        order.verify(repository).deleteTextbookKnowledgeRelations(201L);
        order.verify(repository).deleteTextbookChunks(201L);
        order.verify(repository).insertTextbookChunks(anyList());
        order.verify(repository).insertTextbookKnowledgeRelations(anyList());
        order.verify(repository).updateTextbookRecordResults(anyList());
        order.verify(repository).completeTextbookImport(101L, 1);
    }

    @Test
    void qualificationScopedTextbookPersistsChunksWithoutKnowledgeRelations() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch("create"));
        when(repository.selectTextbookDocumentForUpdate(201L)).thenReturn(
            new TextbookImportDocument(201L, null, "Book", null, "pending_review", "0", 7L, 8L, 9L)
        );
        when(repository.selectPendingTextbookRecords(101L)).thenReturn(
            List.of(new TextbookImportRecordData(301L, rawRecord()))
        );
        when(repository.countDocumentChunks(201L)).thenReturn(0L);
        when(repository.insertTextbookChunks(anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
        when(repository.updateTextbookRecordResults(anyList()))
            .thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
        when(repository.completeTextbookImport(101L, 1)).thenReturn(1);
        when(repository.approveTextbookAfterImport(201L)).thenReturn(1);
        when(repository.publishTextbookAfterImport(201L)).thenReturn(1);

        service.persistTextbook(101L);

        verify(repository, never()).selectTextbookKnowledgePointLookups(anyList());
        verify(repository, never()).insertTextbookKnowledgeRelations(anyList());
        verify(repository).completeTextbookImport(101L, 1);
        verify(repository).approveTextbookAfterImport(201L);
        verify(repository).publishTextbookAfterImport(201L);
    }

    @Test
    void rejectsDocumentThatIsNoLongerPendingReviewBeforeDeletingOldContent() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch("replace_draft"));
        when(repository.selectTextbookDocumentForUpdate(201L)).thenReturn(
            new TextbookImportDocument(201L, 21L, "Book", null, "draft", "0", 7L, 8L)
        );

        assertThatThrownBy(() -> service.persistTextbook(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Textbook document is no longer pending review");

        verify(repository, never()).deleteTextbookImages(201L);
        verify(repository, never()).completeTextbookImport(101L, 1);
    }

    @Test
    void doesNotPublishWhenBatchCompletionFails() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch("create"));
        when(repository.selectTextbookDocumentForUpdate(201L)).thenReturn(
            new TextbookImportDocument(201L, 21L, "Book", null, "pending_review", "0", 7L, 8L)
        );
        when(repository.selectPendingTextbookRecords(101L)).thenReturn(
            List.of(new TextbookImportRecordData(301L, rawRecord()))
        );
        when(repository.selectTextbookKnowledgePointLookups(List.of(11L))).thenReturn(
            List.of(new TextbookKnowledgePointLookup(401L, 21L, 11L, "1.1"))
        );
        when(repository.countDocumentChunks(201L)).thenReturn(0L);
        when(repository.insertTextbookChunks(anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
        when(repository.insertTextbookKnowledgeRelations(anyList()))
            .thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
        when(repository.updateTextbookRecordResults(anyList()))
            .thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
        when(repository.completeTextbookImport(101L, 1)).thenReturn(0);

        assertThatThrownBy(() -> service.persistTextbook(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Textbook import batch completion failed");

        verify(repository, never()).approveTextbookAfterImport(201L);
        verify(repository, never()).publishTextbookAfterImport(201L);
    }

    private void arrangePersistence(String mode) {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch(mode));
        when(repository.selectTextbookDocumentForUpdate(201L)).thenReturn(
            new TextbookImportDocument(201L, 21L, "Book", null, "pending_review", "0", 7L, 8L)
        );
        when(repository.selectPendingTextbookRecords(101L)).thenReturn(
            List.of(new TextbookImportRecordData(301L, rawRecord()))
        );
        when(repository.selectTextbookKnowledgePointLookups(List.of(11L))).thenReturn(
            List.of(new TextbookKnowledgePointLookup(401L, 21L, 11L, "1.1"))
        );
        if ("create".equals(mode)) {
            when(repository.countDocumentChunks(201L)).thenReturn(0L);
        }
        when(repository.insertTextbookChunks(anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
        when(repository.insertTextbookKnowledgeRelations(anyList()))
            .thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
        when(repository.updateTextbookRecordResults(anyList()))
            .thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
        when(repository.completeTextbookImport(101L, 1)).thenReturn(1);
        when(repository.approveTextbookAfterImport(201L)).thenReturn(1);
        when(repository.publishTextbookAfterImport(201L)).thenReturn(1);
    }

    private static CmImportBatch batch(String mode) {
        return new CmImportBatch(
            101L,
            "upload-request",
            201L,
            null,
            null,
            "imports/document-chunk/101/source.jsonl",
            "file-hash",
            3_000_000L,
            "document_chunk",
            "document_chunk/1.0",
            "{\"schema_version\":\"import_parse_config/1.0\",\"import_type\":\"document_chunk\","
                + "\"mode\":\"" + mode + "\",\"title\":\"Book\",\"edition\":null,"
                + "\"subject_mappings\":[{\"subject_no\":1,\"exam_subject_id\":11}]}",
            "importing",
            "persist_document_chunks",
            BigDecimal.ZERO,
            1,
            0,
            0,
            0,
            null,
            OffsetDateTime.parse("2026-08-03T10:00:00+08:00"),
            null,
            null,
            7L,
            8L
        );
    }

    private static String rawRecord() {
        return "{\"schema_version\":\"document_chunk_raw_record/1.0\",\"record\":{"
            + "\"source_key\":\"chunk-1\",\"chunk_no\":1,\"heading\":\"Heading\","
            + "\"heading_path\":[\"Chapter 1\"],\"content\":\"Body\","
            + "\"page_start\":13,\"page_end\":13,"
            + "\"source_locator\":{\"markdown_line_start\":10,\"markdown_line_end\":12},"
            + "\"content_hash\":\"" + "a".repeat(64) + "\","
            + "\"knowledge_points\":[{\"subject_no\":1,\"code\":\"1.1\"}]}}";
    }
}
