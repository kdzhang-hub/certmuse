package org.dromara.certmuse.catalog.service;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.certmuse.catalog.domain.TextbookRows;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkQueryBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkUpdateBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookQueryBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookUpdateBo;
import org.dromara.certmuse.catalog.domain.vo.TextbookListVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookOptionVo;
import org.dromara.certmuse.catalog.mapper.TextbookMapper;
import org.dromara.certmuse.catalog.service.impl.TextbookServiceImpl;
import org.dromara.certmuse.catalog.support.TextbookException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class TextbookServiceBehaviorTest {
    private final TextbookMapper mapper = mock(TextbookMapper.class);
    private final TextbookServiceImpl service = new TextbookServiceImpl(mapper, JsonMapper.builder().build());

    @Test
    void listsDraftBooksAndExposesFilterOptions() {
        TextbookListVo row = new TextbookListVo("1", "10", "20", "2026", "架构师", "教材",
            "第1版", "draft", 2L, 1L, 1L, 3L, null, null, "7", "管理员", null, null, null, null);
        TextbookQueryBo query = new TextbookQueryBo();
        query.setPageNum(2);
        query.setPageSize(10);
        when(mapper.selectTextbooks(any(), eq(null), eq(null), eq(null), eq(null), eq(10), eq(10L)))
            .thenReturn(List.of(row));
        when(mapper.countTextbooks(any(), eq(null), eq(null), eq(null), eq(null))).thenReturn(1L);
        when(mapper.selectCertificationOptions(null)).thenReturn(List.of(new TextbookOptionVo("1", "架构师")));
        when(mapper.selectCreatorOptions(null)).thenReturn(List.of(new TextbookOptionVo("7", "管理员")));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            stp.when(() -> StpUtil.hasPermission("certmuse:catalog:resource:remove")).thenReturn(true);

            var page = service.list(query);
            var options = service.options();

            assertThat(page.getRows()).singleElement().satisfies(item -> {
                assertThat(item.deletable()).isTrue();
                assertThat(item.deleteDisabledReason()).isNull();
            });
            assertThat(page.getTotal()).isEqualTo(1L);
            assertThat(options.certifications()).containsExactly(new TextbookOptionVo("1", "架构师"));
            assertThat(options.creators()).containsExactly(new TextbookOptionVo("7", "管理员"));
            assertThat(options.statuses()).hasSize(6);
        }
    }

    @Test
    void mapsDetailAndChunkDataFromJsonProjections() {
        TextbookRows.Detail book = book("draft");
        TextbookRows.Chunk chunk = chunk("draft");
        when(mapper.selectTextbook(1L, null)).thenReturn(book);
        when(mapper.selectChunk(1L, 2L)).thenReturn(chunk);
        when(mapper.selectKnowledgeScope(30L)).thenReturn(new TextbookRows.Scope(20L, 10L));
        when(mapper.selectChunks(1L, 30L, null, true, true, "正文", 20, 0L)).thenReturn(List.of(chunk));
        when(mapper.countChunks(1L, 30L, null, true, true, "正文")).thenReturn(1L);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            stp.when(() -> StpUtil.hasPermission("certmuse:catalog:resource:remove")).thenReturn(true);
            stp.when(() -> StpUtil.hasPermission("certmuse:catalog:resource:edit")).thenReturn(true);

            var detail = service.detail("1");
            var chunks = new TextbookChunkQueryBo();
            chunks.setKnowledgePointId("30");
            chunks.setIncludeDescendants(true);
            chunks.setHasKnowledgePoint(true);
            chunks.setKeyword("  正文 ");
            var page = service.chunks("1", chunks);
            var chunkDetail = service.chunkDetail("1", "2");

            assertThat(detail.statistics().chunkCount()).isEqualTo(2L);
            assertThat(detail.latestImportBatch().id()).isEqualTo("9");
            assertThat(detail.deletable()).isTrue();
            assertThat(page.getRows()).singleElement().satisfies(item -> {
                assertThat(item.headingPath()).containsExactly("第一章", "第一节");
                assertThat(item.sourceLocator().sourceType()).isEqualTo("pdf");
                assertThat(item.knowledgePoints()).hasSize(1);
                assertThat(item.mapped()).isTrue();
            });
            assertThat(chunkDetail.editable()).isTrue();
            assertThat(chunkDetail.knowledgePoints()).hasSize(1);
        }
    }

    @Test
    void updatesChunkContentAndReplacesKnowledgeBindings() {
        TextbookRows.Detail book = book("draft");
        TextbookRows.Chunk chunk = chunk("draft");
        when(mapper.lockTextbook(1L, null)).thenReturn(1L);
        when(mapper.selectTextbook(1L, null)).thenReturn(book);
        when(mapper.updateChunk(eq(1L), eq(2L), any(), eq("新标题"), eq("新正文"), any(), eq(7L))).thenReturn(1);
        when(mapper.countKnowledgePoints(20L, List.of(30L, 31L))).thenReturn(2L);
        when(mapper.selectChunk(1L, 2L)).thenReturn(chunk);

        TextbookChunkUpdateBo command = new TextbookChunkUpdateBo();
        command.setHeading("  新标题 ");
        command.setContent("新正文");
        command.setUpdateTime(OffsetDateTime.parse("2026-08-10T10:00:00+08:00"));
        command.setKnowledgePointIds(List.of("30", "31", "30"));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            login.when(LoginHelper::getUserId).thenReturn(7L);
            stp.when(() -> StpUtil.hasPermission("certmuse:catalog:resource:edit")).thenReturn(true);
            stp.when(() -> StpUtil.hasPermission("certmuse:catalog:resource:remove")).thenReturn(true);

            assertThat(service.updateChunk("1", "2", command).id()).isEqualTo("2");
        }
        verify(mapper).deleteChunkKnowledgeByChunk(2L);
        verify(mapper).insertChunkKnowledge(any());
    }

    @Test
    void updatesDraftOrPublishedTextbookMetadataAndBlocksMappedCertificationChanges() {
        TextbookUpdateBo command = new TextbookUpdateBo();
        command.setTitle("  新教材名称  ");
        command.setCertificationId("11");
        when(mapper.lockTextbook(1L, null)).thenReturn(1L);
        when(mapper.selectTextbook(1L, null)).thenReturn(book("draft"));
        when(mapper.selectActiveCertification(11L)).thenReturn(11L);
        when(mapper.countTextbookKnowledgeMappings(1L)).thenReturn(0L);
        when(mapper.updateTextbook(1L, "新教材名称", 11L, true, 7L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            login.when(LoginHelper::getUserId).thenReturn(7L);
            service.updateTextbook("1", command);
            verify(mapper).updateTextbook(1L, "新教材名称", 11L, true, 7L);

            when(mapper.selectTextbook(1L, null)).thenReturn(book("published"));
            service.updateTextbook("1", command);

            when(mapper.countTextbookKnowledgeMappings(1L)).thenReturn(1L);
            assertThatThrownBy(() -> service.updateTextbook("1", command))
                .isInstanceOfSatisfying(TextbookException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo("TEXTBOOK_CERTIFICATION_CHANGE_BLOCKED"));

            TextbookUpdateBo titleOnly = new TextbookUpdateBo();
            titleOnly.setTitle("已发布教材名称");
            titleOnly.setCertificationId("10");
            when(mapper.updateTextbook(1L, "已发布教材名称", 10L, false, 7L)).thenReturn(1);
            service.updateTextbook("1", titleOnly);
            verify(mapper).updateTextbook(1L, "已发布教材名称", 10L, false, 7L);
        }
    }

    @Test
    void publishesDraftAndTakesPublishedTextbookOfflineWithStrictStateChecks() {
        when(mapper.lockTextbook(1L, null)).thenReturn(1L);
        when(mapper.selectTextbook(1L, null)).thenReturn(book("draft"));
        when(mapper.publishTextbook(1L, 7L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            login.when(LoginHelper::getUserId).thenReturn(7L);
            service.publishTextbook("1");
            verify(mapper).publishTextbook(1L, 7L);

            when(mapper.selectTextbook(1L, null)).thenReturn(book("published"));
            when(mapper.takeTextbookOffline(1L, 7L)).thenReturn(1);
            service.takeTextbookOffline("1");
            verify(mapper).takeTextbookOffline(1L, 7L);

            when(mapper.selectTextbook(1L, null)).thenReturn(book("published"));
            assertThatThrownBy(() -> service.publishTextbook("1"))
                .isInstanceOfSatisfying(TextbookException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo("TEXTBOOK_PUBLISH_FORBIDDEN"));

            when(mapper.selectTextbook(1L, null)).thenReturn(book("draft"));
            assertThatThrownBy(() -> service.takeTextbookOffline("1"))
                .isInstanceOfSatisfying(TextbookException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo("TEXTBOOK_OFFLINE_FORBIDDEN"));
        }
    }

    @Test
    void deletesChunksAndDraftBooksOnlyAfterOwnershipChecks() {
        TextbookRows.Detail book = book("draft");
        when(mapper.lockTextbook(1L, null)).thenReturn(1L);
        when(mapper.selectTextbook(1L, null)).thenReturn(book);
        when(mapper.countChunksForDelete(1L, List.of(2L, 3L))).thenReturn(2L);
        when(mapper.deleteChunks(1L, List.of(2L, 3L))).thenReturn(2);
        when(mapper.softDeleteTextbook(1L, 7L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            login.when(LoginHelper::getUserId).thenReturn(7L);
            service.deleteChunks("1", List.of("2, 3"));
            service.deleteTextbook("1");
        }

        verify(mapper).deleteImages(1L, List.of(2L, 3L));
        verify(mapper).deleteChunkKnowledge(1L, List.of(2L, 3L));
        verify(mapper).deleteChunks(1L, List.of(2L, 3L));
        verify(mapper).softDeleteTextbook(1L, 7L);
    }

    @Test
    void rejectsInvalidChunkScopesAndProtectsNonDraftMutations() {
        TextbookRows.Detail draft = book("draft");
        when(mapper.selectTextbook(1L, null)).thenReturn(draft);

        try (MockedStatic<LoginHelper> login = org.mockito.Mockito.mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            assertThatThrownBy(() -> service.chunks("1", new TextbookChunkQueryBo()))
                .isInstanceOfSatisfying(TextbookException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_CHUNK_SCOPE"));

            TextbookChunkQueryBo wrongVersion = new TextbookChunkQueryBo();
            wrongVersion.setKnowledgePointId("30");
            when(mapper.selectKnowledgeScope(30L)).thenReturn(new TextbookRows.Scope(999L, 10L));
            assertThatThrownBy(() -> service.chunks("1", wrongVersion))
                .isInstanceOfSatisfying(TextbookException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo("KNOWLEDGE_POINT_VERSION_MISMATCH"));

            TextbookChunkQueryBo wrongSubject = new TextbookChunkQueryBo();
            wrongSubject.setExamSubjectId("99");
            when(mapper.selectSubjectSyllabus(1L, 99L)).thenReturn(null);
            assertThatThrownBy(() -> service.chunks("1", wrongSubject))
                .isInstanceOfSatisfying(TextbookException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_CHUNK_SCOPE"));

            when(mapper.lockTextbook(1L, null)).thenReturn(1L);
            when(mapper.selectTextbook(1L, null)).thenReturn(book("published"));
            TextbookChunkUpdateBo update = new TextbookChunkUpdateBo();
            update.setContent("正文");
            assertThatThrownBy(() -> service.updateChunk("1", "2", update))
                .isInstanceOfSatisfying(TextbookException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo("CHUNK_EDIT_FORBIDDEN"));
            assertThatThrownBy(() -> service.deleteTextbook("1"))
                .isInstanceOfSatisfying(TextbookException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo("TEXTBOOK_STATUS_DELETE_FORBIDDEN"));
        }
    }

    @Test
    void rejectsEmptyAndForeignChunkDeletionBatches() {
        when(mapper.lockTextbook(1L, null)).thenReturn(1L);
        when(mapper.selectTextbook(1L, null)).thenReturn(book("draft"));

        try (MockedStatic<LoginHelper> login = org.mockito.Mockito.mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            assertThatThrownBy(() -> service.deleteChunks("1", List.of(" ", "")))
                .isInstanceOfSatisfying(TextbookException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo("CHUNK_REQUEST_INVALID"));

            when(mapper.countChunksForDelete(1L, List.of(2L, 3L))).thenReturn(1L);
            assertThatThrownBy(() -> service.deleteChunks("1", List.of("2,3")))
                .isInstanceOfSatisfying(TextbookException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo("CHUNK_BATCH_DELETE_BLOCKED"));
        }
    }

    private static TextbookRows.Detail book(String status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-10T10:00:00+08:00");
        return new TextbookRows.Detail(1L, 20L, 10L, "2026", "教材", "第1版", status, 7L, "管理员",
            now, now, "提交人", now, "审核人", now, "意见", "发布人", now,
            2L, 1L, 1L, 3L, 4L, 9L, "教材.jsonl", 100L, "hash", "completed", 10, 1, 0, now, now);
    }

    private static TextbookRows.Chunk chunk(String status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-10T10:00:00+08:00");
        return new TextbookRows.Chunk(2L, 1L, 1, "标题", "正文内容",
            "{\"headings\":[\"第一章\",\"第一节\"]}",
            "{\"source_type\":\"pdf\",\"source_key\":\"page-1\",\"line_start\":1,\"line_end\":2,\"page_start\":3,\"page_end\":4}",
            "content-hash", "[{\"id\":\"30\",\"subjectNo\":1,\"code\":\"K1\",\"title\":\"知识点\"}]", now, status);
    }
}
