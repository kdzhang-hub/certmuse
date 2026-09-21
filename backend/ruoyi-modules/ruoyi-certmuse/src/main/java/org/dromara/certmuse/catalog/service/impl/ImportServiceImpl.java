package org.dromara.certmuse.catalog.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffCounts;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffInsert;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffRow;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.dromara.certmuse.catalog.domain.TextbookImportDocument;
import org.dromara.certmuse.catalog.domain.TextbookPreviewKnowledgePointLookup;
import org.dromara.certmuse.catalog.domain.TextbookPreviewRecord;
import org.dromara.certmuse.catalog.domain.bo.CompletedImportBatchQueryBo;
import org.dromara.certmuse.catalog.domain.bo.SubjectMappingBo;
import org.dromara.certmuse.catalog.domain.bo.ImportCreateBo;
import org.dromara.certmuse.catalog.domain.bo.PaperImportCreateBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffBatchResolutionBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffQueryBo;
import org.dromara.certmuse.catalog.domain.bo.KnowledgeImportDiffResolutionBo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchDetailVo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchPageVo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchVo;
import org.dromara.certmuse.catalog.domain.vo.ExamSubjectOptionVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchFilterOptionsVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchTypeCountsVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchVo;
import org.dromara.certmuse.catalog.domain.vo.ImportContextOptionsVo;
import org.dromara.certmuse.catalog.domain.vo.ImportFieldErrorVo;
import org.dromara.certmuse.catalog.domain.vo.ImportIssueVo;
import org.dromara.certmuse.catalog.domain.vo.ImportProgressVo;
import org.dromara.certmuse.catalog.domain.vo.ImportValidationAcceptedVo;
import org.dromara.certmuse.catalog.domain.vo.PaperImportProgressVo;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeImportDiffPageVo;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeImportDiffResolutionVo;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeImportDiffVo;
import org.dromara.certmuse.catalog.domain.vo.QuestionImportContextOptionsVo;
import org.dromara.certmuse.catalog.domain.vo.DerivedQuestionSubjectVo;
import org.dromara.certmuse.catalog.domain.vo.DefaultSubjectMappingVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportContextOptionsVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewChunkVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewDocumentVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewSummaryVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookPreviewKnowledgePointVo;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.ImportService;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.dromara.certmuse.catalog.support.ImportJsonSchema;
import org.dromara.certmuse.catalog.support.ImportProtocol;
import org.dromara.certmuse.catalog.support.ImportStorage;
import org.dromara.certmuse.catalog.support.QuestionImportSubjectPreReader;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.oss.exception.S3StorageException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of the content import use cases.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ImportServiceImpl implements ImportService {

    static final long MAX_FILE_SIZE = 10_485_760L;
    static final long MIN_TEXTBOOK_FILE_SIZE = 1_048_576L;
    static final Set<String> MIMES = Set.of(
        "application/x-ndjson",
        "application/jsonl",
        "application/json",
        "text/plain",
        "application/octet-stream"
    );
    private static final long MAX_QUESTION_FILE_SIZE = 67_108_864L;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> COMPLETED_IMPORT_TYPES = Set.of(
        "knowledge_point",
        "question",
        "textbook"
    );

    private final ImportMapper repository;
    private final ImportStorage storage;
    private final ImportPersistenceService persistence;
    private final JsonMapper objectMapper;
    private final VersionedJsonDocumentFactory jsonDocuments;
    private final KnowledgeImportReconciliationService reconciliationService;

    /** Compatibility constructor retained for focused legacy unit tests. */
    public ImportServiceImpl(
        ImportMapper repository,
        ImportStorage storage,
        ImportPersistenceService persistence,
        JsonMapper objectMapper,
        VersionedJsonDocumentFactory jsonDocuments
    ) {
        this(repository, storage, persistence, objectMapper, jsonDocuments, null);
    }

    @Override
    public CompletedImportBatchPageVo completedBatches(CompletedImportBatchQueryBo query) {
        String importType = normalize(query.getImportType());
        if (importType == null) {
            importType = "all";
        }
        if (!"all".equals(importType) && !COMPLETED_IMPORT_TYPES.contains(importType)) {
            throw context("importType", "UNSUPPORTED", "导入类型不支持");
        }

        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        if (pageNum < 1) {
            throw context("pageNum", "OUT_OF_RANGE", "页码最小为1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw context("pageSize", "OUT_OF_RANGE", "每页数量必须为1至100");
        }

        String orderByColumn = normalize(query.getOrderByColumn());
        if (orderByColumn == null) {
            orderByColumn = "completedTime";
        }
        if (!"completedTime".equals(orderByColumn)) {
            throw context("orderByColumn", "UNSUPPORTED", "排序字段不支持");
        }
        String direction = normalize(query.getIsAsc());
        if (direction == null) {
            direction = "desc";
        }
        if (!Set.of("asc", "desc").contains(direction)) {
            throw context("isAsc", "UNSUPPORTED", "排序方向不支持");
        }

        LocalDate startDate = query.getCompletedStartDate();
        LocalDate endDate = query.getCompletedEndDate();
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw context("completedStartDate", "OUT_OF_RANGE", "开始日期不能晚于结束日期");
        }

        OffsetDateTime startTime = atStartOfBusinessDay(startDate, "completedStartDate");
        OffsetDateTime endTime = startOfNextBusinessDay(endDate);
        Long syllabusVersionId = parseOptionalPositive(query.getSyllabusVersionId(), "syllabusVersionId");
        Long uploaderId = parseOptionalPositive(query.getUploaderId(), "uploaderId");
        Long visibleUserId = completedBatchVisibleUserId();
        String keyword = normalize(query.getKeyword());
        String selectedType = "all".equals(importType) ? null : importType;
        long offset = (long) (pageNum - 1) * pageSize;

        List<CompletedImportBatchVo> rows = repository.selectCompletedBatches(
            keyword,
            selectedType,
            syllabusVersionId,
            uploaderId,
            startTime,
            endTime,
            visibleUserId,
            "asc".equals(direction),
            pageSize,
            offset
        );
        long total = repository.countCompletedBatches(
            keyword,
            selectedType,
            syllabusVersionId,
            uploaderId,
            startTime,
            endTime,
            visibleUserId
        );
        ImportBatchTypeCountsVo typeCounts = Optional
            .ofNullable(repository.selectCompletedTypeCounts(visibleUserId))
            .orElseGet(() -> new ImportBatchTypeCountsVo(0, 0, 0, 0));
        return new CompletedImportBatchPageVo(rows, total, typeCounts);
    }

    @Override
    public ImportBatchFilterOptionsVo filterOptions() {
        Long visibleUserId = completedBatchVisibleUserId();
        return new ImportBatchFilterOptionsVo(
            repository.selectCompletedSyllabusOptions(visibleUserId),
            repository.selectCompletedUploaderOptions(visibleUserId)
        );
    }

    @Override
    public CompletedImportBatchDetailVo completedBatch(String id) {
        long batchId = parsePositive(id, "id");
        CompletedImportBatchDetailVo detail = Optional
            .ofNullable(repository.selectCompletedBatchDetail(batchId, completedBatchVisibleUserId()))
            .orElseThrow(ImportServiceImpl::notFound);
        return detail.withCalculatedTotalCount();
    }

    @Override
    public ImportContextOptionsVo contextOptions(String type, String syllabusVersionId, String certificationId) {
        if (!"knowledge_point".equals(type)) {
            throw context("type", "UNSUPPORTED", "仅支持knowledge_point导入上下文");
        }
        List<ExamSubjectOptionVo> subjects = List.of();
        if (syllabusVersionId != null && !syllabusVersionId.isBlank()) {
            long syllabusId = parsePositive(syllabusVersionId, "syllabusVersionId");
            if (repository.selectSyllabusCertification(syllabusId) == null) {
                throw context("syllabusVersionId", "NOT_FOUND", "考纲不存在");
            }
            subjects = repository.selectExamSubjectOptions(syllabusId);
        } else if (certificationId != null && !certificationId.isBlank()) {
            long parsedCertificationId = parsePositive(certificationId, "certificationId");
            if (repository.selectCertificationName(parsedCertificationId) == null) {
                throw context("certificationId", "NOT_FOUND", "资格不存在或已停用");
            }
            subjects = repository.selectExamSubjectOptionsByCertification(parsedCertificationId);
        }
        return new ImportContextOptionsVo(repository.selectSyllabusOptions(), subjects,
            repository.selectCertificationOptionsWithoutSyllabus());
    }

    @Override
    public QuestionImportContextOptionsVo questionContextOptions() {
        return new QuestionImportContextOptionsVo(
            repository.selectSyllabusOptions(), repository.selectCertificationOptions()
        );
    }

    @Override
    public TextbookImportContextOptionsVo textbookContextOptions(String syllabusVersionId) {
        return textbookContextOptions(syllabusVersionId, null);
    }

    @Override
    public TextbookImportContextOptionsVo textbookContextOptions(
        String syllabusVersionId,
        String certificationId
    ) {
        List<org.dromara.certmuse.catalog.domain.vo.SyllabusVersionOptionVo> versions = repository
            .selectSyllabusOptions();
        List<org.dromara.certmuse.catalog.domain.vo.TextbookOptionVo> certifications = repository
            .selectCertificationOptions();
        String selected = normalize(syllabusVersionId);
        Long selectedCertification = parseOptionalPositive(certificationId, "certificationId");
        Long syllabusId = selected == null ? null : parsePositive(selected, "syllabusVersionId");
        if (syllabusId != null) {
            Long syllabusCertification = repository.selectSyllabusCertification(syllabusId);
            if (syllabusCertification == null) {
                throw context("syllabusVersionId", "NOT_FOUND", "考纲版本不存在");
            }
            if (selectedCertification != null && !selectedCertification.equals(syllabusCertification)) {
                throw context("certificationId", "MISMATCH", "考试资格与考纲版本不属于同一资格");
            }
            selectedCertification = syllabusCertification;
        }
        if (selectedCertification != null && normalize(certificationId) != null
            && repository.selectCertificationName(selectedCertification) == null) {
            throw context("certificationId", "NOT_FOUND", "考试资格不存在");
        }
        if (selectedCertification == null) {
            return new TextbookImportContextOptionsVo(
                versions, List.of(), List.of(), List.of(), certifications
            );
        }
        List<ExamSubjectOptionVo> subjects = repository
            .selectExamSubjectOptionsByCertification(selectedCertification);
        List<DefaultSubjectMappingVo> mappings = subjects.stream()
            .map(subject -> new DefaultSubjectMappingVo(subject.subjectNo(), subject.id()))
            .toList();
        return new TextbookImportContextOptionsVo(
            versions,
            subjects,
            mappings,
            syllabusId == null
                ? List.of()
                : repository.selectReplaceableTextbookDrafts(syllabusId, resourceVisibleUserId()),
            certifications
        );
    }

    @Override
    public ImportBatchVo create(
        MultipartFile file,
        String requestId,
        String importType,
        String certificationId,
        String syllabusId,
        String templateVersion,
        String mappingsJson
    ) {
        validateHeader(requestId, importType, templateVersion, file);
        boolean createSyllabus = syllabusId == null || syllabusId.isBlank();
        long certification;
        long syllabus;
        if (createSyllabus) {
            certification = parsePositive(certificationId, "certificationId");
            if (repository.selectCertificationName(certification) == null) {
                throw context("certificationId", "NOT_FOUND", "资格不存在或已停用");
            }
            if (repository.selectSyllabusByCertification(certification) != null) {
                throw context("certificationId", "ALREADY_HAS_SYLLABUS", "该资格已存在考纲，请使用更新考纲");
            }
            syllabus = IdUtil.getSnowflakeNextId();
        } else {
            syllabus = parsePositive(syllabusId, "syllabusVersionId");
            certification = Optional.ofNullable(repository.selectSyllabusCertification(syllabus))
                .orElseThrow(() -> context("syllabusVersionId", "NOT_FOUND", "考纲不存在"));
        }
        List<SubjectMappingBo> mappings = parseMappings(mappingsJson, certification);
        Path temp = null;
        String key = null;
        boolean objectUploaded = false;
        boolean databaseCommitted = false;
        Long batchId = null;
        try {
            temp = Files.createTempFile("certmuse-import-", ".jsonl");
            String hash = copyAndValidate(file, temp);
            String config = canonicalConfig(mappings);
            long payloadScopeId = createSyllabus ? certification : syllabus;
            String payloadHash = payloadHash(importType, payloadScopeId, templateVersion, config, hash);
            CmIdempotencyRecord existing = repository.selectIdempotency(ImportProtocol.KNOWLEDGE_CREATE_ACTION, requestId);
            if (existing != null) {
                return reuseOrConflict(existing, payloadHash);
            }
            long id = IdUtil.getSnowflakeNextId();
            batchId = id;
            key = "imports/knowledge-point/" + id + "/source.jsonl";
            storage.upload(key, temp);
            objectUploaded = true;
            OffsetDateTime createTime = OffsetDateTime.now();
            try {
                if (createSyllabus) {
                    persistence.create(id, requestId, payloadHash, syllabus, key, hash,
                        safeName(file.getOriginalFilename()), file.getSize(), file.getContentType(), config,
                        LoginHelper.getUserId(), LoginHelper.getDeptId(), createTime,
                        batchResponse(ImportJsonSchema.KNOWLEDGE_POINT_PRECHECK_CREATE, id), certification);
                } else {
                    persistence.create(id, requestId, payloadHash, syllabus, key, hash,
                        safeName(file.getOriginalFilename()), file.getSize(), file.getContentType(), config,
                        LoginHelper.getUserId(), LoginHelper.getDeptId(), createTime,
                        batchResponse(ImportJsonSchema.KNOWLEDGE_POINT_PRECHECK_CREATE, id));
                }
                databaseCommitted = true;
            } catch (DataIntegrityViolationException race) {
                compensate(key, hash, "concurrent_create_lost");
                CmIdempotencyRecord concurrentRecord = concurrentRecordOrThrow(
                    ImportProtocol.KNOWLEDGE_CREATE_ACTION, requestId, "knowledge_create", id, race
                );
                return reuseOrConflict(concurrentRecord, payloadHash);
            }
            return new ImportBatchVo(
                Long.toString(id),
                null,
                importType,
                null,
                Long.toString(syllabus),
                null,
                null,
                List.of(),
                templateVersion,
                "uploaded",
                false,
                createTime
            );
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            if (objectUploaded && !databaseCommitted) {
                compensate(key, null, "create_failed");
            }
            throw system("create", batchId, exception);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Temporary file cleanup is best effort.
                }
            }
        }
    }

    @Override
    public ImportBatchVo createQuestion(ImportCreateBo form, String requestId) {
        validateQuestionHeader(requestId, form);
        ImportScope scope = resolveImportScope(
            form.getCertificationId(), form.getKnowledgeSyllabusVersionId(), "knowledgeSyllabusVersionId"
        );
        Long syllabusId = scope.syllabusVersionId();
        long certificationId = scope.certificationId();
        Path temp = null;
        String key = null;
        boolean uploaded = false;
        boolean committed = false;
        try {
            temp = Files.createTempFile("certmuse-question-import-", ".zip");
            String hash = copyAndHash(form.getFile(), temp);
            List<DerivedQuestionSubjectVo> derivedSubjects = new QuestionImportSubjectPreReader(objectMapper)
                .read(temp, repository.selectExamSubjectOptionsByCertification(certificationId));
            Map<String, Object> configValues = new LinkedHashMap<>();
            if (syllabusId != null) {
                configValues.put("knowledge_syllabus_version_id", syllabusId);
            }
            configValues.put("template_version", form.getTemplateVersion());
            configValues.put("certification_id", certificationId);
            configValues.put("certification_name", scope.certificationName());
            configValues.put("derived_subjects", derivedSubjects.stream().map(subject -> Map.of(
                    "subject_no", subject.subjectNo(), "exam_subject_id", Long.parseLong(subject.examSubjectId()), "label", subject.label()
                )).toList());
            String config = jsonDocuments.flat(ImportJsonSchema.QUESTION_ZIP_CONFIG, configValues);
            String payloadHash = questionPayloadHash(
                certificationId, syllabusId, form.getTemplateVersion(), derivedSubjects, hash
            );
            CmIdempotencyRecord existing = repository.selectIdempotency(ImportProtocol.QUESTION_CREATE_ACTION, requestId);
            if (existing != null) return reuseOrConflict(existing, payloadHash);
            long id = IdUtil.getSnowflakeNextId();
            key = "imports/question/" + id + "/source.zip";
            storage.upload(key, temp);
            uploaded = true;
            OffsetDateTime createTime = OffsetDateTime.now();
            try {
                persistence.createQuestion(id, requestId, payloadHash, syllabusId, certificationId, key, hash,
                    safeName(form.getFile().getOriginalFilename()), form.getFile().getSize(), form.getFile().getContentType(), config,
                    LoginHelper.getUserId(), LoginHelper.getDeptId(), createTime,
                    batchResponse(ImportJsonSchema.QUESTION_PRECHECK_CREATE, id));
                committed = true;
            } catch (DataIntegrityViolationException race) {
                compensate(key, hash, "question_concurrent_create_lost");
                return reuseOrConflict(concurrentRecordOrThrow(
                    ImportProtocol.QUESTION_CREATE_ACTION, requestId, "question_create", id, race
                ), payloadHash);
            }
            String syllabus = syllabusId == null ? null : Long.toString(syllabusId);
            return new ImportBatchVo(Long.toString(id), null, "question", null, syllabus, null, syllabus,
                derivedSubjects, form.getTemplateVersion(), "uploaded", false, createTime);
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            if (uploaded && !committed && key != null) compensate(key, null, "question_create_failed");
            throw system("question_create", null, exception);
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    @Override
    public ImportBatchVo createTextbook(ImportCreateBo form, String requestId) {
        validateTextbookHeader(form, requestId);
        String mode = normalize(form.getMode());
        Long syllabusId;
        long certificationId;
        Long documentId;
        boolean createDocument;
        String title;
        String edition;
        TextbookImportDocument target = null;
        if ("create".equals(mode)) {
            if (normalize(form.getDocumentId()) != null) {
                throw context("documentId", "UNSUPPORTED", "新建教材时不能指定documentId");
            }
            ImportScope scope = resolveImportScope(
                form.getCertificationId(), form.getSyllabusVersionId(), "syllabusVersionId"
            );
            syllabusId = scope.syllabusVersionId();
            certificationId = scope.certificationId();
            title = requiredText(form.getTitle(), "title", 500);
            edition = optionalText(form.getEdition(), "edition", 100);
            documentId = null;
            createDocument = true;
        } else {
            documentId = parsePositive(form.getDocumentId(), "documentId");
            target = Optional.ofNullable(repository.selectTextbookDocument(documentId, resourceVisibleUserId()))
                .orElseThrow(() -> new ImportException(404, "TEXTBOOK_NOT_FOUND", "替换目标教材不存在或不可见"));
            if (!"draft".equals(target.status())) {
                throw new ImportException(409, "IMPORT_TARGET_NOT_DRAFT", "替换目标已不是草稿教材");
            }
            certificationId = target.certificationId();
            Long suppliedCertification = parseOptionalPositive(form.getCertificationId(), "certificationId");
            if (suppliedCertification != null && suppliedCertification != certificationId) {
                throw context("certificationId", "MISMATCH", "考试资格与替换目标教材不一致");
            }
            syllabusId = target.syllabusVersionId();
            String suppliedSyllabus = normalize(form.getSyllabusVersionId());
            if (suppliedSyllabus != null) {
                long parsedSyllabus = parsePositive(suppliedSyllabus, "syllabusVersionId");
                Long suppliedSyllabusCertification = repository.selectSyllabusCertification(parsedSyllabus);
                if (suppliedSyllabusCertification == null) {
                    throw context("syllabusVersionId", "NOT_FOUND", "考纲版本不存在");
                }
                if (suppliedSyllabusCertification != certificationId) {
                    throw context("syllabusVersionId", "MISMATCH", "考纲版本与替换目标教材不属于同一资格");
                }
                if (syllabusId != null && syllabusId != parsedSyllabus) {
                    throw context("syllabusVersionId", "MISMATCH", "考纲版本与替换目标教材不一致");
                }
                syllabusId = parsedSyllabus;
            }
            title = target.title();
            edition = target.edition();
            createDocument = false;
        }

        List<SubjectMappingBo> mappings = parseMappings(form.getSubjectMappings(), certificationId);
        Path temp = null;
        String key = null;
        boolean uploaded = false;
        boolean committed = false;
        Long batchId = null;
        try {
            temp = Files.createTempFile("certmuse-textbook-import-", ".jsonl");
            String fileHash = copyAndValidateTextbook(form.getFile(), temp);
            String config = canonicalTextbookConfig(
                mode, certificationId, syllabusId, title, edition, mappings
            );
            String payloadHash = textbookPayloadHash(mode, certificationId, syllabusId,
                createDocument ? null : documentId,
                title, edition, form.getTemplateVersion(), config, fileHash);
            CmIdempotencyRecord existing = repository.selectIdempotency(ImportProtocol.TEXTBOOK_CREATE_ACTION, requestId);
            if (existing != null) {
                return reuseOrConflict(existing, payloadHash);
            }
            long id = IdUtil.getSnowflakeNextId();
            batchId = id;
            key = "imports/document-chunk/" + id + "/source.jsonl";
            storage.upload(key, temp);
            uploaded = true;
            OffsetDateTime createTime = OffsetDateTime.now();
            try {
                persistence.createTextbook(
                    id, documentId, createDocument, requestId, payloadHash, certificationId, syllabusId,
                    title, edition,
                    key, fileHash, safeName(form.getFile().getOriginalFilename()), form.getFile().getSize(),
                    form.getFile().getContentType(), config, LoginHelper.getUserId(), LoginHelper.getDeptId(),
                    createTime, batchResponse(ImportJsonSchema.TEXTBOOK_PRECHECK_CREATE, id)
                );
                committed = true;
            } catch (DataIntegrityViolationException race) {
                compensate(key, fileHash, "textbook_concurrent_create_lost");
                CmIdempotencyRecord concurrent = concurrentRecordOrThrow(
                    ImportProtocol.TEXTBOOK_CREATE_ACTION, requestId, "textbook_create", id, race
                );
                return reuseOrConflict(concurrent, payloadHash);
            }
            return new ImportBatchVo(
                Long.toString(id), documentId == null ? null : Long.toString(documentId), "document_chunk", mode, null, null, null,
                List.of(), form.getTemplateVersion(), "uploaded", false, createTime
            );
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            if (uploaded && !committed && key != null) {
                compensate(key, null, "textbook_create_failed");
            }
            throw system("textbook_create", batchId, exception);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Temporary file cleanup is best effort.
                }
            }
        }
    }

    @Override
    public ImportBatchVo createPaper(PaperImportCreateBo form, String requestId) {
        validatePaperHeader(requestId, form);
        long certificationId = parsePositive(form.getCertificationId(), "certificationId");
        Long syllabusId = repository.selectLatestSyllabusForCertification(certificationId);
        if (syllabusId == null) throw new ImportException(400, "PAPER_SYLLABUS_UNRESOLVED", "该考试资格没有可用考纲版本");
        Path temp = null;
        String key = null;
        boolean uploaded = false;
        boolean committed = false;
        try {
            temp = Files.createTempFile("certmuse-paper-import-", ".zip");
            String hash = copyAndHash(form.getFile(), temp);
            List<DerivedQuestionSubjectVo> subjects = new QuestionImportSubjectPreReader(objectMapper)
                .read(temp, repository.selectExamSubjectOptions(syllabusId));
            String config = jsonDocuments.flat(ImportJsonSchema.QUESTION_ZIP_CONFIG, Map.of(
                "knowledge_syllabus_version_id", syllabusId,
                "template_version", "question-zip/1.0",
                "certification_id", certificationId,
                "certification_name", repository.selectCertificationNameBySyllabus(syllabusId),
                "derived_subjects", subjects.stream().map(subject -> Map.of(
                    "subject_no", subject.subjectNo(), "exam_subject_id", Long.parseLong(subject.examSubjectId()), "label", subject.label()
                )).toList()
            ));
            String payloadHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (certificationId + "\n" + form.getCollectionName().trim() + "\n" + form.getCollectionType()
                    + "\n" + form.getDurationMinutes() + "\n" + Objects.toString(form.getExamYear(), "")
                    + "\n" + Objects.toString(form.getExamMonth(), "") + "\n" + Objects.toString(form.getPaperTypeCode(), "")
                    + "\n" + Objects.toString(form.getPaperTypeName(), "") + "\n" + hash).getBytes(StandardCharsets.UTF_8)
            ));
            CmIdempotencyRecord existing = repository.selectIdempotency(ImportProtocol.PAPER_CREATE_ACTION, requestId);
            if (existing != null) return reuseOrConflict(existing, payloadHash);
            long id = IdUtil.getSnowflakeNextId();
            key = "imports/paper/" + id + "/source.zip";
            storage.upload(key, temp);
            uploaded = true;
            OffsetDateTime createTime = OffsetDateTime.now();
            try {
                if ("PAST_PAPER".equals(form.getCollectionType())) {
                    persistence.createPaper(id, requestId, payloadHash, syllabusId, certificationId, form.getCollectionName(),
                        form.getCollectionType(), form.getDurationMinutes(), form.getExamYear(), form.getExamMonth(),
                        form.getPaperTypeCode(), form.getPaperTypeName(), key, hash, safeName(form.getFile().getOriginalFilename()),
                        form.getFile().getSize(), form.getFile().getContentType(), config, LoginHelper.getUserId(), LoginHelper.getDeptId(),
                        createTime, batchResponse(ImportJsonSchema.PAPER_PRECHECK_CREATE, id));
                } else {
                    persistence.createPaper(id, requestId, payloadHash, syllabusId, certificationId, form.getCollectionName(),
                        form.getCollectionType(), form.getDurationMinutes(), key, hash, safeName(form.getFile().getOriginalFilename()),
                        form.getFile().getSize(), form.getFile().getContentType(), config, LoginHelper.getUserId(), LoginHelper.getDeptId(),
                        createTime, batchResponse(ImportJsonSchema.PAPER_PRECHECK_CREATE, id));
                }
                committed = true;
            } catch (DataIntegrityViolationException race) {
                compensate(key, hash, "paper_concurrent_create_lost");
                return reuseOrConflict(concurrentRecordOrThrow(
                    ImportProtocol.PAPER_CREATE_ACTION, requestId, "paper_create", id, race
                ), payloadHash);
            }
            return new ImportBatchVo(Long.toString(id), null, "paper", null, Long.toString(syllabusId), null,
                Long.toString(syllabusId), subjects, "question-zip/1.0", "uploaded", false, createTime);
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            if (uploaded && !committed && key != null) compensate(key, null, "paper_create_failed");
            throw system("paper_create", null, exception);
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    @Override
    public ImportValidationAcceptedVo validate(String idText) {
        CmImportBatch batch = visible(idText);
        boolean accepted = false;
        if ("failed".equals(batch.status())) {
            throw new ImportException(409, "IMPORT_BATCH_STATE_INVALID", "当前批次状态不允许预检");
        }
        if ("uploaded".equals(batch.status())) {
            if ("question".equals(batch.importType())) {
                accepted = persistence.enqueueQuestionValidation(batch.id());
            } else if ("document_chunk".equals(batch.importType())) {
                accepted = persistence.enqueueTextbookValidation(batch.id());
            } else {
                try {
                accepted = persistence.enqueueValidation(batch.id());
                } catch (RuntimeException exception) {
                throw system("validate_enqueue", batch.id(), exception);
                }
            }
        }
        CmImportBatch current = Optional.ofNullable(repository.selectById(batch.id()))
            .orElseThrow(ImportServiceImpl::notFound);
        return new ImportValidationAcceptedVo(
            Long.toString(current.id()),
            current.status(),
            current.currentStage(),
            accepted
        );
    }

    @Override
    public ImportValidationAcceptedVo confirm(String idText, String requestId) {
        validateRequestId(requestId);
        CmImportBatch batch = visible(idText);
        boolean question = "question".equals(batch.importType());
        boolean textbook = "document_chunk".equals(batch.importType());
        if (textbook) {
            validateUuidRequestId(requestId);
        }
        String action = question
            ? ImportProtocol.QUESTION_CONFIRM_ACTION
            : textbook ? ImportProtocol.TEXTBOOK_CONFIRM_ACTION : ImportProtocol.KNOWLEDGE_CONFIRM_ACTION;
        if (!question && !textbook && (
            batch.failedCount() != 0 || batch.baselineHash() == null || batch.resolutionHash() == null
                || repository.countPendingKnowledgeImportDiffs(batch.id()) != 0
        )) {
            throw new ImportException(409, "KNOWLEDGE_DIFF_UNRESOLVED", "知识点差异尚未全部确认或预检存在错误");
        }
        String payloadHash = confirmationPayloadHash(
            batch.id(), action, !question && !textbook ? batch.resolutionHash() : null
        );
        CmIdempotencyRecord existing = repository.selectIdempotency(action, requestId);
        if (existing != null) {
            return reuseConfirmation(existing, payloadHash);
        }
        if (!"waiting_confirm".equals(batch.status()) || batch.validCount() <= 0 || (textbook && batch.failedCount() != 0)) {
            throw stateInvalid();
        }
        try {
            if (question) {
                persistence.acceptQuestionConfirmation(batch.id(), requestId, payloadHash, LoginHelper.getUserId(), OffsetDateTime.now(),
                    batchResponse(ImportJsonSchema.QUESTION_IMPORT_CONFIRM, batch.id()));
            } else if (textbook) {
                persistence.acceptTextbookConfirmation(
                    batch.id(), requestId, payloadHash, LoginHelper.getUserId(), OffsetDateTime.now(),
                    batchResponse(ImportJsonSchema.TEXTBOOK_IMPORT_CONFIRM, batch.id())
                );
            } else persistence.acceptConfirmation(
                batch.id(),
                requestId,
                payloadHash,
                LoginHelper.getUserId(),
                OffsetDateTime.now(),
                batchResponse(ImportJsonSchema.KNOWLEDGE_POINT_IMPORT_CONFIRM, batch.id())
            );
        } catch (DataIntegrityViolationException race) {
            CmIdempotencyRecord concurrent = concurrentRecordOrThrow(
                action, requestId, "confirm", batch.id(), race
            );
            return reuseConfirmation(concurrent, payloadHash);
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw system("confirm", batch.id(), exception);
        }
        CmImportBatch current = Optional.ofNullable(repository.selectById(batch.id()))
            .orElseThrow(ImportServiceImpl::notFound);
        return confirmationVo(current, true);
    }

    @Override
    public ImportValidationAcceptedVo validatePaper(String idText, String requestId) {
        validateRequestId(requestId);
        CmImportBatch batch = visible(idText);
        if (!"paper".equals(batch.importType())) {
            throw new ImportException(404, "PAPER_IMPORT_NOT_FOUND", "试卷导入批次不存在");
        }
        String payloadHash = confirmationPayloadHash(batch.id(), ImportProtocol.PAPER_VALIDATE_ACTION, null);
        CmIdempotencyRecord existing = repository.selectIdempotency(ImportProtocol.PAPER_VALIDATE_ACTION, requestId);
        if (existing != null) {
            return reusePaperValidation(existing, payloadHash);
        }
        if (!"uploaded".equals(batch.status())) {
            throw new ImportException(409, "PAPER_IMPORT_STATE_CONFLICT", "当前批次状态不允许预检");
        }
        try {
            persistence.acceptPaperValidation(batch.id(), requestId, payloadHash,
                batchResponse(ImportJsonSchema.PAPER_PRECHECK_JOB, batch.id()));
        } catch (DataIntegrityViolationException race) {
            return reusePaperValidation(concurrentRecordOrThrow(
                ImportProtocol.PAPER_VALIDATE_ACTION, requestId, "paper_validate", batch.id(), race
            ), payloadHash);
        }
        CmImportBatch current = Optional.ofNullable(repository.selectById(batch.id())).orElseThrow(ImportServiceImpl::notFound);
        return confirmationVo(current, true);
    }

    @Override
    public ImportValidationAcceptedVo confirmPaper(String idText, String requestId) {
        validateRequestId(requestId);
        CmImportBatch batch = visible(idText);
        if (!"paper".equals(batch.importType())) throw new ImportException(404, "PAPER_IMPORT_NOT_FOUND", "试卷导入批次不存在");
        String payloadHash = confirmationPayloadHash(batch.id(), ImportProtocol.PAPER_CONFIRM_ACTION, null);
        CmIdempotencyRecord existing = repository.selectIdempotency(ImportProtocol.PAPER_CONFIRM_ACTION, requestId);
        if (existing != null) return reuseConfirmation(existing, payloadHash);
        if (!"waiting_confirm".equals(batch.status()) || batch.validCount() <= 0 || batch.warningCount() != 0 || batch.failedCount() != 0) {
            throw new ImportException(422, "PAPER_IMPORT_PRECHECK_FAILED", "试卷预检存在问题，不能确认导入");
        }
        persistence.acceptPaperConfirmation(batch.id(), requestId, payloadHash, LoginHelper.getUserId(), OffsetDateTime.now(),
            batchResponse(ImportJsonSchema.PAPER_IMPORT_CONFIRM, batch.id()));
        CmImportBatch current = Optional.ofNullable(repository.selectById(batch.id())).orElseThrow(ImportServiceImpl::notFound);
        return confirmationVo(current, true);
    }

    @Override
    public PaperImportProgressVo paperProgress(String idText) {
        CmImportBatch batch = visible(idText);
        if (!"paper".equals(batch.importType())) throw new ImportException(404, "PAPER_IMPORT_NOT_FOUND", "试卷导入批次不存在");
        var paper = Optional.ofNullable(repository.selectPaperImport(batch.id())).orElseThrow(ImportServiceImpl::notFound);
        return new PaperImportProgressVo(Long.toString(batch.id()), batch.status(), batch.currentStage(), batch.progressPercent(),
            batch.validCount() + batch.failedCount(), batch.validCount(), batch.warningCount(), batch.failedCount(),
            batch.syllabusVersionId() == null ? null : Long.toString(batch.syllabusVersionId()),
            batch.syllabusVersionId() == null ? null : repository.selectSyllabusDisplayName(batch.syllabusVersionId()),
            paper.collectionId() == null ? null : Long.toString(paper.collectionId()),
            paper.collectionRevisionId() == null ? null : Long.toString(paper.collectionRevisionId()), batch.startedTime(), batch.finishedTime(),
            "failed".equals(batch.status()) ? batch.traceId() : null);
    }

    @Override
    public ImportProgressVo progress(String idText) {
        CmImportBatch batch = visible(idText);
        return new ImportProgressVo(
            Long.toString(batch.id()),
            batch.status(),
            batch.currentStage(),
            batch.progressPercent(),
            batch.validCount() + batch.failedCount(),
            batch.validCount(),
            batch.warningCount(),
            batch.failedCount(),
            batch.startedTime(),
            batch.finishedTime(),
            "failed".equals(batch.status()) ? batch.traceId() : null,
            "knowledge_point".equals(batch.importType())
                && repository.countKnowledgeImportDiffs(batch.id(), null, null, null, false, null, null) > 0
        );
    }

    @Override
    public KnowledgeImportDiffPageVo knowledgeDiff(String idText, KnowledgeImportDiffQueryBo query) {
        CmImportBatch batch = knowledgeDiffBatch(idText);
        Long subjectId = parseOptionalPositive(query.getExamSubjectId(), "examSubjectId");
        String status = normalize(query.getResolutionStatus());
        String action = normalize(query.getAction());
        if (status != null && !Set.of("not_required", "pending", "manual_confirmed").contains(status)) {
            throw context("resolutionStatus", "UNSUPPORTED", "差异确认状态不支持");
        }
        if (action != null && !Set.of("unchanged", "update", "move", "add", "delete").contains(action)) {
            throw context("action", "UNSUPPORTED", "差异动作不支持");
        }
        if (query.getMinScore() != null && query.getMaxScore() != null
            && query.getMinScore().compareTo(query.getMaxScore()) > 0) {
            throw context("minScore", "OUT_OF_RANGE", "最低分不能高于最高分");
        }
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        long offset = (long) (pageNum - 1) * pageSize;
        boolean excludeUnchanged = Boolean.TRUE.equals(query.getExcludeUnchanged());
        List<KnowledgeImportDiffVo> rows = repository.selectKnowledgeImportDiffs(
            batch.id(), subjectId, status, action, excludeUnchanged, query.getMinScore(), query.getMaxScore(), pageSize, offset
        ).stream().map(this::toKnowledgeDiffVo).toList();
        long total = repository.countKnowledgeImportDiffs(
            batch.id(), subjectId, status, action, excludeUnchanged, query.getMinScore(), query.getMaxScore()
        );
        KnowledgeImportDiffCounts counts = repository.selectKnowledgeImportDiffCounts(batch.id());
        return new KnowledgeImportDiffPageVo(
            rows, total, counts.unchangedCount(), counts.updateCount(), counts.moveCount(), counts.addCount(),
            counts.deleteCount(), counts.pendingCount(), counts.affectedQuestionCount(), counts.offlineQuestionCount()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeImportDiffResolutionVo resolveKnowledgeDiff(
        String idText, String diffId, KnowledgeImportDiffResolutionBo command, String requestId
    ) {
        command.setDiffId(diffId);
        KnowledgeImportDiffBatchResolutionBo batch = new KnowledgeImportDiffBatchResolutionBo();
        batch.setResolutions(List.of(command));
        return resolveKnowledgeDiffBatch(idText, batch, requestId, ImportProtocol.KNOWLEDGE_DIFF_RESOLVE_ACTION);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeImportDiffResolutionVo resolveKnowledgeDiffBatch(
        String idText, KnowledgeImportDiffBatchResolutionBo command, String requestId
    ) {
        return resolveKnowledgeDiffBatch(
            idText, command, requestId, ImportProtocol.KNOWLEDGE_DIFF_BATCH_RESOLVE_ACTION
        );
    }

    private KnowledgeImportDiffResolutionVo resolveKnowledgeDiffBatch(
        String idText,
        KnowledgeImportDiffBatchResolutionBo command,
        String requestId,
        String actionCode
    ) {
        validateRequestId(requestId);
        CmImportBatch batch = knowledgeDiffBatch(idText);
        if (command == null || command.getResolutions() == null || command.getResolutions().isEmpty()) {
            throw context("resolutions", "REQUIRED", "至少需要一条人工决议");
        }
        if (command.getResolutions().size() > 500) {
            throw new ImportException(400, "KNOWLEDGE_DIFF_BATCH_TOO_LARGE", "单次最多确认500条差异");
        }
        String payloadHash = knowledgeResolutionPayloadHash(batch.id(), command.getResolutions());
        CmIdempotencyRecord replay = repository.selectIdempotency(actionCode, requestId);
        if (replay != null) {
            if (!MessageDigest.isEqual(
                replay.payloadHash().getBytes(StandardCharsets.US_ASCII),
                payloadHash.getBytes(StandardCharsets.US_ASCII)
            ) || replay.resourceId() == null || replay.resourceId() != batch.id()
                || !"succeeded".equals(replay.status())) {
                throw new ImportException(409, "IDEMPOTENCY_KEY_CONFLICT", "请求号已被其他差异决议使用");
            }
            return currentKnowledgeResolution(batch, command.getResolutions().size());
        }
        if (!"waiting_confirm".equals(batch.status())) {
            throw new ImportException(409, "IMPORT_BATCH_STATE_INVALID", "当前批次状态不允许修改差异");
        }
        repository.insertIdempotency(IdUtil.getSnowflakeNextId(), actionCode, requestId, payloadHash);
        Set<Long> resolvedIds = new HashSet<>();
        for (KnowledgeImportDiffResolutionBo resolution : command.getResolutions()) {
            long diffId = parsePositive(resolution.getDiffId(), "diffId");
            if (!resolvedIds.add(diffId)) throw diffConflict("同一差异不能在一次请求中重复处理");
            resolveKnowledgeDiff(batch, diffId, resolution);
        }
        String hash = reconciliationService.currentResolutionHash(batch.id());
        if (repository.updateKnowledgeImportResolutionHash(batch.id(), hash) != 1) {
            throw new ImportException(409, "IMPORT_BATCH_STATE_INVALID", "当前批次状态不允许修改差异");
        }
        KnowledgeImportDiffResolutionVo result = new KnowledgeImportDiffResolutionVo(
            Long.toString(batch.id()), resolvedIds.size(), repository.countPendingKnowledgeImportDiffs(batch.id()), hash
        );
        if (repository.completeIdempotency(actionCode, requestId, batch.id(), resolutionResponse(result)) != 1) {
            throw new IllegalStateException("Knowledge difference idempotency completion failed");
        }
        return result;
    }

    private KnowledgeImportDiffResolutionVo currentKnowledgeResolution(CmImportBatch batch, int resolvedCount) {
        return new KnowledgeImportDiffResolutionVo(
            Long.toString(batch.id()), resolvedCount, repository.countPendingKnowledgeImportDiffs(batch.id()),
            reconciliationService.currentResolutionHash(batch.id())
        );
    }

    private void resolveKnowledgeDiff(
        CmImportBatch batch,
        long diffId,
        KnowledgeImportDiffResolutionBo command
    ) {
        KnowledgeImportDiffRow diff = Optional.ofNullable(repository.selectKnowledgeImportDiffForUpdate(batch.id(), diffId))
            .orElseThrow(() -> new ImportException(404, "KNOWLEDGE_DIFF_NOT_FOUND", "知识点差异不存在或不可访问"));
        if ("not_required".equals(diff.getResolutionStatus())) {
            throw diffConflict("完全一致的知识点不需要确认");
        }
        String decision = normalize(command.getDecision());
        Long confirmedId;
        String action;
        if ("approve".equals(decision)) {
            confirmedId = diff.getAction().equals("update") || diff.getAction().equals("move")
                ? diff.getOldKnowledgePointId() : null;
            action = diff.getAction();
        } else if ("reject".equals(decision)) {
            confirmedId = null;
            action = diff.getAction();
        } else {
            throw diffConflict("人工确认决议不支持");
        }
        if (confirmedId != null) {
            KnowledgeImportPointRow old = Optional.ofNullable(repository.selectCurrentKnowledgePoint(
                requiredSyllabusVersionId(batch), confirmedId
            )).orElseThrow(() -> diffConflict("所选旧知识点不存在或已失效"));
            if (old.getExamSubjectId() != diff.getExamSubjectId()) throw diffConflict("新旧知识点必须属于同一考试科目");
            if (repository.countConfirmedKnowledgePointUse(batch.id(), confirmedId, diffId) > 0) {
                throw diffConflict("一个旧知识点不能被多个新知识点继承");
            }
            if (Objects.equals(confirmedId, diff.getSuggestedKnowledgePointId())) {
                KnowledgeImportDiffRow orphan = repository.selectPendingDeleteDiffForOldForUpdate(batch.id(), confirmedId);
                if (orphan != null && repository.deletePendingKnowledgeImportDiff(batch.id(), orphan.getId()) != 1) {
                    throw diffConflict("旧知识点删除决议重分配失败");
                }
            }
            if (!Objects.equals(confirmedId, diff.getSuggestedKnowledgePointId())) {
                KnowledgeImportDiffRow selectedDelete = repository.selectPendingDeleteDiffForOldForUpdate(
                    batch.id(), confirmedId
                );
                if (selectedDelete != null
                    && repository.deletePendingKnowledgeImportDiff(batch.id(), selectedDelete.getId()) != 1) {
                    throw diffConflict("所选旧知识点删除决议重分配失败");
                }
                ensurePendingDelete(batch, diff, diff.getSuggestedKnowledgePointId(), diffId);
            }
        } else if ("add".equals(action) && "approve".equals(decision)) {
            ensurePendingDelete(batch, diff, diff.getSuggestedKnowledgePointId(), diffId);
        }
        if (repository.updateKnowledgeImportDiffResolution(
            batch.id(), diffId, action, confirmedId, decision, LoginHelper.getUserId()
        ) != 1) throw diffConflict("知识点差异确认失败");
        if ("move".equals(diff.getAction()) || ("add".equals(diff.getAction()) && "reject".equals(decision))) {
            repository.cascadeKnowledgeImportDiffResolution(batch.id(), diffId, decision, LoginHelper.getUserId());
        }
    }

    private void ensurePendingDelete(
        CmImportBatch batch,
        KnowledgeImportDiffRow source,
        Long knowledgePointId,
        long sourceDiffId
    ) {
        if (knowledgePointId == null
            || repository.countConfirmedKnowledgePointUse(batch.id(), knowledgePointId, sourceDiffId) > 0
            || repository.selectPendingDeleteDiffForOldForUpdate(batch.id(), knowledgePointId) != null) {
            return;
        }
            KnowledgeImportDiffInsert delete = new KnowledgeImportDiffInsert(
                IdUtil.getSnowflakeNextId(), batch.id(), null, knowledgePointId,
                knowledgePointId, null, source.getExamSubjectId(), "delete", "pending", null,
                jsonDocuments.flat(ImportJsonSchema.KNOWLEDGE_IMPORT_MATCH_EVIDENCE, Map.of()),
                jsonDocuments.flat(ImportJsonSchema.KNOWLEDGE_IMPORT_CHANGED_FIELDS,
                    Map.of("fields", List.of("deleted"))),
                LoginHelper.getUserId()
            );
            if (repository.insertKnowledgeImportDiffs(List.of(delete)) != 1) {
                throw diffConflict("旧知识点删除差异补充失败");
            }
    }

    private KnowledgeImportDiffVo toKnowledgeDiffVo(KnowledgeImportDiffRow row) {
        JsonNode evidence = objectMapper.readTree(row.getMatchEvidence());
        JsonNode changed = objectMapper.readTree(row.getChangedFields()).path("fields");
        List<String> fields = new ArrayList<>();
        changed.forEach(value -> fields.add(value.asText()));
        return new KnowledgeImportDiffVo(
            Long.toString(row.getId()), Long.toString(row.getExamSubjectId()), row.getSubjectName(),
            point(row.getOldKnowledgePointId(), row.getOldNumber(), row.getOldTitle()),
            point(null, row.getNewNumber(), row.getNewTitle()),
            point(row.getSuggestedKnowledgePointId(), row.getSuggestedNumber(), row.getSuggestedTitle()),
            point(row.getConfirmedKnowledgePointId(), row.getConfirmedNumber(), row.getConfirmedTitle()),
            row.getAction(), row.getResolutionStatus(), row.getResolutionDecision(),
            row.getParentDiffId() == null ? null : Long.toString(row.getParentDiffId()), row.getMatchScore(),
            new KnowledgeImportDiffVo.EvidenceVo(
                decimal(evidence, "title_score"), decimal(evidence, "parent_score"),
                decimal(evidence, "description_score"), decimal(evidence, "children_score"),
                decimal(evidence, "number_score"), decimal(evidence, "total_score"),
                decimal(evidence, "candidate_gap"), evidence.path("possible_move").asBoolean(),
                evidence.path("ambiguous").asBoolean()
            ), fields
        );
    }

    private static KnowledgeImportDiffVo.PointVo point(Long id, String number, String title) {
        return id == null && number == null && title == null ? null
            : new KnowledgeImportDiffVo.PointVo(id == null ? null : Long.toString(id), number, title);
    }

    private static java.math.BigDecimal decimal(JsonNode node, String field) {
        String value = node.path(field).asText("0");
        try { return new java.math.BigDecimal(value); }
        catch (NumberFormatException ignored) { return java.math.BigDecimal.ZERO; }
    }

    private CmImportBatch knowledgeDiffBatch(String idText) {
        CmImportBatch batch = visible(idText);
        if (!"knowledge_point".equals(batch.importType())) {
            throw new ImportException(404, "KNOWLEDGE_DIFF_NOT_FOUND", "知识点差异不存在或不可访问");
        }
        return batch;
    }

    @Override
    public TextbookImportPreviewVo preview(String idText, Integer pageNum, Integer pageSize) {
        CmImportBatch batch = visible(idText);
        if (!"document_chunk".equals(batch.importType())) {
            throw new ImportException(400, "IMPORT_PREVIEW_UNSUPPORTED", "该导入类型不支持教材预览");
        }
        if (!Set.of("waiting_confirm", "completed", "partial_failed", "failed", "cancelled").contains(batch.status())) {
            throw stateInvalid();
        }
        int resolvedPageNum = pageNum == null ? 1 : pageNum;
        int resolvedPageSize = pageSize == null ? 20 : pageSize;
        if (resolvedPageNum < 1) {
            throw context("pageNum", "OUT_OF_RANGE", "页码最小为1");
        }
        if (resolvedPageSize < 1 || resolvedPageSize > 100) {
            throw context("pageSize", "OUT_OF_RANGE", "每页数量必须为1至100");
        }
        TextbookImportPreviewDocumentVo document = Optional
            .ofNullable(repository.selectTextbookPreviewDocument(batch.id()))
            .orElseThrow(ImportServiceImpl::notFound);
        TextbookImportPreviewSummaryVo summary = Optional
            .ofNullable(repository.selectTextbookPreviewSummary(batch.id()))
            .orElseGet(() -> new TextbookImportPreviewSummaryVo(0, 0, 0, 0, 0, 0, 0, 0));
        List<TextbookPreviewRecord> previewRecords = repository.selectTextbookPreviewRecords(
            batch.id(), resolvedPageSize, (resolvedPageNum - 1) * resolvedPageSize
        );
        Map<Integer, Long> subjectMappings = previewSubjectMappings(batch.parseConfig());
        Map<String, String> knowledgePointTitles = previewKnowledgePointTitles(batch, previewRecords, subjectMappings);
        List<TextbookImportPreviewChunkVo> rows = previewRecords.stream()
            .map(row -> previewChunk(row, subjectMappings, knowledgePointTitles))
            .toList();
        return new TextbookImportPreviewVo(
            document,
            summary,
            List.of(),
            PageResult.build(rows, repository.countTextbookPreviewRecords(batch.id()))
        );
    }

    @Override
    public PageResult<ImportIssueVo> issues(
        String idText,
        Integer pageNum,
        Integer pageSize,
        String severity,
        String issueCode,
        String order,
        String direction
    ) {
        CmImportBatch batch = visible(idText);
        int resolvedPageNum = pageNum == null ? 1 : pageNum;
        int resolvedPageSize = pageSize == null ? 20 : pageSize;
        if (resolvedPageNum < 1) {
            throw context("pageNum", "OUT_OF_RANGE", "页码最小为1");
        }
        if (resolvedPageSize < 1 || resolvedPageSize > 100) {
            throw context("pageSize", "OUT_OF_RANGE", "每页数量必须为1至100");
        }
        if (severity != null && !Set.of("warning", "error").contains(severity)) {
            throw context("severity", "UNSUPPORTED", "问题级别不支持");
        }
        String sort = order == null ? "lineNo" : order;
        if (!Set.of("lineNo", "severity", "issueCode", "createTime").contains(sort)) {
            throw context("orderByColumn", "UNSUPPORTED", "排序字段不支持");
        }
        String resolvedDirection = direction == null ? "asc" : direction;
        boolean ascending = Set.of("asc", "ascending").contains(resolvedDirection);
        if (!ascending && !Set.of("desc", "descending").contains(resolvedDirection)) {
            throw context("isAsc", "UNSUPPORTED", "排序方向不支持");
        }
        return PageResult.build(
            repository.selectIssues(
                batch.id(),
                severity,
                issueCode,
                sort,
                ascending,
                resolvedPageSize,
                (resolvedPageNum - 1) * resolvedPageSize
            ),
            repository.countIssues(batch.id(), severity, issueCode)
        );
    }

    private CmImportBatch visible(String id) {
        long value = parsePositive(id, "id");
        return Optional.ofNullable(repository.selectVisible(value, resourceVisibleUserId()))
            .orElseThrow(ImportServiceImpl::notFound);
    }

    private Map<Integer, Long> previewSubjectMappings(String parseConfig) {
        Map<Integer, Long> mappings = new LinkedHashMap<>();
        objectMapper.readTree(parseConfig).path("subject_mappings").forEach(mapping -> {
            int subjectNo = mapping.path("subject_no").intValue();
            long subjectId = mapping.path("exam_subject_id").longValue();
            if (subjectNo > 0 && subjectId > 0) {
                mappings.put(subjectNo, subjectId);
            }
        });
        return mappings;
    }

    private Map<String, String> previewKnowledgePointTitles(
        CmImportBatch batch,
        List<TextbookPreviewRecord> records,
        Map<Integer, Long> subjectMappings
    ) {
        Set<String> codes = new java.util.LinkedHashSet<>();
        for (TextbookPreviewRecord row : records) {
            objectMapper.readTree(row.rawRecord()).path("record").path("knowledge_points").forEach(point -> {
                String code = point.path("code").textValue();
                if (code != null && !code.isBlank()) {
                    codes.add(code);
                }
            });
        }
        if (codes.isEmpty() || subjectMappings.isEmpty()) {
            return Map.of();
        }
        List<TextbookPreviewKnowledgePointLookup> lookups = Optional.ofNullable(
            repository.selectTextbookPreviewKnowledgePointLookups(
                batch.id(), List.copyOf(subjectMappings.values()), List.copyOf(codes)
            )
        ).orElseGet(List::of);
        Map<String, String> titles = new LinkedHashMap<>();
        for (TextbookPreviewKnowledgePointLookup lookup : lookups) {
            titles.put(lookup.examSubjectId() + ":" + lookup.syllabusNumber(), lookup.syllabusTitle());
        }
        return titles;
    }

    private TextbookImportPreviewChunkVo previewChunk(
        TextbookPreviewRecord row,
        Map<Integer, Long> subjectMappings,
        Map<String, String> knowledgePointTitles
    ) {
        JsonNode record = objectMapper.readTree(row.rawRecord()).path("record");
        List<String> headings = new java.util.ArrayList<>();
        record.path("heading_path").forEach(node -> headings.add(node.textValue()));
        List<TextbookPreviewKnowledgePointVo> points = new java.util.ArrayList<>();
        record.path("knowledge_points").forEach(node -> {
            int subjectNo = node.path("subject_no").intValue();
            String code = node.path("code").textValue();
            Long subjectId = subjectMappings.get(subjectNo);
            String title = subjectId == null || code == null ? null : knowledgePointTitles.get(subjectId + ":" + code);
            points.add(new TextbookPreviewKnowledgePointVo(
                subjectNo, code, Objects.requireNonNullElse(title, "知识点名称不可用")
            ));
        });
        return new TextbookImportPreviewChunkVo(
            row.lineNo(),
            row.sourceKey(),
            record.path("chunk_no").intValue(),
            nullableText(record.get("heading")),
            List.copyOf(headings),
            previewText(record.path("content").textValue()),
            nullablePositiveInt(record.get("page_start")),
            nullablePositiveInt(record.get("page_end")),
            List.copyOf(points),
            row.issueCount()
        );
    }

    private static String previewText(String value) {
        if (value == null || value.length() <= 200) {
            return value;
        }
        return value.substring(0, 200);
    }

    private static String nullableText(JsonNode node) {
        return node == null || node.isNull() ? null : node.textValue();
    }

    private static Integer nullablePositiveInt(JsonNode node) {
        return node == null || node.isNull() ? null : node.intValue();
    }

    private List<SubjectMappingBo> parseMappings(String json, long certification) {
        try {
            List<SubjectMappingBo> mappings = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (mappings.isEmpty() || mappings.size() > 3) {
                throw context("subjectMappings", "OUT_OF_RANGE", "科目映射数量必须为1至3");
            }
            Set<Integer> subjectNumbers = new HashSet<>();
            Set<Long> subjectIds = new HashSet<>();
            for (int index = 0; index < mappings.size(); index++) {
                SubjectMappingBo mapping = mappings.get(index);
                if (mapping.subjectNo() == null || mapping.subjectNo() < 1 || mapping.subjectNo() > 3) {
                    throw context(
                        "subjectMappings[" + index + "].subjectNo",
                        "OUT_OF_RANGE",
                        "科目编号只允许1至3"
                    );
                }
                if (!subjectNumbers.add(mapping.subjectNo())) {
                    throw context("subjectMappings[" + index + "].subjectNo", "DUPLICATE", "科目编号重复");
                }
                long subjectId = parsePositive(
                    mapping.examSubjectId(),
                    "subjectMappings[" + index + "].examSubjectId"
                );
                if (!subjectIds.add(subjectId)) {
                    throw context("subjectMappings[" + index + "].examSubjectId", "DUPLICATE", "目标科目重复");
                }
                long subjectCertification = Optional.ofNullable(repository.selectSubjectCertification(subjectId))
                    .orElseThrow(() -> context("subjectMappings.examSubjectId", "NOT_FOUND", "目标科目不存在"));
                if (subjectCertification != certification) {
                    throw context(
                        "subjectMappings[" + index + "].examSubjectId",
                        "CERTIFICATION_MISMATCH",
                        "目标科目与考纲不属于同一考试资格"
                    );
                }
            }
            return mappings.stream().sorted(Comparator.comparing(SubjectMappingBo::subjectNo)).toList();
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw context("subjectMappings", "INVALID_FORMAT", "科目映射不是合法JSON");
        }
    }

    private String canonicalConfig(List<SubjectMappingBo> mappings) throws Exception {
        List<Map<String, Object>> normalized = mappings.stream()
            .map(mapping -> Map.<String, Object>of(
                "subject_no",
                mapping.subjectNo(),
                "exam_subject_id",
                Long.parseLong(mapping.examSubjectId())
            ))
            .toList();
        return jsonDocuments.flat(ImportJsonSchema.IMPORT_PARSE_CONFIG, Map.of(
            "subject_mappings",
            normalized
        ));
    }

    private String canonicalTextbookConfig(
        String mode,
        long certificationId,
        Long syllabusVersionId,
        String title,
        String edition,
        List<SubjectMappingBo> mappings
    ) throws Exception {
        List<Map<String, Object>> normalized = mappings.stream()
            .map(mapping -> Map.<String, Object>of(
                "subject_no", mapping.subjectNo(),
                "exam_subject_id", Long.parseLong(mapping.examSubjectId())
            ))
            .toList();
        Map<String, Object> config = jsonDocuments.flatFields(ImportJsonSchema.IMPORT_PARSE_CONFIG, Map.of());
        config.put("import_type", "document_chunk");
        config.put("mode", mode);
        config.put("certification_id", certificationId);
        if (syllabusVersionId != null) {
            config.put("syllabus_version_id", syllabusVersionId);
        }
        config.put("title", title);
        config.put("edition", edition);
        config.put("subject_mappings", normalized);
        return objectMapper.writeValueAsString(config);
    }

    private static String copyAndValidate(MultipartFile file, Path temp) throws Exception {
        String hash = copyAndHash(file, temp);
        validateUtf8Jsonl(temp);
        return hash;
    }

    private static String copyAndValidateTextbook(MultipartFile file, Path temp) throws Exception {
        String hash = copyAndHash(file, temp);
        try {
            validateUtf8Jsonl(temp);
        } catch (ImportException exception) {
            throw new ImportException(400, "IMPORT_FILE_ENCODING_INVALID", exception.getMessage());
        }
        return hash;
    }

    private static String copyAndHash(MultipartFile file, Path temp) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (
            InputStream input = file.getInputStream();
            OutputStream output = Files.newOutputStream(temp);
            DigestInputStream digestInput = new DigestInputStream(input, digest)
        ) {
            digestInput.transferTo(output);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void validateUtf8Jsonl(Path temp) throws Exception {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Reader reader = new InputStreamReader(Files.newInputStream(temp), decoder)) {
            char[] buffer = new char[8192];
            while (reader.read(buffer) >= 0) {
                // Fully decode the file to validate UTF-8.
            }
        } catch (CharacterCodingException exception) {
            throw fileError("文件不是合法UTF-8");
        }
        try (InputStream input = Files.newInputStream(temp)) {
            byte[] bom = input.readNBytes(3);
            if (Arrays.equals(bom, new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF})) {
                throw fileError("文件不能包含UTF-8 BOM");
            }
        }
    }

    private static void validateQuestionHeader(String requestId, ImportCreateBo form) {
        validateRequestId(requestId);
        if (form == null || !"question".equals(form.getImportType())) throw context("importType", "UNSUPPORTED", "仅支持question");
        if (!"question-zip/1.0".equals(form.getTemplateVersion())) throw context("templateVersion", "UNSUPPORTED", "模板版本不支持");
        if (normalize(form.getExamSubjectId()) != null) throw context("examSubjectId", "UNSUPPORTED", "题目科目由服务端自动识别");
        MultipartFile file = form.getFile();
        String filename = file == null ? null : file.getOriginalFilename();
        if (file == null || file.isEmpty() || file.getSize() > MAX_QUESTION_FILE_SIZE || filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw fileError("文件为空、扩展名错误或超过64MiB");
        }
    }

    private static void validatePaperHeader(String requestId, PaperImportCreateBo form) {
        validateRequestId(requestId);
        if (form == null || form.getFile() == null || form.getFile().isEmpty()
            || form.getFile().getSize() > MAX_QUESTION_FILE_SIZE
            || form.getFile().getOriginalFilename() == null
            || !form.getFile().getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw fileError("文件为空、扩展名错误或超过64MiB");
        }
        String type = normalize(form.getCollectionType());
        if (!Set.of("FIRST_DIAGNOSTIC", "PRACTICE", "SIMULATION", "PAST_PAPER").contains(type)) {
            throw context("collectionType", "UNSUPPORTED", "题集类型不支持");
        }
        String name = form.getCollectionName() == null ? null : form.getCollectionName().trim();
        if (name == null || name.isEmpty() || name.length() > 200) {
            throw context("collectionName", "INVALID", "题集名称长度必须为1至200字符");
        }
        if (form.getDurationMinutes() == null || form.getDurationMinutes() < 1 || form.getDurationMinutes() > 1440) {
            throw context("durationMinutes", "OUT_OF_RANGE", "考试时长必须为1至1440分钟");
        }
        form.setCollectionType(type);
        form.setCollectionName(name);
        if (!"PAST_PAPER".equals(type)) {
            return;
        }
        String paperTypeCode = normalize(form.getPaperTypeCode());
        String paperTypeName = normalize(form.getPaperTypeName());
        if (form.getExamYear() == null || form.getExamYear() < 1990 || form.getExamYear() > 2100) {
            throw context("examYear", "OUT_OF_RANGE", "历年真题年份必须为1990至2100");
        }
        if (form.getExamMonth() == null || !Set.of(5, 11).contains(form.getExamMonth())) {
            throw context("examMonth", "UNSUPPORTED", "历年真题批次只允许5月或11月");
        }
        if (paperTypeCode == null || paperTypeCode.length() > 50) {
            throw context("paperTypeCode", "INVALID", "卷型编码长度必须为1至50字符");
        }
        if (paperTypeName == null || paperTypeName.length() > 100) {
            throw context("paperTypeName", "INVALID", "卷型名称长度必须为1至100字符");
        }
        form.setPaperTypeCode(paperTypeCode);
        form.setPaperTypeName(paperTypeName);
    }

    private static void validateTextbookHeader(ImportCreateBo form, String requestId) {
        validateUuidRequestId(requestId);
        if (form == null || !"document_chunk".equals(form.getImportType())) {
            throw context("importType", "UNSUPPORTED", "教材导入类型必须为document_chunk");
        }
        if (!"document_chunk/1.0".equals(form.getTemplateVersion())) {
            throw context("templateVersion", "UNSUPPORTED", "教材模板版本不支持");
        }
        String mode = normalize(form.getMode());
        if (mode == null || !Set.of("create", "replace_draft").contains(mode)) {
            throw context("mode", "UNSUPPORTED", "导入模式只允许create或replace_draft");
        }
        MultipartFile file = form.getFile();
        String filename = file == null ? null : file.getOriginalFilename();
        if (file == null || file.isEmpty() || filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".jsonl")) {
            throw fileError("教材文件为空或扩展名不是.jsonl");
        }
        if (file.getSize() < MIN_TEXTBOOK_FILE_SIZE) {
            throw fileError("教材文件不能小于1MiB");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ImportException(400, "IMPORT_FILE_TOO_LARGE", "教材文件不能超过10MiB");
        }
        String mime = file.getContentType();
        if (mime == null || mime.isBlank() || !MIMES.contains(mime.toLowerCase(Locale.ROOT))) {
            throw fileError("文件MIME类型不支持");
        }
    }

    private static void validateHeader(
        String requestId,
        String type,
        String template,
        MultipartFile file
    ) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 100) {
            throw context("X-Request-Id", "OUT_OF_RANGE", "请求号长度必须为1至100");
        }
        if (!"knowledge_point".equals(type)) {
            throw context("importType", "UNSUPPORTED", "仅支持knowledge_point");
        }
        if (!"knowledge_point/1.0".equals(template)) {
            throw context("templateVersion", "UNSUPPORTED", "模板版本不支持");
        }
        String filename = file == null ? null : file.getOriginalFilename();
        if (
            file == null
                || file.isEmpty()
                || file.getSize() > MAX_FILE_SIZE
                || filename == null
                || !filename.toLowerCase(Locale.ROOT).endsWith(".jsonl")
        ) {
            throw fileError("文件为空、扩展名错误或超过10MiB");
        }
        String mime = file.getContentType();
        if (mime != null && !mime.isBlank() && !MIMES.contains(mime.toLowerCase(Locale.ROOT))) {
            throw fileError("文件MIME类型不支持");
        }
    }

    private ImportBatchVo reuseOrConflict(CmIdempotencyRecord record, String payloadHash) {
        boolean samePayload = MessageDigest.isEqual(
            record.payloadHash().getBytes(StandardCharsets.US_ASCII),
            payloadHash.getBytes(StandardCharsets.US_ASCII)
        );
        if (!samePayload || record.resourceId() == null) {
            throw new ImportException(409, "IMPORT_REQUEST_ID_CONFLICT", "请求号已被使用");
        }
        CmImportBatch batch = Optional.ofNullable(repository.selectById(record.resourceId()))
            .orElseThrow(ImportServiceImpl::system);
        if (!Objects.equals(batch.createBy(), LoginHelper.getUserId())) {
            throw new ImportException(409, "IMPORT_REQUEST_ID_CONFLICT", "请求号已被使用");
        }
        return toVo(batch, true);
    }

    private ImportValidationAcceptedVo reuseConfirmation(CmIdempotencyRecord record, String payloadHash) {
        boolean samePayload = MessageDigest.isEqual(
            record.payloadHash().getBytes(StandardCharsets.US_ASCII),
            payloadHash.getBytes(StandardCharsets.US_ASCII)
        );
        if (!samePayload || record.resourceId() == null) {
            throw new ImportException(409, "IMPORT_REQUEST_ID_CONFLICT", "请求号已被其他确认操作使用");
        }
        CmImportBatch batch = Optional.ofNullable(repository.selectById(record.resourceId()))
            .orElseThrow(ImportServiceImpl::system);
        if (!Objects.equals(batch.createBy(), LoginHelper.getUserId())) {
            throw new ImportException(409, "IMPORT_REQUEST_ID_CONFLICT", "请求号已被其他确认操作使用");
        }
        return confirmationVo(batch, false);
    }

    private ImportValidationAcceptedVo reusePaperValidation(CmIdempotencyRecord record, String payloadHash) {
        if (!samePayload(record, payloadHash) || record.resourceId() == null) {
            throw new ImportException(409, "PAPER_IMPORT_REQUEST_ID_CONFLICT", "请求号已被其他预检操作使用");
        }
        CmImportBatch batch = Optional.ofNullable(repository.selectById(record.resourceId()))
            .orElseThrow(ImportServiceImpl::system);
        if (!"paper".equals(batch.importType()) || !Objects.equals(batch.createBy(), LoginHelper.getUserId())) {
            throw new ImportException(409, "PAPER_IMPORT_REQUEST_ID_CONFLICT", "请求号已被其他预检操作使用");
        }
        return confirmationVo(batch, true);
    }

    private static boolean samePayload(CmIdempotencyRecord record, String payloadHash) {
        return MessageDigest.isEqual(
            record.payloadHash().getBytes(StandardCharsets.US_ASCII),
            payloadHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static ImportValidationAcceptedVo confirmationVo(CmImportBatch batch, boolean accepted) {
        return new ImportValidationAcceptedVo(
            Long.toString(batch.id()),
            batch.status(),
            batch.currentStage(),
            accepted
        );
    }

    private static String confirmationPayloadHash(long batchId, String action, String resolutionHash) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest((action + "\n" + batchId + "\n" + Objects.toString(resolutionHash, ""))
                        .getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw system();
        }
    }

    private static String knowledgeResolutionPayloadHash(
        long batchId,
        List<KnowledgeImportDiffResolutionBo> resolutions
    ) {
        try {
            StringBuilder payload = new StringBuilder("knowledge_diff_resolution\n").append(batchId).append('\n');
            resolutions.stream()
                .sorted(Comparator.comparing(value -> parsePositive(value.getDiffId(), "diffId")))
                .forEach(value -> payload.append(value.getDiffId()).append('\u001f')
                    .append(Objects.toString(normalize(value.getDecision()), "")).append('\u001f')
                    .append(Objects.toString(value.getOldKnowledgePointId(), "")).append('\n'));
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw system();
        }
    }

    private String resolutionResponse(KnowledgeImportDiffResolutionVo result) {
        try {
            return jsonDocuments.flat(ImportJsonSchema.KNOWLEDGE_IMPORT_DIFF_RESOLUTION_RESPONSE,
                Map.of("data", result));
        } catch (Exception exception) {
            throw system();
        }
    }

    private static void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 100) {
            throw context("X-Request-Id", "OUT_OF_RANGE", "请求号长度必须为1至100");
        }
    }

    private static void validateUuidRequestId(String requestId) {
        try {
            if (requestId == null || !UUID.fromString(requestId).toString().equalsIgnoreCase(requestId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw context("X-Request-Id", "INVALID_FORMAT", "请求号必须是UUID");
        }
    }

    private static ImportException stateInvalid() {
        return new ImportException(409, "IMPORT_BATCH_STATE_INVALID", "当前批次状态不允许确认导入");
    }

    private static ImportException diffConflict(String message) {
        return new ImportException(409, "KNOWLEDGE_DIFF_MAPPING_CONFLICT", message);
    }

    private static String payloadHash(
        String type,
        long syllabus,
        String template,
        String config,
        String fileHash
    ) throws Exception {
        String normalized = type + "\n" + syllabus + "\n" + template + "\n" + config + "\n" + fileHash;
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8))
        );
    }

    private static String questionPayloadHash(
        long certificationId,
        Long syllabusId,
        String templateVersion,
        List<DerivedQuestionSubjectVo> subjects,
        String fileHash
    ) throws Exception {
        String subjectNos = subjects.stream().map(DerivedQuestionSubjectVo::subjectNo).sorted()
            .map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
            ("question\n" + certificationId + "\n" + Objects.toString(syllabusId, "") + "\n"
                + subjectNos + "\n" + templateVersion + "\n" + fileHash).getBytes(StandardCharsets.UTF_8)
        ));
    }

    private static String textbookPayloadHash(
        String mode,
        long certificationId,
        Long syllabusId,
        Long documentId,
        String title,
        String edition,
        String templateVersion,
        String config,
        String fileHash
    ) throws Exception {
        String normalized = String.join("\n",
            "document_chunk",
            mode,
            Long.toString(certificationId),
            Objects.toString(syllabusId, ""),
            documentId == null ? "" : Long.toString(documentId),
            title,
            edition == null ? "" : edition,
            templateVersion,
            config,
            fileHash
        );
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8))
        );
    }

    private ImportScope resolveImportScope(
        String rawCertificationId,
        String rawSyllabusVersionId,
        String syllabusField
    ) {
        Long certificationId = parseOptionalPositive(rawCertificationId, "certificationId");
        Long syllabusVersionId = parseOptionalPositive(rawSyllabusVersionId, syllabusField);
        if (certificationId == null && syllabusVersionId == null) {
            throw context("certificationId", "REQUIRED", "考试资格不能为空");
        }
        Long syllabusCertification = null;
        if (syllabusVersionId != null) {
            syllabusCertification = repository.selectSyllabusCertification(syllabusVersionId);
            if (syllabusCertification == null) {
                throw context(syllabusField, "NOT_FOUND", "考纲版本不存在");
            }
            if (certificationId != null && !certificationId.equals(syllabusCertification)) {
                throw context("certificationId", "MISMATCH", "考试资格与考纲版本不属于同一资格");
            }
        }
        long resolvedCertificationId = certificationId == null ? syllabusCertification : certificationId;
        String certificationName = syllabusVersionId == null
            ? repository.selectCertificationName(resolvedCertificationId)
            : repository.selectCertificationNameBySyllabus(syllabusVersionId);
        if (certificationName == null && certificationId != null) {
            certificationName = repository.selectCertificationName(resolvedCertificationId);
        }
        if (certificationName == null && (syllabusVersionId == null || certificationId != null)) {
            throw context("certificationId", "NOT_FOUND", "考试资格不存在");
        }
        return new ImportScope(resolvedCertificationId, syllabusVersionId, certificationName);
    }

    private record ImportScope(long certificationId, Long syllabusVersionId, String certificationName) {
    }

    private static String requiredText(String value, String field, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw context(field, "REQUIRED", "字段不能为空");
        }
        if (normalized.length() > maxLength) {
            throw context(field, "OUT_OF_RANGE", "字段长度不能超过" + maxLength);
        }
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        String normalized = normalize(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw context(field, "OUT_OF_RANGE", "字段长度不能超过" + maxLength);
        }
        return normalized;
    }

    private void compensate(String key, String hash, String reason) {
        if (!storage.deleteQuietly(key)) {
            try {
                persistence.enqueueCleanup(key, hash == null ? "unknown" : hash, reason);
            } catch (RuntimeException ignored) {
                // Preserve the existing best-effort compensation behavior.
            }
        }
    }

    private ImportBatchVo toVo(CmImportBatch batch, boolean reused) {
        String syllabusVersionId = batch.syllabusVersionId() == null ? null : Long.toString(batch.syllabusVersionId());
        boolean textbook = "document_chunk".equals(batch.importType());
        String mode = textbook ? objectMapper.readTree(batch.parseConfig()).path("mode").textValue() : null;
        return new ImportBatchVo(
            Long.toString(batch.id()),
            batch.documentId() == null ? null : Long.toString(batch.documentId()),
            batch.importType(),
            mode,
            syllabusVersionId,
            batch.examSubjectId() == null ? null : Long.toString(batch.examSubjectId()),
            "question".equals(batch.importType()) ? syllabusVersionId : null,
            List.of(),
            batch.templateVersion(),
            batch.status(),
            reused,
            batch.createTime()
        );
    }

    private static long requiredSyllabusVersionId(CmImportBatch batch) {
        if (batch.syllabusVersionId() == null) {
            throw new IllegalStateException("导入批次未绑定考纲版本");
        }
        return batch.syllabusVersionId();
    }

    private String batchResponse(ImportJsonSchema schema, long batchId) {
        return jsonDocuments.flat(schema, Map.of("batch_id", Long.toString(batchId)));
    }

    private static long parsePositive(String value, String field) {
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
            throw context(field, "INVALID_FORMAT", "必须是十进制正整数");
        }
    }

    private static Long parseOptionalPositive(String value, String field) {
        String normalized = normalize(value);
        return normalized == null ? null : parsePositive(normalized, field);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static OffsetDateTime atStartOfBusinessDay(LocalDate date, String field) {
        if (date == null) {
            return null;
        }
        try {
            return date.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        } catch (DateTimeException exception) {
            throw context(field, "INVALID_FORMAT", "日期格式不正确");
        }
    }

    private static OffsetDateTime startOfNextBusinessDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        try {
            return date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        } catch (DateTimeException exception) {
            throw context("completedEndDate", "OUT_OF_RANGE", "结束日期超出支持范围");
        }
    }

    private static Long completedBatchVisibleUserId() {
        return LoginHelper.isSuperAdmin() ? null : LoginHelper.getUserId();
    }

    private static Long resourceVisibleUserId() {
        return LoginHelper.isSuperAdmin() ? null : LoginHelper.getUserId();
    }

    private static String safeName(String filename) {
        return filename == null ? "source.jsonl" : Paths.get(filename).getFileName().toString();
    }

    private static ImportException context(String field, String code, String message) {
        return new ImportException(
            400,
            "IMPORT_CONTEXT_INVALID",
            "导入上下文不合法",
            false,
            null,
            List.of(new ImportFieldErrorVo(field, code, message))
        );
    }

    private static ImportException fileError(String message) {
        return new ImportException(400, "IMPORT_FILE_INVALID", message);
    }

    private static ImportException notFound() {
        return new ImportException(404, "IMPORT_BATCH_NOT_FOUND", "导入批次不存在或不可见");
    }

    private static ImportException system() {
        String traceId = UUID.randomUUID().toString();
        return new ImportException(500, "IMPORT_SYSTEM_FAILURE", "导入服务异常", true, traceId, List.of());
    }

    private CmIdempotencyRecord concurrentRecordOrThrow(
        String action, String requestId, String operation, Long batchId, DataIntegrityViolationException exception
    ) {
        CmIdempotencyRecord concurrent = repository.selectIdempotency(action, requestId);
        if (concurrent == null) {
            throw system(operation, batchId, exception);
        }
        return concurrent;
    }

    private static ImportException system(String operation, Long batchId, Exception exception) {
        String traceId = UUID.randomUUID().toString();
        log.error(
            "Import operation failed, operation={}, batchId={}, traceId={}",
            operation,
            batchId,
            traceId,
            exception
        );
        if (exception instanceof S3StorageException) {
            return new ImportException(
                503,
                "IMPORT_STORAGE_UNAVAILABLE",
                "文件存储服务暂不可用，请稍后重试",
                true,
                traceId,
                List.of()
            );
        }
        return new ImportException(500, "IMPORT_SYSTEM_FAILURE", "导入服务异常", true, traceId, List.of());
    }
}
