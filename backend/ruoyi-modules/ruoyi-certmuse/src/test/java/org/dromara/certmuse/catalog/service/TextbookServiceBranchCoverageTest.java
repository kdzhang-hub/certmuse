package org.dromara.certmuse.catalog.service;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.certmuse.catalog.domain.TextbookRows;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkQueryBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkUpdateBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookQueryBo;
import org.dromara.certmuse.catalog.domain.vo.TextbookListVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookOptionVo;
import org.dromara.certmuse.catalog.mapper.TextbookMapper;
import org.dromara.certmuse.catalog.service.impl.TextbookServiceImpl;
import org.dromara.certmuse.catalog.support.TextbookException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TextbookServiceBranchCoverageTest {

    @Mock
    private TextbookMapper mapper;

    private TextbookServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TextbookServiceImpl(mapper, JsonMapper.builder().build());
    }

    @Test
    void listNormalizesFiltersAndAppliesOrdinaryUserVisibility() {
        TextbookQueryBo query = new TextbookQueryBo();
        query.setTitle("  教材  ");
        query.setEdition("  第一版  ");
        query.setStatus(" published ");
        query.setCertificationId("1");
        query.setSyllabusVersionId("2");
        query.setCreateBy("3");
        query.setOrderByColumn(" title ");
        query.setIsAsc(" ASC ");
        query.setPageNum(2);
        query.setPageSize(5);
        TextbookListVo row = listRow("published");
        when(mapper.selectTextbooks(query, 1L, 2L, 3L, 7L, 5, 5L)).thenReturn(List.of(row));
        when(mapper.countTextbooks(query, 1L, 2L, 3L, 7L)).thenReturn(1L);

        try (MockedStatic<LoginHelper> login = ordinaryUser();
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("certmuse:catalog:resource:remove")).thenReturn(false);

            var result = service.list(query);

            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getRows()).singleElement().satisfies(item -> {
                assertThat(item.deletable()).isFalse();
                assertThat(item.deleteDisabledReason()).isEqualTo("仅草稿教材可删除");
            });
            assertThat(query.getTitle()).isEqualTo("教材");
            assertThat(query.getEdition()).isEqualTo("第一版");
            assertThat(query.getOrderByColumn()).isEqualTo("title");
        }
    }

    @Test
    void listRejectsInvalidLengthsStatusDatesSortDirectionIdsAndPagination() {
        TextbookQueryBo title = new TextbookQueryBo();
        title.setTitle("x".repeat(501));
        assertError(() -> service.list(title), 400, "TEXTBOOK_REQUEST_INVALID");

        TextbookQueryBo edition = new TextbookQueryBo();
        edition.setEdition("x".repeat(101));
        assertError(() -> service.list(edition), 400, "TEXTBOOK_REQUEST_INVALID");

        TextbookQueryBo status = new TextbookQueryBo();
        status.setStatus("deleted");
        assertError(() -> service.list(status), 400, "TEXTBOOK_REQUEST_INVALID");

        TextbookQueryBo dates = new TextbookQueryBo();
        dates.setBeginCreateTime(time().plusDays(1));
        dates.setEndCreateTime(time());
        assertError(() -> service.list(dates), 400, "TEXTBOOK_REQUEST_INVALID");

        TextbookQueryBo direction = new TextbookQueryBo();
        direction.setIsAsc("ascending");
        assertError(() -> service.list(direction), 400, "TEXTBOOK_REQUEST_INVALID");

        TextbookQueryBo identifier = new TextbookQueryBo();
        identifier.setCreateBy("0");
        assertError(() -> service.list(identifier), 400, "TEXTBOOK_REQUEST_INVALID");

        TextbookQueryBo page = new TextbookQueryBo();
        page.setPageNum(0);
        assertError(() -> service.list(page), 400, "TEXTBOOK_REQUEST_INVALID");
    }

    @Test
    void optionsAreLimitedToTheOrdinaryUsersVisibleResources() {
        var certifications = List.of(new TextbookOptionVo("1", "系统架构设计师"));
        var creators = List.of(new TextbookOptionVo("7", "管理员"));
        when(mapper.selectCertificationOptions(7L)).thenReturn(certifications);
        when(mapper.selectCreatorOptions(7L)).thenReturn(creators);

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            var result = service.options();

            assertThat(result.certifications()).containsExactlyElementsOf(certifications);
            assertThat(result.creators()).containsExactlyElementsOf(creators);
            assertThat(result.statuses()).extracting("value")
                .containsExactly("draft", "pending_review", "approved", "rejected", "published", "offline");
        }
    }

    @Test
    void detailRejectsMalformedAndInvisibleIdentifiers() {
        assertError(() -> service.detail("0"), 400, "TEXTBOOK_REQUEST_INVALID");
        when(mapper.selectTextbook(1L, 7L)).thenReturn(null);

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            assertError(() -> service.detail("1"), 404, "TEXTBOOK_NOT_FOUND");
        }
    }

    @Test
    void detailHandlesQualificationScopedDraftWithoutImportOrDeletePermission() {
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("draft", null, null, null));

        try (MockedStatic<LoginHelper> login = ordinaryUser();
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("certmuse:catalog:resource:remove")).thenReturn(false);

            var result = service.detail("1");

            assertThat(result.syllabusVersionId()).isNull();
            assertThat(result.latestImportBatch()).isNull();
            assertThat(result.createBy()).isNull();
            assertThat(result.deletable()).isFalse();
            assertThat(result.deleteDisabledReason()).isEqualTo("当前用户没有删除权限");
        }
    }

    @Test
    void chunksRejectBothScopesAndMissingKnowledgePoint() {
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("draft", 20L, null, 7L));

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            TextbookChunkQueryBo both = new TextbookChunkQueryBo();
            both.setKnowledgePointId("30");
            both.setExamSubjectId("3");
            assertError(() -> service.chunks("1", both), 400, "INVALID_CHUNK_SCOPE");

            TextbookChunkQueryBo missing = new TextbookChunkQueryBo();
            missing.setKnowledgePointId("30");
            when(mapper.selectKnowledgeScope(30L)).thenReturn(null);
            assertError(() -> service.chunks("1", missing), 400, "KNOWLEDGE_POINT_NOT_FOUND");
        }
    }

    @Test
    void chunksAcceptSubjectScopeAndNormalizeBlankKeyword() {
        TextbookChunkQueryBo query = new TextbookChunkQueryBo();
        query.setExamSubjectId("3");
        query.setKeyword("   ");
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("draft", 20L, null, 7L));
        when(mapper.selectSubjectSyllabus(1L, 3L)).thenReturn(20L);
        when(mapper.selectChunks(1L, null, 3L, false, null, null, 20, 0L)).thenReturn(List.of());
        when(mapper.countChunks(1L, null, 3L, false, null, null)).thenReturn(0L);

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            var result = service.chunks("1", query);

            assertThat(result.getRows()).isEmpty();
            assertThat(result.getTotal()).isZero();
            assertThat(query.getKeyword()).isNull();
        }
    }

    @Test
    void chunksRejectKeywordAndPageBounds() {
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("draft", 20L, null, 7L));

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            TextbookChunkQueryBo keyword = new TextbookChunkQueryBo();
            keyword.setKnowledgePointId("30");
            keyword.setKeyword("x".repeat(201));
            assertError(() -> service.chunks("1", keyword), 400, "CHUNK_REQUEST_INVALID");

            TextbookChunkQueryBo page = new TextbookChunkQueryBo();
            page.setExamSubjectId("3");
            page.setPageSize(0);
            when(mapper.selectSubjectSyllabus(1L, 3L)).thenReturn(20L);
            assertError(() -> service.chunks("1", page), 400, "TEXTBOOK_REQUEST_INVALID");
        }
    }

    @Test
    void chunkDetailReturnsNotFoundForForeignChunk() {
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("draft", 20L, null, 7L));
        when(mapper.selectChunk(1L, 2L)).thenReturn(null);

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            assertError(() -> service.chunkDetail("1", "2"), 404, "CHUNK_NOT_FOUND");
        }
    }

    @Test
    void chunkDetailReportsCorruptSourceHeadingAndKnowledgeData() {
        TextbookRows.Chunk badSource = chunk("published", "{", headings(), null);
        TextbookRows.Chunk badHeading = chunk("published", "{}", "{", null);
        TextbookRows.Chunk badKnowledge = chunk("published", "{}", headings(), "{");
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("published", 20L, null, 7L));
        when(mapper.selectChunk(1L, 2L)).thenReturn(badSource, badHeading, badKnowledge);

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            assertError(() -> service.chunkDetail("1", "2"), 500, "TEXTBOOK_DATA_INVALID");
            assertError(() -> service.chunkDetail("1", "2"), 500, "TEXTBOOK_DATA_INVALID");
            assertError(() -> service.chunkDetail("1", "2"), 500, "TEXTBOOK_DATA_INVALID");
        }
    }

    @Test
    void chunkDetailUsesLegacyLocatorFieldsAndAllowsNoKnowledgeMappings() {
        String locator = """
            {"source_type":"markdown","source_key":"chapter.md","markdown_line_start":4,
             "markdown_line_end":9,"source_page_start":2,"source_page_end":3}
            """;
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("published", 20L, null, 7L));
        when(mapper.selectChunk(1L, 2L)).thenReturn(chunk("published", locator, headings(), null));

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            var result = service.chunkDetail("1", "2");

            assertThat(result.sourceLocator().lineStart()).isEqualTo(4);
            assertThat(result.sourceLocator().lineEnd()).isEqualTo(9);
            assertThat(result.knowledgePoints()).isEmpty();
            assertThat(result.editable()).isFalse();
            assertThat(result.operationDisabledReason()).isNotBlank();
        }
    }

    @Test
    void updateChunkRejectsBlankContentAndOverlongHeading() {
        when(mapper.lockTextbook(1L, 7L)).thenReturn(1L);
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("draft", 20L, null, 7L));

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            TextbookChunkUpdateBo blank = update(" ");
            assertError(() -> service.updateChunk("1", "2", blank), 400, "CHUNK_REQUEST_INVALID");

            TextbookChunkUpdateBo heading = update("正文");
            heading.setHeading("x".repeat(501));
            assertError(() -> service.updateChunk("1", "2", heading), 400, "CHUNK_REQUEST_INVALID");
        }
    }

    @Test
    void updateChunkDistinguishesDeletedChunkFromVersionConflict() {
        TextbookChunkUpdateBo command = update("正文");
        when(mapper.lockTextbook(1L, 7L)).thenReturn(1L);
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("draft", 20L, null, 7L));
        when(mapper.updateChunk(eq(1L), eq(2L), any(), eq(null), eq("正文"), any(), eq(7L))).thenReturn(0);
        when(mapper.selectChunk(1L, 2L)).thenReturn(null, chunk("published", "{}", headings(), null));

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            assertError(() -> service.updateChunk("1", "2", command), 404, "CHUNK_NOT_FOUND");
            assertThatThrownBy(() -> service.updateChunk("1", "2", command))
                .isInstanceOfSatisfying(TextbookException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(409);
                    assertThat(exception.getErrorCode()).isEqualTo("CHUNK_VERSION_CONFLICT");
                    assertThat(exception.isRetryable()).isTrue();
                });
        }
    }

    @Test
    void updateChunkRejectsKnowledgeBindingWithoutSyllabusOrCompleteOwnership() {
        TextbookChunkUpdateBo command = update("正文");
        command.setKnowledgePointIds(List.of("30"));
        when(mapper.lockTextbook(1L, 7L)).thenReturn(1L);
        when(mapper.selectTextbook(1L, 7L)).thenReturn(
            book("draft", null, null, 7L), book("draft", 20L, null, 7L));
        when(mapper.updateChunk(eq(1L), eq(2L), any(), eq(null), eq("正文"), any(), eq(7L))).thenReturn(1);
        when(mapper.countKnowledgePoints(20L, List.of(30L))).thenReturn(0L);

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            assertError(() -> service.updateChunk("1", "2", command), 400, "CHUNK_REQUEST_INVALID");
            assertError(() -> service.updateChunk("1", "2", command), 400, "CHUNK_REQUEST_INVALID");
        }
    }

    @Test
    void updateChunkAcceptsClearingAllKnowledgeBindings() {
        TextbookChunkUpdateBo command = update("正文");
        command.setKnowledgePointIds(List.of());
        when(mapper.lockTextbook(1L, 7L)).thenReturn(1L);
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("draft", 20L, null, 7L));
        when(mapper.updateChunk(eq(1L), eq(2L), any(), eq(null), eq("正文"), any(), eq(7L))).thenReturn(1);
        when(mapper.selectChunk(1L, 2L)).thenReturn(chunk("published", "{}", headings(), null));

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            var result = service.updateChunk("1", "2", command);

            assertThat(result.knowledgePoints()).isEmpty();
        }
        verify(mapper).deleteChunkKnowledgeByChunk(2L);
        verify(mapper, never()).insertChunkKnowledge(any());
    }

    @Test
    void deleteChunksRejectsNullMalformedOversizedAndPartiallyDeletedBatches() {
        when(mapper.lockTextbook(1L, 7L)).thenReturn(1L);
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("draft", 20L, null, 7L));

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            assertError(() -> service.deleteChunks("1", null), 400, "CHUNK_REQUEST_INVALID");
            assertError(() -> service.deleteChunks("1", List.of("abc")), 400, "TEXTBOOK_REQUEST_INVALID");

            List<String> oversized = LongStream.rangeClosed(1, 101).mapToObj(String::valueOf).toList();
            assertError(() -> service.deleteChunks("1", oversized), 400, "CHUNK_REQUEST_INVALID");

            when(mapper.countChunksForDelete(1L, List.of(2L, 3L))).thenReturn(2L);
            when(mapper.deleteChunks(1L, List.of(2L, 3L))).thenReturn(1);
            assertError(() -> service.deleteChunks("1", List.of("2,3")), 409, "CHUNK_BATCH_DELETE_BLOCKED");
        }
    }

    @Test
    void deleteTextbookDistinguishesInvisibleFromConcurrentSoftDelete() {
        when(mapper.lockTextbook(1L, 7L)).thenReturn(null, 1L);
        when(mapper.selectTextbook(1L, 7L)).thenReturn(book("draft", 20L, null, 7L));
        when(mapper.softDeleteTextbook(1L, 7L)).thenReturn(0);

        try (MockedStatic<LoginHelper> login = ordinaryUser()) {
            assertError(() -> service.deleteTextbook("1"), 404, "TEXTBOOK_NOT_FOUND");
            assertError(() -> service.deleteTextbook("1"), 404, "TEXTBOOK_NOT_FOUND");
        }
    }

    private void assertError(
        org.assertj.core.api.ThrowableAssert.ThrowingCallable call, int status, String code
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(TextbookException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(status);
            assertThat(exception.getErrorCode()).isEqualTo(code);
        });
    }

    private static MockedStatic<LoginHelper> ordinaryUser() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        login.when(LoginHelper::getUserId).thenReturn(7L);
        return login;
    }

    private static TextbookChunkUpdateBo update(String content) {
        TextbookChunkUpdateBo command = new TextbookChunkUpdateBo();
        command.setContent(content);
        command.setUpdateTime(time());
        return command;
    }

    private static TextbookRows.Detail book(String status, Long syllabusId, Long batchId, Long createBy) {
        return new TextbookRows.Detail(1L, syllabusId, 9L, syllabusId == null ? null : "2026考纲", "教材", "第一版",
            status, createBy, createBy == null ? null : "管理员", time(), time(), null, null, null, null,
            null, null, null, 2L, 1L, 1L, 3L, 0L, batchId, batchId == null ? null : "教材.jsonl",
            batchId == null ? null : 100L, null, null, null, null, null, null, null);
    }

    private static TextbookRows.Chunk chunk(
        String status, String sourceLocator, String headingPath, String knowledgePoints
    ) {
        return new TextbookRows.Chunk(2L, 1L, 1, "标题", "正文", headingPath, sourceLocator,
            "content-hash", knowledgePoints, time(), status);
    }

    private static String headings() {
        return "{\"headings\":[\"第一章\"]}";
    }

    private static TextbookListVo listRow(String status) {
        return new TextbookListVo("1", "9", "2", "2026考纲", "架构师", "教材", "第一版", status,
            2L, 1L, 1L, 3L, null, null, "7", "管理员", time(), time(), false, null);
    }

    private static OffsetDateTime time() {
        return OffsetDateTime.parse("2026-08-12T10:00:00+08:00");
    }
}
