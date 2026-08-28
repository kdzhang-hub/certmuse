package org.dromara.certmuse.catalog.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.QualificationRow;
import org.dromara.certmuse.catalog.domain.ReferenceBlockerRow;
import org.dromara.certmuse.catalog.domain.ReferenceCountRow;
import org.dromara.certmuse.catalog.domain.SyllabusVersionRow;
import org.dromara.certmuse.catalog.domain.bo.QualificationQueryBo;
import org.dromara.certmuse.catalog.domain.bo.QualificationWriteBo;
import org.dromara.certmuse.catalog.domain.bo.SyllabusVersionWriteBo;
import org.dromara.certmuse.catalog.domain.vo.QualificationVo;
import org.dromara.certmuse.catalog.domain.vo.QualificationVersionBlockerVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusVersionVo;
import org.dromara.certmuse.catalog.mapper.QualificationVersionMapper;
import org.dromara.certmuse.catalog.service.QualificationVersionService;
import org.dromara.certmuse.catalog.support.CatalogJsonSchema;
import org.dromara.certmuse.catalog.support.QualificationVersionException;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Transactional M01 implementation. The mapper owns all PostgreSQL-specific queries. */
@Service
@RequiredArgsConstructor
public class QualificationVersionServiceImpl implements QualificationVersionService {
    private static final List<ExamSubject> HIGH_QUALIFICATION_SUBJECTS = List.of(
        new ExamSubject("COMPREHENSIVE", "综合知识"),
        new ExamSubject("CASE_ANALYSIS", "案例分析"),
        new ExamSubject("ESSAY", "论文")
    );
    private static final String CREATE_QUALIFICATION = "qualification_create";
    private static final String UPDATE_QUALIFICATION = "qualification_update";
    private static final String DELETE_QUALIFICATION = "qualification_delete";
    private static final String CREATE_VERSION = "syllabus_version_create";
    private static final String UPDATE_VERSION = "syllabus_version_update";
    private static final String DELETE_VERSION = "syllabus_version_delete";
    private final QualificationVersionMapper mapper;
    private final JsonMapper jsonMapper;
    private final ImportPersistenceService importPersistence;

