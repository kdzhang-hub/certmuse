package org.dromara.certmuse.catalog.service.impl;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.catalog.domain.bo.SyllabusPublishedDateUpdateBo;
import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeTreeVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusListVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusOverviewBaseVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusOverviewVo;
import org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper;
import org.dromara.certmuse.catalog.service.KnowledgeTreeService;
import org.dromara.certmuse.catalog.support.CatalogJsonSchema;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.dromara.certmuse.catalog.support.CatalogQueryException;
import org.dromara.certmuse.catalog.support.SyllabusPublishedDateException;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeTreeServiceImpl implements KnowledgeTreeService {
    private static final String UPDATE_SYLLABUS_PUBLISHED_DATE = "syllabus_published_date_update";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final KnowledgeTreeMapper mapper;
    private final ImportPersistenceService importPersistence;

    public PageResult<SyllabusListVo> syllabuses(String keyword, String certificationId, String versionName, String status,
                                                 Integer pageNum, Integer pageSize) {
        String normalizedKeyword = normalizeText(keyword, "keyword");
        String normalizedVersionName = normalizeText(versionName, "versionName");
        String normalizedStatus = syllabusStatus(status);
        Long parsedCertificationId = certificationId == null || certificationId.isBlank()
            ? null : parsePositiveId(certificationId, "certificationId");
        int normalizedPageNum = pageNum == null ? 1 : pageNum;
        int normalizedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (normalizedPageNum < 1 || normalizedPageSize < 1 || normalizedPageSize > MAX_PAGE_SIZE) {
            throw invalid("pageNum 或 pageSize 超出允许范围");
        }
        long offset = (long) (normalizedPageNum - 1) * normalizedPageSize;
        try {
            List<SyllabusListVo> rows = mapper.selectSyllabuses(normalizedKeyword, parsedCertificationId,
                normalizedVersionName, normalizedStatus, normalizedPageSize, offset);
            return PageResult.build(rows, mapper.countSyllabuses(normalizedKeyword, parsedCertificationId,
                normalizedVersionName, normalizedStatus));
        } catch (CatalogQueryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw queryFailure("查询考纲列表失败", exception);
        }
    }

    public KnowledgeTreeVo knowledgeTree(String syllabusVersionId) {
        long parsedSyllabusVersionId = parsePositiveId(syllabusVersionId, "syllabusVersionId");
        try {
            SyllabusOverviewBaseVo overview = mapper.selectOverview(parsedSyllabusVersionId);
            if (overview == null) {
                throw new CatalogQueryException(404, "SYLLABUS_VERSION_NOT_FOUND", "考纲版本不存在或不可访问");
            }
            return new KnowledgeTreeVo(
                new SyllabusOverviewVo(overview.id(), overview.certificationId(), overview.certificationName(),
                    overview.versionName(), overview.publishedDate(), overview.createdTime(),
                    mapper.selectLatestKnowledgeImport(parsedSyllabusVersionId)),
                mapper.selectSummary(parsedSyllabusVersionId),
                mapper.selectSubjects(parsedSyllabusVersionId),
                mapper.selectNodes(parsedSyllabusVersionId)
            );
        } catch (CatalogQueryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw queryFailure("查询知识点树失败", exception);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSyllabusPublishedDate(String syllabusVersionId, String requestId, SyllabusPublishedDateUpdateBo command) {
        long parsedId = syllabusPublishedDateId(syllabusVersionId);
        String normalizedRequestId = requestId(requestId);
        if (command == null || command.getPublishedDate() == null) throw publishedDateInvalid(
            "发布日期不能为空", List.of(new org.dromara.certmuse.shared.web.ApiFieldError("publishedDate", "REQUIRED", "发布日期不能为空")));
        String payloadHash = DigestUtil.sha256Hex("syllabusVersionId=" + parsedId + "&publishedDate=" + command.getPublishedDate());
        try {
            CmIdempotencyRecord existing = mapper.selectIdempotency(UPDATE_SYLLABUS_PUBLISHED_DATE, normalizedRequestId);
            if (existing != null) {
                replayPublishedDateUpdate(existing, payloadHash);
                return;
            }
            if (mapper.lockSyllabusVersion(parsedId) == null) {
                throw new SyllabusPublishedDateException(404, "SYLLABUS_VERSION_NOT_FOUND", "考纲版本不存在或不可访问");
            }
            if (mapper.insertIdempotency(IdUtil.getSnowflakeNextId(), UPDATE_SYLLABUS_PUBLISHED_DATE, normalizedRequestId,
                payloadHash, parsedId, OffsetDateTime.now().plusDays(1)) != 1) {
                replayPublishedDateUpdate(mapper.selectIdempotency(UPDATE_SYLLABUS_PUBLISHED_DATE, normalizedRequestId), payloadHash);
                return;
            }
            if (mapper.updateSyllabusPublishedDate(parsedId, command.getPublishedDate(), LoginHelper.getUserId()) != 1) {
                throw new SyllabusPublishedDateException(404, "SYLLABUS_VERSION_NOT_FOUND", "考纲版本不存在或不可访问");
            }
            java.util.Map<String, Object> responseFields = new java.util.LinkedHashMap<>();
            responseFields.put("data", null);
            mapper.completeIdempotency(UPDATE_SYLLABUS_PUBLISHED_DATE, normalizedRequestId, parsedId,
                VersionedJsonDocumentFactory.json(CatalogJsonSchema.SYLLABUS_PUBLISHED_DATE_RESPONSE,
                    responseFields));
        } catch (CatalogQueryException | SyllabusPublishedDateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            String traceId = UUID.randomUUID().toString();
            throw new SyllabusPublishedDateException(500, "SYLLABUS_PUBLISHED_DATE_FAILURE", "更新发布日期失败，请稍后重试",
                true, List.of(), traceId, exception);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeTree(String syllabusVersionId) {
        long parsedId = parsePositiveId(syllabusVersionId, "syllabusVersionId");
        try {
            if (mapper.lockSyllabusVersion(parsedId) == null) {
                throw new CatalogQueryException(404, "SYLLABUS_VERSION_NOT_FOUND", "考纲版本不存在或不可访问");
            }
            if (mapper.countImportingImports(parsedId) > 0) {
                throw new CatalogQueryException(409, "SYLLABUS_VERSION_IMPORT_IN_PROGRESS", "该考纲存在正在写入的导入任务，请稍后再删除");
            }
            DeletedStorageObjects storageObjects = new DeletedStorageObjects(
                distinctObjectKeys(mapper.selectSourceObjectKeysBySyllabusVersion(parsedId)),
                distinctObjectKeys(mapper.selectImageObjectKeysBySyllabusVersion(parsedId))
            );
            mapper.setQualificationCascadeDelete(true);
            try {
                mapper.deleteKnowledgeImportDiffsBySyllabusVersion(parsedId);
                mapper.deleteQuestionsExclusiveToSyllabusVersion(parsedId);
                mapper.deleteSyllabusVersion(parsedId);
            } finally {
                mapper.setQualificationCascadeDelete(false);
            }
            cleanupStorageAfterCommit(storageObjects);
        } catch (CatalogQueryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw queryFailure("删除知识点树失败", exception);
        }
    }

    private static List<String> distinctObjectKeys(List<String> values) {
        return values == null ? List.of() : values.stream()
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
    }

    private void cleanupStorageAfterCommit(DeletedStorageObjects storageObjects) {
        if (storageObjects.empty()) {
            return;
        }
        Runnable cleanup = () -> {
            for (String objectKey : storageObjects.sourceObjectKeys()) {
                importPersistence.enqueueCleanup(objectKey, "unknown", "syllabus_version_delete");
            }
            for (String objectKey : storageObjects.imageObjectKeys()) {
                importPersistence.enqueueImageCleanup(objectKey, "unknown", "syllabus_version_delete");
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    private record DeletedStorageObjects(List<String> sourceObjectKeys, List<String> imageObjectKeys) {
        boolean empty() {
            return sourceObjectKeys.isEmpty() && imageObjectKeys.isEmpty();
        }
    }

    private static String normalizeText(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 100) {
            throw invalid(field + "长度不能超过100字符");
        }
        return value.trim();
    }

    private static long parsePositiveId(String value, String field) {
        try {
            if (value == null || !value.matches("^[1-9][0-9]{0,18}$")) {
                throw new NumberFormatException();
            }
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid(field + "必须是十进制正整数");
        }
    }

    private static long syllabusPublishedDateId(String value) {
        try {
            return parsePositiveId(value, "syllabusVersionId");
        } catch (CatalogQueryException exception) {
            throw publishedDateInvalid("syllabusVersionId必须是十进制正整数",
                List.of(new org.dromara.certmuse.shared.web.ApiFieldError("syllabusVersionId", "INVALID_FORMAT", "必须是十进制正整数")));
        }
    }

    private static String requestId(String value) {
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw publishedDateInvalid("X-Request-Id必须是UUID",
                List.of(new org.dromara.certmuse.shared.web.ApiFieldError("requestId", "INVALID_FORMAT", "X-Request-Id必须是UUID")));
        }
    }

    private static SyllabusPublishedDateException publishedDateInvalid(String message,
                                                                         List<org.dromara.certmuse.shared.web.ApiFieldError> fieldErrors) {
        return new SyllabusPublishedDateException(400, "SYLLABUS_PUBLISHED_DATE_INVALID", message, false, fieldErrors, null, null);
    }

    private static void replayPublishedDateUpdate(CmIdempotencyRecord record, String payloadHash) {
        if (record == null || !java.security.MessageDigest.isEqual(record.payloadHash().getBytes(StandardCharsets.US_ASCII),
            payloadHash.getBytes(StandardCharsets.US_ASCII)) || !"succeeded".equals(record.status()) || record.resourceId() == null) {
            throw new SyllabusPublishedDateException(409, "IDEMPOTENCY_KEY_CONFLICT", "请求号已被使用");
        }
    }

    private static String syllabusStatus(String value) {
        String normalized = normalizeText(value, "status");
        if (normalized == null || "available".equals(normalized) || "empty".equals(normalized)) {
            return normalized;
        }
        throw invalid("status必须是available或empty");
    }

    private static CatalogQueryException invalid(String message) {
        return new CatalogQueryException(400, "CATALOG_QUERY_INVALID", message);
    }

    private CatalogQueryException queryFailure(String message, RuntimeException exception) {
        String traceId = UUID.randomUUID().toString();
        log.error("Catalog knowledge query failed, traceId={}", traceId, exception);
        return new CatalogQueryException(500, "CATALOG_QUERY_FAILURE", message, traceId);
    }
}
