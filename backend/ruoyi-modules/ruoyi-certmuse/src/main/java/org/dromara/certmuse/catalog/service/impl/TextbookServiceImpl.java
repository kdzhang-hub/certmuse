package org.dromara.certmuse.catalog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.TextbookRows;
import org.dromara.certmuse.catalog.domain.TextbookChunkKnowledgeInsert;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkQueryBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkUpdateBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookQueryBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookUpdateBo;
import org.dromara.certmuse.catalog.domain.vo.StatusOptionVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookChunkDetailVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookChunkListItemVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookDetailVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookListVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookOptionsVo;
import org.dromara.certmuse.catalog.mapper.TextbookMapper;
import org.dromara.certmuse.catalog.service.TextbookService;
import org.dromara.certmuse.catalog.support.TextbookException;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class TextbookServiceImpl implements TextbookService {
    private static final List<StatusOptionVo> STATUS_OPTIONS = List.of(
        new StatusOptionVo("draft", "草稿"), new StatusOptionVo("pending_review", "待审核"),
        new StatusOptionVo("approved", "已通过"), new StatusOptionVo("rejected", "已驳回"),
        new StatusOptionVo("published", "已发布"), new StatusOptionVo("offline", "已下架"));
    private static final List<String> STATUSES = STATUS_OPTIONS.stream().map(StatusOptionVo::value).toList();
    private final TextbookMapper mapper;
    private final JsonMapper jsonMapper;

    public PageResult<TextbookListVo> list(TextbookQueryBo query) {
        normalize(query);
        Long certification = nullableId(query.getCertificationId(), "certificationId");
        Long syllabus = nullableId(query.getSyllabusVersionId(), "syllabusVersionId");
        Long creator = nullableId(query.getCreateBy(), "createBy");
        int[] page = page(query.getPageNum(), query.getPageSize());
        Long visible = visibleUser();
        boolean canRemove = StpUtil.hasPermission("certmuse:catalog:resource:remove");
        List<TextbookListVo> rows = mapper.selectTextbooks(query, certification, syllabus, creator, visible, page[1],
            (long) (page[0] - 1) * page[1]).stream().map(row -> operationList(row, canRemove)).toList();
        return PageResult.build(rows, mapper.countTextbooks(query, certification, syllabus, creator, visible));
    }

    public TextbookOptionsVo options() {
        Long visible = visibleUser();
        return new TextbookOptionsVo(mapper.selectCertificationOptions(visible), STATUS_OPTIONS,
            mapper.selectCreatorOptions(visible));
    }

    public TextbookDetailVo detail(String textbookId) {
        return detail(textbook(id(textbookId, "textbookId")));
    }

    @Transactional
    public void updateTextbook(String textbookId, TextbookUpdateBo command) {
        long documentId = id(textbookId, "textbookId");
        TextbookRows.Detail book = textbookForUpdate(documentId);
        requireMetadataEditable(book);
        String title = trim(command == null ? null : command.getTitle());
        if (title == null || title.length() > 500) throw requestInvalid("教材名称长度必须在1到500字符之间");
        long certificationId = id(command.getCertificationId(), "certificationId");
        if (mapper.selectActiveCertification(certificationId) == null) {
            throw invalid("CERTIFICATION_NOT_FOUND", "绑定资格不存在或已停用");
        }
        boolean certificationChanged = !Objects.equals(book.certificationId(), certificationId);
        if (certificationChanged && mapper.countTextbookKnowledgeMappings(documentId) > 0) {
            throw new TextbookException(409, "TEXTBOOK_CERTIFICATION_CHANGE_BLOCKED", "已绑定知识点的教材不能修改绑定资格");
        }
        if (mapper.updateTextbook(documentId, title, certificationId, certificationChanged, LoginHelper.getUserId()) != 1) {
            throw notFound("TEXTBOOK_NOT_FOUND", "教材不存在或不可访问");
        }
    }

    @Transactional
    public void publishTextbook(String textbookId) {
        long documentId = id(textbookId, "textbookId");
        TextbookRows.Detail book = textbookForUpdate(documentId);
        requireDraft(book, "TEXTBOOK_PUBLISH_FORBIDDEN", "仅草稿教材允许发布");
        if (mapper.publishTextbook(documentId, LoginHelper.getUserId()) != 1) {
            throw new TextbookException(409, "TEXTBOOK_PUBLISH_FORBIDDEN", "教材状态已变更，请刷新后重试");
        }
    }

    @Transactional
    public void takeTextbookOffline(String textbookId) {
        long documentId = id(textbookId, "textbookId");
        TextbookRows.Detail book = textbookForUpdate(documentId);
        if (!"published".equals(book.status())) {
            throw new TextbookException(409, "TEXTBOOK_OFFLINE_FORBIDDEN", "仅已发布教材允许下架");
        }
        if (mapper.takeTextbookOffline(documentId, LoginHelper.getUserId()) != 1) {
            throw new TextbookException(409, "TEXTBOOK_OFFLINE_FORBIDDEN", "教材状态已变更，请刷新后重试");
        }
    }

    public PageResult<TextbookChunkListItemVo> chunks(String textbookId, TextbookChunkQueryBo query) {
        long documentId = id(textbookId, "textbookId");
        TextbookRows.Detail book = textbook(documentId);
        Long knowledgeId = nullableId(query.getKnowledgePointId(), "knowledgePointId");
        Long subjectId = nullableId(query.getExamSubjectId(), "examSubjectId");
        if ((knowledgeId == null) == (subjectId == null)) {
            throw invalid("INVALID_CHUNK_SCOPE", "knowledgePointId和examSubjectId必须二选一");
        }
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
            if (query.getKeyword().isEmpty()) query.setKeyword(null);
            else if (query.getKeyword().length() > 200) throw chunkInvalid("keyword长度不能超过200字符");
        }
        if (knowledgeId != null) {
            TextbookRows.Scope scope = mapper.selectKnowledgeScope(knowledgeId);
            if (scope == null) throw invalid("KNOWLEDGE_POINT_NOT_FOUND", "知识点不存在");
            if (!Objects.equals(scope.syllabusVersionId(), book.syllabusVersionId())) {
                throw invalid("KNOWLEDGE_POINT_VERSION_MISMATCH", "知识点与教材不属于同一大纲版本");
            }
        } else if (mapper.selectSubjectSyllabus(documentId, subjectId) == null) {
            throw invalid("INVALID_CHUNK_SCOPE", "考试科目不属于教材对应资格");
        }
        int[] page = page(query.getPageNum(), query.getPageSize());
        boolean descendants = Boolean.TRUE.equals(query.getIncludeDescendants());
        List<TextbookChunkListItemVo> rows = mapper.selectChunks(documentId, knowledgeId, subjectId,
            descendants, query.getHasKnowledgePoint(), query.getKeyword(), page[1], (long) (page[0] - 1) * page[1]).stream()
            .map(this::listChunk).toList();
        return PageResult.build(rows, mapper.countChunks(documentId, knowledgeId, subjectId,
            descendants, query.getHasKnowledgePoint(), query.getKeyword()));
    }

    public TextbookChunkDetailVo chunkDetail(String textbookId, String chunkId) {
        long documentId = id(textbookId, "textbookId");
        textbook(documentId);
        TextbookRows.Chunk row = mapper.selectChunk(documentId, id(chunkId, "chunkId"));
        if (row == null) throw notFound("CHUNK_NOT_FOUND", "内容块不存在或不属于当前教材");
        return detailChunk(row);
    }

    @Transactional
    public TextbookChunkDetailVo updateChunk(String textbookId, String chunkId, TextbookChunkUpdateBo command) {
        long documentId = id(textbookId, "textbookId");
        long parsedChunkId = id(chunkId, "chunkId");
        TextbookRows.Detail book = textbookForUpdate(documentId);
        requireDraft(book, "CHUNK_EDIT_FORBIDDEN", "当前教材状态不允许修改内容块");
        String heading = command.getHeading() == null || command.getHeading().isBlank()
            ? null : command.getHeading().trim();
        if (heading != null && heading.length() > 500) throw chunkInvalid("heading长度不能超过500字符");
        if (command.getContent() == null || command.getContent().isBlank()) throw chunkInvalid("content不能为空");
        int changed = mapper.updateChunk(documentId, parsedChunkId, command.getUpdateTime(), heading,
            command.getContent(), DigestUtil.sha256Hex(command.getContent()), LoginHelper.getUserId());
        if (changed != 1) {
            if (mapper.selectChunk(documentId, parsedChunkId) == null) {
                throw notFound("CHUNK_NOT_FOUND", "内容块不存在或不属于当前教材");
            }
            throw new TextbookException(409, "CHUNK_VERSION_CONFLICT", "内容块已被其他人更新，请刷新后重试", true);
        }
        if (command.getKnowledgePointIds() != null) {
            List<Long> knowledgePointIds = command.getKnowledgePointIds().stream()
                .map(value -> id(value, "knowledgePointIds")).distinct().toList();
            if (!knowledgePointIds.isEmpty() && book.syllabusVersionId() == null) {
                throw chunkInvalid("无考纲教材不能绑定知识点");
            }
            if (!knowledgePointIds.isEmpty()
                && mapper.countKnowledgePoints(book.syllabusVersionId(), knowledgePointIds) != knowledgePointIds.size()) {
                throw chunkInvalid("知识点不存在或不属于当前教材大纲版本");
            }
            mapper.deleteChunkKnowledgeByChunk(parsedChunkId);
            if (!knowledgePointIds.isEmpty()) {
                mapper.insertChunkKnowledge(knowledgePointIds.stream()
                    .map(knowledgePointId -> new TextbookChunkKnowledgeInsert(IdUtil.getSnowflakeNextId(), parsedChunkId, knowledgePointId))
                    .toList());
            }
        }
        return detailChunk(mapper.selectChunk(documentId, parsedChunkId));
    }

    @Transactional
    public void deleteChunks(String textbookId, Collection<String> rawIds) {
        long documentId = id(textbookId, "textbookId");
        TextbookRows.Detail book = textbookForUpdate(documentId);
        requireDraft(book, "CHUNK_DELETE_FORBIDDEN", "当前教材状态不允许删除内容块");
        List<Long> ids = rawIds == null ? List.of() : rawIds.stream().flatMap(value ->
            List.of(value.split(",")).stream()).map(String::trim).filter(value -> !value.isEmpty())
            .map(value -> id(value, "ids")).distinct().toList();
        if (ids.isEmpty() || ids.size() > 100) throw chunkInvalid("ids数量必须在1到100之间");
        if (mapper.countChunksForDelete(documentId, ids) != ids.size()) {
            throw new TextbookException(409, "CHUNK_BATCH_DELETE_BLOCKED", "存在不存在或不属于当前教材的内容块");
        }
        mapper.deleteImages(documentId, ids);
        mapper.deleteChunkKnowledge(documentId, ids);
        if (mapper.deleteChunks(documentId, ids) != ids.size()) {
            throw new TextbookException(409, "CHUNK_BATCH_DELETE_BLOCKED", "内容块删除未完整执行");
        }
    }

    @Transactional
    public void deleteTextbook(String textbookId) {
        long documentId = id(textbookId, "textbookId");
        TextbookRows.Detail book = textbookForUpdate(documentId);
        requireDraft(book, "TEXTBOOK_STATUS_DELETE_FORBIDDEN", "仅草稿教材允许删除");
        if (mapper.softDeleteTextbook(documentId, LoginHelper.getUserId()) != 1) {
            throw notFound("TEXTBOOK_NOT_FOUND", "教材不存在或不可访问");
        }
    }

    private TextbookRows.Detail textbook(long documentId) {
        TextbookRows.Detail row = mapper.selectTextbook(documentId, visibleUser());
        if (row == null) throw notFound("TEXTBOOK_NOT_FOUND", "教材不存在或不可访问");
        return row;
    }

    private TextbookRows.Detail textbookForUpdate(long documentId) {
        if (mapper.lockTextbook(documentId, visibleUser()) == null) {
            throw notFound("TEXTBOOK_NOT_FOUND", "教材不存在或不可访问");
        }
        return textbook(documentId);
    }

    private TextbookDetailVo detail(TextbookRows.Detail row) {
        boolean deletable = "draft".equals(row.status()) && StpUtil.hasPermission("certmuse:catalog:resource:remove");
        TextbookDetailVo.LatestImportBatchVo batch = row.latestImportBatchId() == null ? null
            : new TextbookDetailVo.LatestImportBatchVo(String.valueOf(row.latestImportBatchId()), row.sourceFileName(),
                row.sourceFileSize() == null ? 0 : row.sourceFileSize(), row.sourceFileHash(), row.latestImportStatus(),
                value(row.validCount()), value(row.warningCount()), value(row.failedCount()),
                row.latestImportCreateTime(), row.latestImportFinishedTime());
        return new TextbookDetailVo(String.valueOf(row.id()), row.title(),
            row.syllabusVersionId() == null ? null : String.valueOf(row.syllabusVersionId()),
            row.syllabusVersionName(), "textbook", row.edition(), row.status(),
            new TextbookDetailVo.StatisticsVo(row.chunkCount(), row.mappedChunkCount(), row.unmappedChunkCount(),
                row.knowledgePointCount(), row.imageCount()), batch, row.submittedByName(), row.submittedTime(),
            row.reviewedByName(), row.reviewedTime(), row.reviewComment(), row.publishedByName(), row.publishedTime(),
            row.createBy() == null ? null : String.valueOf(row.createBy()), row.createByName(), row.createTime(),
            row.updateTime(), deletable, deletable ? null : deleteReason(row.status()));
    }

    private TextbookChunkListItemVo listChunk(TextbookRows.Chunk row) {
        Source source = source(row.sourceLocator());
        List<TextbookChunkListItemVo.KnowledgePointVo> points = knowledge(row.knowledgePoints());
        return new TextbookChunkListItemVo(String.valueOf(row.id()), String.valueOf(row.documentId()), row.chunkOrder(),
            row.heading(), headings(row.headingPath()), summary(row.content()), source.pageStart(), source.pageEnd(),
            source.locator(), points, !points.isEmpty());
    }

    private TextbookChunkDetailVo detailChunk(TextbookRows.Chunk row) {
        boolean editable = "draft".equals(row.documentStatus())
            && StpUtil.hasPermission("certmuse:catalog:resource:edit");
        boolean deletable = "draft".equals(row.documentStatus())
            && StpUtil.hasPermission("certmuse:catalog:resource:remove");
        Source source = source(row.sourceLocator());
        return new TextbookChunkDetailVo(String.valueOf(row.id()), String.valueOf(row.documentId()), row.chunkOrder(),
            row.heading(), headings(row.headingPath()), row.content(), row.contentHash(), source.locator(),
            knowledge(row.knowledgePoints()), row.updateTime(), editable, deletable,
            editable || deletable ? null : "当前教材状态或权限不允许操作");
    }

    private TextbookListVo operationList(TextbookListVo row, boolean canRemove) {
        boolean deletable = "draft".equals(row.status()) && canRemove;
        return new TextbookListVo(row.id(), row.certificationId(), row.syllabusVersionId(), row.syllabusVersionName(),
            row.certificationName(), row.title(),
            row.edition(), row.status(), row.chunkCount(), row.mappedChunkCount(), row.unmappedChunkCount(),
            row.knowledgePointCount(), row.latestImportBatchId(), row.latestImportStatus(), row.createBy(),
            row.createByName(), row.createTime(), row.updateTime(), deletable,
            deletable ? null : deleteReason(row.status()));
    }

    private Source source(String value) {
        try {
            JsonNode node = jsonMapper.readTree(value);
            String sourceType = text(node, "source_type");
            String sourceKey = text(node, "source_key");
            Integer lineStart = integer(node, "line_start", "markdown_line_start");
            Integer lineEnd = integer(node, "line_end", "markdown_line_end");
            Integer pageStart = integer(node, "page_start", "source_page_start");
            Integer pageEnd = integer(node, "page_end", "source_page_end");
            return new Source(new TextbookChunkListItemVo.SourceLocatorVo(sourceType, sourceKey, lineStart, lineEnd),
                pageStart, pageEnd);
        } catch (Exception e) {
            throw new TextbookException(500, "TEXTBOOK_DATA_INVALID", "教材来源定位数据损坏", e);
        }
    }

    private List<TextbookChunkListItemVo.KnowledgePointVo> knowledge(String value) {
        try {
            if (value == null) return List.of();
            return jsonMapper.readTree(value).valueStream().map(node -> new TextbookChunkListItemVo.KnowledgePointVo(
                node.path("id").asText(), node.path("subjectNo").isNull() ? null : node.path("subjectNo").asInt(),
                node.path("code").asText(), node.path("title").asText())).toList();
        } catch (Exception e) {
            throw new TextbookException(500, "TEXTBOOK_DATA_INVALID", "教材知识点数据损坏", e);
        }
    }

    private List<String> headings(String value) {
        try { return jsonMapper.readTree(value).path("headings").valueStream().map(JsonNode::asText).toList(); }
        catch (Exception e) { throw new TextbookException(500, "TEXTBOOK_DATA_INVALID", "教材章节路径数据损坏", e); }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).isMissingNode() || node.path(field).isNull() ? null : node.path(field).asText();
    }
    private static Integer integer(JsonNode node, String... fields) {
        for (String field : fields) if (!node.path(field).isMissingNode() && !node.path(field).isNull()) return node.path(field).asInt();
        return null;
    }
    private static String summary(String value) {
        return value.codePoints().limit(200).collect(StringBuilder::new, StringBuilder::appendCodePoint,
            StringBuilder::append).toString();
    }
    private static void requireDraft(TextbookRows.Detail book, String code, String message) {
        if (!"draft".equals(book.status())) throw new TextbookException(409, code, message);
    }
    private static void requireMetadataEditable(TextbookRows.Detail book) {
        if (!List.of("draft", "published").contains(book.status())) {
            throw new TextbookException(409, "TEXTBOOK_EDIT_FORBIDDEN", "仅草稿或已发布教材允许修改名称和绑定资格");
        }
    }
    private static String deleteReason(String status) {
        return "draft".equals(status) ? "当前用户没有删除权限" : "仅草稿教材可删除";
    }
    private static int value(Integer value) { return value == null ? 0 : value; }
    private static Long visibleUser() { return LoginHelper.isSuperAdmin() ? null : LoginHelper.getUserId(); }

    private static void normalize(TextbookQueryBo query) {
        query.setTitle(trim(query.getTitle())); query.setEdition(trim(query.getEdition()));
        query.setStatus(trim(query.getStatus())); query.setOrderByColumn(trim(query.getOrderByColumn()));
        query.setIsAsc(trim(query.getIsAsc()));
        if (query.getTitle() != null && query.getTitle().length() > 500) throw requestInvalid("title长度不能超过500字符");
        if (query.getEdition() != null && query.getEdition().length() > 100) throw requestInvalid("edition长度不能超过100字符");
        if (query.getStatus() != null && !STATUSES.contains(query.getStatus())) throw requestInvalid("status不合法");
        if ((query.getBeginCreateTime() == null) != (query.getEndCreateTime() == null)) throw requestInvalid("创建时间范围必须成对提供");
        if (query.getBeginCreateTime() != null && query.getEndCreateTime().isBefore(query.getBeginCreateTime())) throw requestInvalid("结束时间不能早于开始时间");
        if (query.getOrderByColumn() != null && !List.of("createTime", "updateTime", "title", "chunkCount").contains(query.getOrderByColumn())) throw requestInvalid("orderByColumn不合法");
        if (query.getIsAsc() != null && !List.of("asc", "desc").contains(query.getIsAsc().toLowerCase())) throw requestInvalid("isAsc不合法");
    }
    private static int[] page(Integer number, Integer size) {
        int page = number == null ? 1 : number, limit = size == null ? 20 : size;
        if (page < 1 || limit < 1 || limit > 100) throw requestInvalid("分页参数超出允许范围");
        return new int[]{page, limit};
    }
    private static Long nullableId(String value, String field) { return value == null || value.isBlank() ? null : id(value, field); }
    private static long id(String value, String field) {
        try { if (value == null || !value.matches("^[1-9][0-9]{0,18}$")) throw new NumberFormatException(); return Long.parseLong(value); }
        catch (NumberFormatException e) { throw requestInvalid(field + "必须是十进制正整数"); }
    }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static TextbookException requestInvalid(String message) { return invalid("TEXTBOOK_REQUEST_INVALID", message); }
    private static TextbookException chunkInvalid(String message) { return invalid("CHUNK_REQUEST_INVALID", message); }
    private static TextbookException invalid(String code, String message) { return new TextbookException(400, code, message); }
    private static TextbookException notFound(String code, String message) { return new TextbookException(404, code, message); }
    private record Source(TextbookChunkListItemVo.SourceLocatorVo locator, Integer pageStart, Integer pageEnd) {}
}