    @Override
    public PageResult<QualificationVo> list(QualificationQueryBo query) {
        normalizeQuery(query);
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        long total = mapper.countQualifications(query);
        if (total == 0) return PageResult.build(List.of(), 0);
        List<QualificationRow> rows = mapper.selectQualifications(query, pageSize, (long) (pageNum - 1) * pageSize);
        if (rows.isEmpty()) return PageResult.build(List.of(), total);
        Collection<Long> ids = rows.stream().map(QualificationRow::id).toList();
        List<SyllabusVersionVo> versionRows = mapper.selectVersions(ids).stream().map(this::toVersion).toList();
        Map<Long, List<SyllabusVersionVo>> versions = versionRows.stream()
            .collect(java.util.stream.Collectors.groupingBy(value -> Long.parseLong(value.certificationId()), java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toList(), values -> values.stream().sorted(versionOrder()).toList())));
        Map<Long, Long> qualificationReferences = toCountMap(mapper.selectQualificationReferenceCounts(ids));
        List<Long> versionIds = versionRows.stream().map(value -> Long.parseLong(value.id())).toList();
        Map<Long, Long> versionReferences = versionIds.isEmpty() ? Map.of()
            : toCountMap(mapper.selectVersionReferenceCounts(versionIds));
        List<QualificationVo> result = rows.stream().map(row -> toQualification(row, versions.getOrDefault(row.id(), List.of()), qualificationReferences, versionReferences)).toList();
        return PageResult.build(result, total);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public QualificationVo createQualification(String requestId, QualificationWriteBo command) {
        QualificationInput input = qualificationInput(command); String normalizedRequestId = requestId(requestId);
        String payload = hash(Map.of("code", input.code(), "name", input.name(), "level", input.level(), "status", input.status(), "sortOrder", input.sortOrder()));
        CmIdempotencyRecord existing = mapper.selectIdempotency(CREATE_QUALIFICATION, normalizedRequestId);
        if (existing != null) return replayQualification(existing, payload);
        ensureQualificationUnique(input, null);
        long id = IdUtil.getSnowflakeNextId(); insertIdempotency(CREATE_QUALIFICATION, normalizedRequestId, payload, "certification", id);
        try {
            mapper.insertQualification(id, input.code(), input.name(), input.level(), input.status(), input.sortOrder(), LoginHelper.getUserId(), LoginHelper.getDeptId());
            insertDefaultSubjects(id, input.level());
        }
        catch (DataIntegrityViolationException exception) { throw conflict("QUALIFICATION_CONFLICT", "资格名称或资格编码已存在"); }
        QualificationVo result = qualificationForId(id); complete(CREATE_QUALIFICATION, normalizedRequestId, id, result); return result;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public QualificationVo updateQualification(String certificationId, String requestId, QualificationWriteBo command) {
        long id = qualificationId(certificationId); QualificationInput input = qualificationInput(command); String normalizedRequestId = requestId(requestId);
        String payload = hash(Map.of("id", id, "code", input.code(), "name", input.name(), "level", input.level(), "status", input.status(), "sortOrder", input.sortOrder()));
        CmIdempotencyRecord existing = mapper.selectIdempotency(UPDATE_QUALIFICATION, normalizedRequestId);
        if (existing != null) return replayQualification(existing, payload);
        requireQualification(mapper.lockQualification(id)); ensureQualificationUnique(input, id); insertIdempotency(UPDATE_QUALIFICATION, normalizedRequestId, payload, "certification", id);
        try { mapper.updateQualification(id, input.code(), input.name(), input.level(), input.status(), input.sortOrder(), LoginHelper.getUserId()); }
        catch (DataIntegrityViolationException exception) { throw conflict("QUALIFICATION_CONFLICT", "资格名称或资格编码已存在"); }
        QualificationVo result = qualificationForId(id); complete(UPDATE_QUALIFICATION, normalizedRequestId, id, result); return result;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void deleteQualification(String certificationId, String requestId) {
        long id = qualificationId(certificationId); String normalizedRequestId = requestId(requestId); String payload = hash(Map.of("id", id));
        CmIdempotencyRecord existing = mapper.selectIdempotency(DELETE_QUALIFICATION, normalizedRequestId);
        if (existing != null) { replayDelete(existing, payload); return; }
        requireQualification(mapper.lockQualification(id));
        DeletedStorageObjects storageObjects = deletedStorageObjects(
            mapper.selectSourceObjectKeysByQualification(id), mapper.selectImageObjectKeysByQualification(id)
        );
        insertIdempotency(DELETE_QUALIFICATION, normalizedRequestId, payload, "certification", id);
        try {
            mapper.deleteKnowledgeImportDiffsByQualification(id);
            mapper.setQualificationCascadeDelete(true);
            mapper.deleteQualification(id);
            mapper.setQualificationCascadeDelete(false);
        }
        catch (DataIntegrityViolationException exception) {
            throw conflict("QUALIFICATION_DELETE_FAILED", "资格关联数据删除失败");
        }
        complete(DELETE_QUALIFICATION, normalizedRequestId, id, null); cleanupStorageAfterCommit(storageObjects);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public SyllabusVersionVo createVersion(String certificationId, String requestId, SyllabusVersionWriteBo command) {
        long qualificationId = qualificationId(certificationId); VersionInput input = versionInput(command); String normalizedRequestId = requestId(requestId);
        String payload = hash(Map.of("certificationId", qualificationId, "name", input.name(), "publishedDate", String.valueOf(input.publishedDate())));
        CmIdempotencyRecord existing = mapper.selectIdempotency(CREATE_VERSION, normalizedRequestId);
        if (existing != null) return replayVersion(existing, payload, qualificationId);
        requireQualification(mapper.lockQualification(qualificationId));
        if (!mapper.selectVersions(List.of(qualificationId)).isEmpty()) {
            throw conflict("SYLLABUS_ALREADY_EXISTS", "该资格已存在考纲");
        }
        long id = IdUtil.getSnowflakeNextId(); insertIdempotency(CREATE_VERSION, normalizedRequestId, payload, "syllabus_version", id);
        try { mapper.insertVersion(id, qualificationId, input.name(), input.publishedDate(), LoginHelper.getUserId(), LoginHelper.getDeptId()); }
        catch (DataIntegrityViolationException exception) { throw conflict("SYLLABUS_VERSION_NAME_CONFLICT", "同一资格下的版本名称已存在"); }
        SyllabusVersionVo result = versionForId(qualificationId, id); complete(CREATE_VERSION, normalizedRequestId, id, result); return result;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public SyllabusVersionVo updateVersion(String certificationId, String versionId, String requestId, SyllabusVersionWriteBo command) {
        long qualificationId = qualificationId(certificationId); long id = versionId(versionId); VersionInput input = versionInput(command); String normalizedRequestId = requestId(requestId);
        String payload = hash(Map.of("certificationId", qualificationId, "id", id, "name", input.name(), "publishedDate", String.valueOf(input.publishedDate())));
        CmIdempotencyRecord existing = mapper.selectIdempotency(UPDATE_VERSION, normalizedRequestId);
        if (existing != null) return replayVersion(existing, payload, qualificationId);
        requireVersion(mapper.lockVersion(qualificationId, id)); insertIdempotency(UPDATE_VERSION, normalizedRequestId, payload, "syllabus_version", id);
        try { mapper.updateVersion(id, qualificationId, input.name(), input.publishedDate(), LoginHelper.getUserId()); }
        catch (DataIntegrityViolationException exception) { throw conflict("SYLLABUS_VERSION_NAME_CONFLICT", "同一资格下的版本名称已存在"); }
        SyllabusVersionVo result = versionForId(qualificationId, id); complete(UPDATE_VERSION, normalizedRequestId, id, result); return result;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void deleteVersion(String certificationId, String versionId, String requestId) {
        long qualificationId = qualificationId(certificationId); long id = versionId(versionId); String normalizedRequestId = requestId(requestId); String payload = hash(Map.of("certificationId", qualificationId, "id", id));
        CmIdempotencyRecord existing = mapper.selectIdempotency(DELETE_VERSION, normalizedRequestId);
        if (existing != null) { replayDelete(existing, payload); return; }
        requireVersion(mapper.lockVersion(qualificationId, id));
        List<ReferenceBlockerRow> blockers = mapper.selectVersionDeleteBlockers(id);
        if (!blockers.isEmpty()) {
            throw new QualificationVersionException(
                409,
                "SYLLABUS_VERSION_DELETE_BLOCKED",
                "考纲版本存在题目或教材关联，无法删除",
                List.of(),
                blockers.stream().map(row -> new QualificationVersionBlockerVo(row.type(), row.label(), row.count())).toList(),
                null
            );
        }
        DeletedStorageObjects storageObjects = deletedStorageObjects(
            mapper.selectSourceObjectKeysByVersion(id), mapper.selectImageObjectKeysByVersion(id)
        );
        insertIdempotency(DELETE_VERSION, normalizedRequestId, payload, "syllabus_version", id);
        try {
            mapper.deleteKnowledgeImportDiffsByVersion(id);
            mapper.deleteVersion(id, qualificationId);
        }
        catch (DataIntegrityViolationException exception) {
            throw conflict("SYLLABUS_VERSION_DELETE_FAILED", "考纲版本关联数据删除失败");
        }
        complete(DELETE_VERSION, normalizedRequestId, id, null); cleanupStorageAfterCommit(storageObjects);
    }

    private QualificationVo qualificationForId(long id) {
        QualificationRow qualification = requireQualification(mapper.selectQualification(id));
        List<SyllabusVersionVo> versions = mapper.selectVersions(List.of(id)).stream().map(this::toVersion).sorted(versionOrder()).toList();
        Map<Long, Long> qualificationReferences = toCountMap(mapper.selectQualificationReferenceCounts(List.of(id)));
        Map<Long, Long> versionReferences = versions.isEmpty() ? Map.of()
            : toCountMap(mapper.selectVersionReferenceCounts(versions.stream().map(value -> Long.parseLong(value.id())).toList()));
        return toQualification(qualification, versions, qualificationReferences, versionReferences);
    }
    private SyllabusVersionVo versionForId(long certificationId, long id) {
        SyllabusVersionVo version = toVersion(requireVersion(mapper.selectVersion(certificationId, id)));
        Map<Long, Long> references = toCountMap(mapper.selectVersionReferenceCounts(List.of(id)));
        return new SyllabusVersionVo(version.id(), version.certificationId(), version.versionName(), version.publishedDate(),
            references.getOrDefault(id, 0L), version.createTime(), version.updateTime());
    }
    private QualificationVo replayQualification(CmIdempotencyRecord record, String payload) { replay(record, payload); return replayData(record, QualificationVo.class); }
    private SyllabusVersionVo replayVersion(CmIdempotencyRecord record, String payload, long certificationId) { replay(record, payload); return replayData(record, SyllabusVersionVo.class); }
    private void replayDelete(CmIdempotencyRecord record, String payload) { replay(record, payload); }
    private void replay(CmIdempotencyRecord record, String payload) {
        if (!java.security.MessageDigest.isEqual(record.payloadHash().getBytes(StandardCharsets.US_ASCII), payload.getBytes(StandardCharsets.US_ASCII)) || !"succeeded".equals(record.status()) || record.resourceId() == null) throw conflict("IDEMPOTENCY_KEY_CONFLICT", "请求号已被使用");
    }
    private <T> T replayData(CmIdempotencyRecord record, Class<T> type) {
        try {
            JsonNode root = jsonMapper.readTree(record.responseBody());
            return jsonMapper.treeToValue(root.path("data"), type);
        } catch (Exception exception) {
            throw new QualificationVersionException(500, "QUALIFICATION_VERSION_FAILURE", "读取幂等结果失败", exception);
        }
    }
    private void insertIdempotency(String action, String requestId, String payload, String resourceType, long resourceId) { mapper.insertIdempotency(IdUtil.getSnowflakeNextId(), action, requestId, payload, resourceType, resourceId, OffsetDateTime.now().plusDays(1)); }
    /** Creates the fixed exam subjects shared by every currently supported high-level qualification. */
    private void insertDefaultSubjects(long certificationId, String qualificationLevel) {
        if (!"HIGH".equals(qualificationLevel)) return;
        Long userId = LoginHelper.getUserId();
        for (ExamSubject subject : HIGH_QUALIFICATION_SUBJECTS) {
            mapper.insertExamSubject(IdUtil.getSnowflakeNextId(), certificationId, subject.code(), subject.name(), userId);
        }
    }
    private DeletedStorageObjects deletedStorageObjects(List<String> sourceObjectKeys, List<String> imageObjectKeys) {
        return new DeletedStorageObjects(distinctObjectKeys(sourceObjectKeys), distinctObjectKeys(imageObjectKeys));
    }
    private List<String> distinctObjectKeys(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }
    private void cleanupStorageAfterCommit(DeletedStorageObjects storageObjects) {
        if (storageObjects.empty()) return;
        Runnable cleanup = () -> {
            for (String objectKey : storageObjects.sourceObjectKeys()) {
                importPersistence.enqueueCleanup(objectKey, "unknown", "qualification_version_delete");
            }
            for (String objectKey : storageObjects.imageObjectKeys()) {
                importPersistence.enqueueImageCleanup(objectKey, "unknown", "qualification_version_delete");
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.run(); }
            });
        } else {
            cleanup.run();
        }
    }
    private void complete(String action, String requestId, long resourceId, Object data) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.putAll(VersionedJsonDocumentFactory.flatFields(
            CatalogJsonSchema.QUALIFICATION_VERSION_RESPONSE, Map.of()));
        response.put("data", data);
        mapper.completeIdempotency(action, requestId, resourceId, json(response));
    }
    private String hash(Object payload) { return DigestUtil.sha256Hex(json(payload)); }
    private String json(Object value) { try { return jsonMapper.writeValueAsString(value); } catch (Exception exception) { throw new QualificationVersionException(500, "QUALIFICATION_VERSION_FAILURE", "资格操作失败", exception); } }

    private QualificationVo toQualification(QualificationRow row, List<SyllabusVersionVo> versions, Map<Long, Long> qualificationReferences, Map<Long, Long> versionReferences) {
        List<SyllabusVersionVo> populated = versions.stream().map(version -> new SyllabusVersionVo(version.id(), version.certificationId(), version.versionName(), version.publishedDate(), versionReferences.getOrDefault(Long.parseLong(version.id()), 0L), version.createTime(), version.updateTime())).toList();
        return new QualificationVo(Long.toString(row.id()), row.certificationCode(), row.certificationName(), row.qualificationLevel(), row.status(), row.sortOrder(), populated.size(), qualificationReferences.getOrDefault(row.id(), 0L), row.createTime(), row.updateTime(), populated);
    }
    private SyllabusVersionVo toVersion(SyllabusVersionRow row) { return new SyllabusVersionVo(Long.toString(row.id()), Long.toString(row.certificationId()), row.versionName(), row.publishedDate(), 0, row.createTime(), row.updateTime()); }
    private Map<Long, Long> toCountMap(List<ReferenceCountRow> rows) { return rows.stream().collect(java.util.stream.Collectors.toMap(ReferenceCountRow::id, ReferenceCountRow::referenceCount)); }
    private Comparator<SyllabusVersionVo> versionOrder() { return Comparator.comparing(SyllabusVersionVo::publishedDate, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(SyllabusVersionVo::createTime, Comparator.reverseOrder()).thenComparing(value -> Long.parseLong(value.id()), Comparator.reverseOrder()); }
    private QualificationRow requireQualification(QualificationRow row) { if (row == null) throw new QualificationVersionException(404, "QUALIFICATION_NOT_FOUND", "资格不存在"); return row; }
    private void ensureQualificationUnique(QualificationInput input, Long excludeId) {
        Long codeConflictId = mapper.findQualificationIdByCode(input.code(), excludeId);
        if (codeConflictId != null && codeConflictId > 0) throw conflict("QUALIFICATION_CODE_CONFLICT", "资格编码已存在");
        Long nameConflictId = mapper.findQualificationIdByName(input.name(), excludeId);
        if (nameConflictId != null && nameConflictId > 0) throw conflict("QUALIFICATION_NAME_CONFLICT", "资格名称已存在");
    }
    private SyllabusVersionRow requireVersion(SyllabusVersionRow row) { if (row == null) throw new QualificationVersionException(404, "SYLLABUS_VERSION_NOT_FOUND", "考纲版本不存在"); return row; }
    private QualificationVersionException conflict(String code, String message) { return new QualificationVersionException(409, code, message); }

    private void normalizeQuery(QualificationQueryBo query) {
        if (query == null) throw invalid("请求参数不能为空");
        query.setKeyword(trim(query.getKeyword())); if (query.getKeyword() != null && query.getKeyword().length() > 100) throw invalid("keyword长度不能超过100");
        if (query.getQualificationLevel() != null && !List.of("HIGH", "MIDDLE", "LOW").contains(query.getQualificationLevel())) throw invalid("qualificationLevel不合法");
        if (query.getStatus() != null && !List.of("0", "1").contains(query.getStatus())) throw invalid("status不合法");
        if (query.getPageNum() != null && query.getPageNum() < 1) throw invalid("pageNum不合法"); if (query.getPageSize() != null && (query.getPageSize() < 1 || query.getPageSize() > 100)) throw invalid("pageSize不合法");
    }
    private QualificationInput qualificationInput(QualificationWriteBo command) {
        if (command == null) throw invalid("请求参数不能为空"); String code = trim(command.getCertificationCode()); String name = trim(command.getCertificationName()); String level = trim(command.getQualificationLevel()); String status = trim(command.getStatus());
        if (code == null || !code.matches("[A-Za-z0-9_]{1,50}")) throw invalid("资格编码不合法"); if (name == null || name.length() > 200 || level == null || !List.of("HIGH", "MIDDLE", "LOW").contains(level) || !List.of("0", "1").contains(status) || command.getSortOrder() == null || command.getSortOrder() < 0) throw invalid("资格字段不合法");
        return new QualificationInput(code.toUpperCase(Locale.ROOT), name, level, status, command.getSortOrder());
    }
    private VersionInput versionInput(SyllabusVersionWriteBo command) { if (command == null) throw invalid("请求参数不能为空"); String name = trim(command.getVersionName()); if (name == null || name.length() > 200) throw invalid("版本名称不合法"); return new VersionInput(name, command.getPublishedDate()); }
    private long qualificationId(String value) { return id(value, "资格ID不合法"); } private long versionId(String value) { return id(value, "版本ID不合法"); }
    private long id(String value, String message) { try { long id = Long.parseLong(value); if (id <= 0) throw new NumberFormatException(); return id; } catch (NumberFormatException exception) { throw invalid(message); } }
    private String requestId(String value) { try { return UUID.fromString(value).toString(); } catch (IllegalArgumentException exception) { throw invalid("X-Request-Id必须是UUID"); } }
    private String trim(String value) { if (value == null) return null; String trimmed = value.trim(); return trimmed.isEmpty() ? null : trimmed; }
    private QualificationVersionException invalid(String message) { return new QualificationVersionException(400, "QUALIFICATION_VERSION_INVALID", message); }
    private record QualificationInput(String code, String name, String level, String status, int sortOrder) { }
    private record VersionInput(String name, java.time.LocalDate publishedDate) { }
    private record ExamSubject(String code, String name) { }
    private record DeletedStorageObjects(List<String> sourceObjectKeys, List<String> imageObjectKeys) {
        private boolean empty() { return sourceObjectKeys.isEmpty() && imageObjectKeys.isEmpty(); }
    }
}
