package org.dromara.certmuse.catalog.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffRow;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.dromara.certmuse.catalog.domain.KnowledgePointInsert;
import org.dromara.certmuse.catalog.domain.KnowledgePointUpdate;
import org.dromara.certmuse.catalog.domain.TextbookChunkInsert;
import org.dromara.certmuse.catalog.domain.TextbookChunkKnowledgeInsert;
import org.dromara.certmuse.catalog.domain.TextbookImportDocument;
import org.dromara.certmuse.catalog.domain.TextbookImportRecordData;
import org.dromara.certmuse.catalog.domain.TextbookKnowledgePointLookup;
import org.dromara.certmuse.catalog.domain.TextbookRecordResultUpdate;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.dromara.certmuse.catalog.support.ImportJsonSchema;
import org.dromara.certmuse.catalog.support.ImportProtocol;
import org.dromara.certmuse.question.service.QuestionKnowledgeMaintenanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transactional persistence operations for imports.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ImportPersistenceService {

    private static final int INSERT_CHUNK_SIZE = 500;

    private final ImportMapper repository;
    private final JsonMapper objectMapper;
    private final VersionedJsonDocumentFactory jsonDocuments;
    private final KnowledgeImportReconciliationService reconciliationService;
    private final QuestionKnowledgeMaintenanceService questionKnowledgeMaintenanceService;

    /** Compatibility constructor retained for focused legacy unit tests. */
    public ImportPersistenceService(
        ImportMapper repository,
        JsonMapper objectMapper,
        VersionedJsonDocumentFactory jsonDocuments
    ) {
        this(repository, objectMapper, jsonDocuments, null, null);
    }

    @Transactional
    public void create(
        long id,
        String requestId,
        String payloadHash,
        long syllabusId,
        String objectKey,
        String fileHash,
        String fileName,
        long fileSize,
        String mime,
        String config,
        long userId,
        Long deptId,
        OffsetDateTime createTime,
        String responseBody
    ) {
        create(id, requestId, payloadHash, syllabusId, objectKey, fileHash, fileName, fileSize, mime, config,
            userId, deptId, createTime, responseBody, null);
    }

    @Transactional
    public void create(
        long id, String requestId, String payloadHash, long syllabusId, String objectKey, String fileHash,
        String fileName, long fileSize, String mime, String config, long userId, Long deptId,
        OffsetDateTime createTime, String responseBody, Long certificationId
    ) {
        repository.insertIdempotency(IdUtil.getSnowflakeNextId(), ImportProtocol.KNOWLEDGE_CREATE_ACTION, requestId, payloadHash);
        if (certificationId != null) {
            repository.insertSyllabus(syllabusId, certificationId, userId, deptId);
        }
        repository.insertBatch(
            id,
            requestId,
            syllabusId,
            objectKey,
            fileHash,
            fileName,
            fileSize,
            mime,
            config,
            userId,
            deptId,
            createTime
        );
        if (repository.completeIdempotency(ImportProtocol.KNOWLEDGE_CREATE_ACTION, requestId, id, responseBody) != 1) {
            throw new IllegalStateException("Idempotency completion failed");
        }
    }

    @Transactional
    public void createQuestion(
        long id, String requestId, String payloadHash, Long syllabusId, long certificationId,
        String objectKey, String fileHash, String fileName, long fileSize, String mime, String config,
        long userId, Long deptId, OffsetDateTime createTime, String responseBody
    ) {
        repository.insertIdempotency(IdUtil.getSnowflakeNextId(), ImportProtocol.QUESTION_CREATE_ACTION, requestId, payloadHash);
        repository.insertQuestionBatch(id, requestId, syllabusId, certificationId, objectKey, fileHash, fileName,
            fileSize, mime, config, userId, deptId, createTime);
        if (repository.completeIdempotency(ImportProtocol.QUESTION_CREATE_ACTION, requestId, id, responseBody) != 1) {
            throw new IllegalStateException("Question idempotency completion failed");
        }
    }

    /** Compatibility overload for syllabus-scoped callers retained during the API transition. */
    @Transactional
    public void createQuestion(
        long id, String requestId, String payloadHash, long syllabusId,
        String objectKey, String fileHash, String fileName, long fileSize, String mime, String config,
        long userId, Long deptId, OffsetDateTime createTime, String responseBody
    ) {
        Long certificationId = repository.selectSyllabusCertification(syllabusId);
        if (certificationId == null) {
            throw new IllegalStateException("Question import syllabus does not exist");
        }
        createQuestion(id, requestId, payloadHash, syllabusId, certificationId, objectKey, fileHash, fileName,
            fileSize, mime, config, userId, deptId, createTime, responseBody);
    }

    @Transactional
    public void createPaper(
        long id, String requestId, String payloadHash, long syllabusId,
        long certificationId, String collectionName, String collectionType, int durationMinutes,
        String objectKey, String fileHash, String fileName, long fileSize, String mime, String config,
        long userId, Long deptId, OffsetDateTime createTime, String responseBody
    ) {
        repository.insertIdempotency(IdUtil.getSnowflakeNextId(), ImportProtocol.PAPER_CREATE_ACTION, requestId, payloadHash);
        repository.insertPaperBatch(id, requestId, syllabusId, certificationId, objectKey, fileHash, fileName, fileSize, mime, config,
            userId, deptId, createTime);
        repository.insertPaperImport(id, certificationId, collectionName, collectionType, durationMinutes);
        if (repository.completeIdempotency(ImportProtocol.PAPER_CREATE_ACTION, requestId, id, responseBody) != 1) {
            throw new IllegalStateException("Paper idempotency completion failed");
        }
    }

    @Transactional
    public void createPaper(
        long id, String requestId, String payloadHash, long syllabusId,
        long certificationId, String collectionName, String collectionType, int durationMinutes,
        Integer examYear, Integer examMonth, String paperTypeCode, String paperTypeName,
        String objectKey, String fileHash, String fileName, long fileSize, String mime, String config,
        long userId, Long deptId, OffsetDateTime createTime, String responseBody
    ) {
        repository.insertIdempotency(IdUtil.getSnowflakeNextId(), ImportProtocol.PAPER_CREATE_ACTION, requestId, payloadHash);
        repository.insertPaperBatch(id, requestId, syllabusId, certificationId, objectKey, fileHash, fileName, fileSize, mime, config,
            userId, deptId, createTime);
        repository.insertPaperImport(id, certificationId, collectionName, collectionType, durationMinutes,
            examYear, examMonth, paperTypeCode, paperTypeName);
        if (repository.completeIdempotency(ImportProtocol.PAPER_CREATE_ACTION, requestId, id, responseBody) != 1) {
            throw new IllegalStateException("Paper idempotency completion failed");
        }
    }

    @Transactional
    public void createTextbook(
        long batchId,
        Long documentId,
        boolean createDocument,
        String requestId,
        String payloadHash,
        long certificationId,
        Long syllabusVersionId,
        String title,
        String edition,
        String objectKey,
        String fileHash,
        String fileName,
        long fileSize,
        String mime,
        String parseConfig,
        long userId,
        Long deptId,
        OffsetDateTime createTime,
        String responseBody
    ) {
        repository.insertIdempotency(
            IdUtil.getSnowflakeNextId(), ImportProtocol.TEXTBOOK_CREATE_ACTION, requestId, payloadHash
        );
        repository.insertTextbookBatch(
            batchId, requestId, documentId, certificationId, syllabusVersionId,
            objectKey, fileHash, fileName, fileSize, mime,
            parseConfig, userId, deptId, createTime
        );
        if (repository.completeIdempotency(
            ImportProtocol.TEXTBOOK_CREATE_ACTION, requestId, batchId, responseBody
        ) != 1) {
            throw new IllegalStateException("Textbook idempotency completion failed");
        }
    }

    /** Compatibility overload for existing syllabus-scoped textbook callers. */
    @Transactional
    public void createTextbook(
        long batchId, Long documentId, boolean createDocument, String requestId, String payloadHash,
        long syllabusVersionId, String title, String edition, String objectKey, String fileHash,
        String fileName, long fileSize, String mime, String parseConfig, long userId, Long deptId,
        OffsetDateTime createTime, String responseBody
    ) {
        Long certificationId = repository.selectSyllabusCertification(syllabusVersionId);
        if (certificationId == null) {
            throw new IllegalStateException("Textbook import syllabus does not exist");
        }
        createTextbook(batchId, documentId, createDocument, requestId, payloadHash, certificationId,
            syllabusVersionId, title, edition, objectKey, fileHash, fileName, fileSize, mime, parseConfig,
            userId, deptId, createTime, responseBody);
    }

    @Transactional
    public boolean enqueueQuestionValidation(long batchId) {
        if (repository.start(batchId) != 1) return false;
        repository.enqueueJob(IdUtil.getSnowflakeNextId(), ImportProtocol.QUESTION_PRECHECK_JOB, Long.toString(batchId),
            jobPayload(ImportJsonSchema.QUESTION_PRECHECK_JOB, batchId));
        return true;
    }

    @Transactional
    public boolean enqueuePaperValidation(long batchId) {
        if (repository.start(batchId) != 1) return false;
        repository.enqueueJob(IdUtil.getSnowflakeNextId(), ImportProtocol.PAPER_PRECHECK_JOB, Long.toString(batchId),
            jobPayload(ImportJsonSchema.PAPER_PRECHECK_JOB, batchId));
        return true;
    }

    @Transactional
    public void acceptPaperValidation(long batchId, String requestId, String payloadHash, String responseBody) {
        repository.insertIdempotency(IdUtil.getSnowflakeNextId(), ImportProtocol.PAPER_VALIDATE_ACTION, requestId, payloadHash);
        if (!enqueuePaperValidation(batchId)) {
            throw new ImportException(409, "PAPER_IMPORT_STATE_CONFLICT", "当前批次状态不允许预检");
        }
        if (repository.completeIdempotency(ImportProtocol.PAPER_VALIDATE_ACTION, requestId, batchId, responseBody) != 1) {
            throw new IllegalStateException("Paper validation idempotency completion failed");
        }
    }

    @Transactional
    public boolean enqueueValidation(long batchId) {
        if (repository.start(batchId) != 1) {
            return false;
        }
        repository.enqueueJob(
            IdUtil.getSnowflakeNextId(),
            ImportProtocol.KNOWLEDGE_PRECHECK_JOB,
            Long.toString(batchId),
            jobPayload(ImportJsonSchema.KNOWLEDGE_POINT_PRECHECK_JOB, batchId)
        );
        return true;
    }

    @Transactional
    public boolean enqueueTextbookValidation(long batchId) {
        if (repository.start(batchId) != 1) {
            return false;
        }
        repository.enqueueJob(
            IdUtil.getSnowflakeNextId(),
            ImportProtocol.TEXTBOOK_PRECHECK_JOB,
            Long.toString(batchId),
            jobPayload(ImportJsonSchema.TEXTBOOK_PRECHECK_JOB, batchId)
        );
        return true;
    }

    @Transactional
    public void acceptConfirmation(
        long batchId,
        String requestId,
        String payloadHash,
        long userId,
        OffsetDateTime confirmedTime,
        String responseBody
    ) {
        repository.insertIdempotency(
            IdUtil.getSnowflakeNextId(),
            ImportProtocol.KNOWLEDGE_CONFIRM_ACTION,
            requestId,
            payloadHash
        );
        if (repository.acceptConfirmation(batchId, userId, confirmedTime) != 1) {
            throw new ImportException(409, "IMPORT_BATCH_STATE_INVALID", "当前批次状态不允许确认导入");
        }
        repository.enqueueJob(
            IdUtil.getSnowflakeNextId(),
            ImportProtocol.KNOWLEDGE_PERSIST_JOB,
            Long.toString(batchId),
            jobPayload(ImportJsonSchema.KNOWLEDGE_POINT_PERSIST_JOB, batchId)
        );
        if (repository.completeIdempotency(ImportProtocol.KNOWLEDGE_CONFIRM_ACTION, requestId, batchId, responseBody) != 1) {
            throw new IllegalStateException("Confirmation idempotency completion failed");
        }
    }

    @Transactional
    public void acceptQuestionConfirmation(long batchId, String requestId, String payloadHash, long userId, OffsetDateTime confirmedTime, String responseBody) {
        repository.insertIdempotency(IdUtil.getSnowflakeNextId(), ImportProtocol.QUESTION_CONFIRM_ACTION, requestId, payloadHash);
        if (repository.acceptQuestionConfirmation(batchId, userId, confirmedTime) != 1) {
            throw new ImportException(409, "IMPORT_BATCH_STATE_INVALID", "当前批次状态不允许确认导入");
        }
        repository.enqueueJob(IdUtil.getSnowflakeNextId(), ImportProtocol.QUESTION_PERSIST_JOB, Long.toString(batchId),
            jobPayload(ImportJsonSchema.QUESTION_PERSIST_JOB, batchId));
        if (repository.completeIdempotency(ImportProtocol.QUESTION_CONFIRM_ACTION, requestId, batchId, responseBody) != 1) {
            throw new IllegalStateException("Question confirmation idempotency completion failed");
        }
    }

    @Transactional
    public void acceptPaperConfirmation(long batchId, String requestId, String payloadHash, long userId, OffsetDateTime confirmedTime, String responseBody) {
        repository.insertIdempotency(IdUtil.getSnowflakeNextId(), ImportProtocol.PAPER_CONFIRM_ACTION, requestId, payloadHash);
        if (repository.acceptPaperConfirmation(batchId, userId, confirmedTime) != 1) {
            throw new ImportException(422, "PAPER_IMPORT_PRECHECK_FAILED", "试卷预检尚未全部通过");
        }
        repository.enqueueJob(IdUtil.getSnowflakeNextId(), ImportProtocol.PAPER_PERSIST_JOB, Long.toString(batchId),
            jobPayload(ImportJsonSchema.PAPER_PERSIST_JOB, batchId));
        if (repository.completeIdempotency(ImportProtocol.PAPER_CONFIRM_ACTION, requestId, batchId, responseBody) != 1) {
            throw new IllegalStateException("Paper confirmation idempotency completion failed");
        }
    }

    @Transactional
    public void acceptTextbookConfirmation(
        long batchId,
        String requestId,
        String payloadHash,
        long userId,
        OffsetDateTime confirmedTime,
        String responseBody
    ) {
        repository.insertIdempotency(
            IdUtil.getSnowflakeNextId(), ImportProtocol.TEXTBOOK_CONFIRM_ACTION, requestId, payloadHash
        );
        CmImportBatch batch = Optional.ofNullable(repository.selectByIdForUpdate(batchId))
            .orElseThrow(() -> new ImportException(404, "IMPORT_BATCH_NOT_FOUND", "导入批次不存在"));
        if (!"document_chunk".equals(batch.importType()) || !"waiting_confirm".equals(batch.status())
            || batch.failedCount() != 0 || batch.validCount() <= 0) {
            throw new ImportException(409, "IMPORT_BATCH_STATE_INVALID", "当前批次状态不允许确认导入");
        }
        JsonNode config = objectMapper.readTree(batch.parseConfig());
        String mode = config.path("mode").isTextual()
            ? config.path("mode").textValue()
            : batch.documentId() == null ? "create" : "replace_draft";
        Long documentId = batch.documentId();
        if ("create".equals(mode)) {
            if (documentId != null) {
                throw new ImportException(409, "IMPORT_BATCH_STATE_INVALID", "新建教材批次已绑定正式教材");
            }
            documentId = IdUtil.getSnowflakeNextId();
            repository.insertTextbookDocument(
                documentId, batch.certificationId(), batch.syllabusVersionId(), config.path("title").textValue(),
                config.path("edition").isNull() ? null : config.path("edition").textValue(),
                userId, batch.createDept(), confirmedTime
            );
            if (repository.bindTextbookDocument(batchId, documentId) != 1) {
                throw new ImportException(409, "IMPORT_BATCH_STATE_INVALID", "教材批次绑定失败");
            }
        } else if (documentId == null) {
            throw new ImportException(409, "IMPORT_BATCH_STATE_INVALID", "替换教材批次缺少目标教材");
        }
        TextbookImportDocument document = Optional
            .ofNullable(repository.selectTextbookDocumentForUpdate(documentId))
            .orElseThrow(() -> new ImportException(404, "TEXTBOOK_NOT_FOUND", "目标教材不存在"));
        if (!"0".equals(document.delFlag()) || !"draft".equals(document.status())) {
            throw new ImportException(409, "IMPORT_TARGET_NOT_DRAFT", "替换目标已不是草稿教材");
        }
        if (repository.submitTextbookForAutoPublish(documentId, userId, confirmedTime) != 1) {
            throw new ImportException(409, "IMPORT_TARGET_NOT_DRAFT", "替换目标已不是草稿教材");
        }
        if (repository.acceptTextbookConfirmation(batchId, userId, confirmedTime) != 1) {
            throw new ImportException(409, "IMPORT_BATCH_STATE_INVALID", "当前批次状态不允许确认导入");
        }
        repository.enqueueJob(
            IdUtil.getSnowflakeNextId(),
            ImportProtocol.TEXTBOOK_PERSIST_JOB,
            Long.toString(batchId),
            jobPayload(ImportJsonSchema.TEXTBOOK_PERSIST_JOB, batchId)
        );
        if (repository.completeIdempotency(
            ImportProtocol.TEXTBOOK_CONFIRM_ACTION, requestId, batchId, responseBody
        ) != 1) {
            throw new IllegalStateException("Textbook confirmation idempotency completion failed");
        }
    }

    @Transactional
    public void persistTextbook(long batchId) {
        CmImportBatch batch = Optional.ofNullable(repository.selectByIdForUpdate(batchId))
            .orElseThrow(() -> new IllegalStateException("Textbook import batch does not exist"));
        if (!"document_chunk".equals(batch.importType()) || !"importing".equals(batch.status())) {
            return;
        }
        if (batch.documentId() == null) {
            throw new IllegalStateException("Textbook import batch has no document");
        }
        TextbookImportDocument document = Optional
            .ofNullable(repository.selectTextbookDocumentForUpdate(batch.documentId()))
            .orElseThrow(() -> new IllegalStateException("Textbook document does not exist"));
        if (!"0".equals(document.delFlag()) || !"pending_review".equals(document.status())) {
            throw new IllegalStateException("Textbook document is no longer pending review");
        }
        if (batch.certificationId() != null && batch.certificationId() != document.certificationId()) {
            throw new IllegalStateException("Textbook import certification changed before persistence");
        }
        Long syllabusVersionId = document.syllabusVersionId();
        if (syllabusVersionId != null && batch.syllabusVersionId() != null
            && !syllabusVersionId.equals(batch.syllabusVersionId())) {
            throw new IllegalStateException("Textbook import syllabus changed before persistence");
        }
        if (syllabusVersionId == null && batch.syllabusVersionId() != null) {
            if (repository.bindTextbookSyllabus(document.id(), batch.syllabusVersionId(), batch.createBy()) != 1) {
                throw new IllegalStateException("Textbook syllabus binding failed");
            }
            syllabusVersionId = batch.syllabusVersionId();
        }
        List<TextbookImportRecordData> records = repository.selectPendingTextbookRecords(batchId);
        if (records.isEmpty() || records.size() != batch.validCount()) {
            throw new IllegalStateException("Validated textbook record count changed before persistence");
        }

        JsonNode config = objectMapper.readTree(batch.parseConfig());
        String mode = config.path("mode").textValue();
        Map<Integer, Long> subjectMappings = subjectMappings(batch.parseConfig());
        Map<String, Long> knowledgePointIds = textbookKnowledgePointIds(
            syllabusVersionId, new ArrayList<>(subjectMappings.values())
        );
        if ("create".equals(mode)) {
            if (repository.countDocumentChunks(document.id()) != 0) {
                throw new IllegalStateException("New textbook document is not empty");
            }
        } else if ("replace_draft".equals(mode)) {
            repository.deleteTextbookImages(document.id());
            repository.deleteTextbookKnowledgeRelations(document.id());
            repository.deleteTextbookChunks(document.id());
        } else {
            throw new IllegalStateException("Unsupported textbook import mode");
        }

        List<TextbookChunkInsert> chunks = new ArrayList<>(records.size());
        List<TextbookChunkKnowledgeInsert> relations = new ArrayList<>();
        List<TextbookRecordResultUpdate> results = new ArrayList<>(records.size());
        for (TextbookImportRecordData staged : records) {
            JsonNode row = objectMapper.readTree(staged.rawRecord()).path("record");
            long chunkId = IdUtil.getSnowflakeNextId();
            chunks.add(new TextbookChunkInsert(
                chunkId,
                document.id(),
                row.path("chunk_no").intValue(),
                nullableText(row.get("heading")),
                textbookHeadingPath(row.path("heading_path")),
                row.path("content").textValue(),
                textbookSourceLocator(row),
                row.path("content_hash").textValue(),
                batch.createBy()
            ));
            if (syllabusVersionId != null) {
                for (JsonNode knowledge : row.path("knowledge_points")) {
                    int subjectNo = knowledge.path("subject_no").intValue();
                    Long subjectId = subjectMappings.get(subjectNo);
                    Long knowledgePointId = knowledgePointIds.get(
                        subjectId + ":" + knowledge.path("code").textValue()
                    );
                    if (knowledgePointId == null) {
                        throw new IllegalStateException("Validated textbook knowledge point cannot be resolved");
                    }
                    relations.add(new TextbookChunkKnowledgeInsert(
                        IdUtil.getSnowflakeNextId(), chunkId, knowledgePointId
                    ));
                }
            }
            results.add(new TextbookRecordResultUpdate(staged.id(), textbookResultData(chunkId)));
        }

        insertTextbookChunks(chunks);
        insertTextbookRelations(relations);
        updateTextbookResults(results);
        if (repository.completeTextbookImport(batchId, chunks.size()) != 1) {
            throw new IllegalStateException("Textbook import batch completion failed");
        }
        if (repository.approveTextbookAfterImport(document.id()) != 1) {
            throw new IllegalStateException("Textbook approval failed after import");
        }
        if (repository.publishTextbookAfterImport(document.id()) != 1) {
            throw new IllegalStateException("Textbook publication failed after import");
        }
    }

    @Transactional
    public void persistKnowledgeTree(long batchId) {
        CmImportBatch batch = Optional.ofNullable(repository.selectByIdForUpdate(batchId))
            .orElseThrow(() -> new IllegalStateException("Import batch does not exist"));
        if (!"importing".equals(batch.status())) {
            return;
        }
        long syllabusVersionId = requiredSyllabusVersionId(batch);
        if (repository.lockSyllabusVersion(syllabusVersionId) == null) {
            throw new IllegalStateException("Target syllabus does not exist");
        }
        List<KnowledgeImportPointRow> current = repository.selectCurrentKnowledgePoints(syllabusVersionId);
        String currentBaseline = reconciliationService.baselineHash(current);
        if (batch.baselineHash() == null || !batch.baselineHash().equals(currentBaseline)) {
            throw new ImportException(409, "KNOWLEDGE_TREE_BASELINE_CHANGED", "正式知识树已发生变化，请重新预检");
        }
        boolean requiresDiffResolution = repository.countKnowledgeImportDiffs(batchId, null, null, null, false, null, null) > 0;
        if (requiresDiffResolution && (batch.resolutionHash() == null
            || !batch.resolutionHash().equals(reconciliationService.currentResolutionHash(batchId))
            || repository.countPendingKnowledgeImportDiffs(batchId) != 0)) {
            throw new ImportException(409, "KNOWLEDGE_DIFF_UNRESOLVED", "知识点差异决议不完整或已变化");
        }

        List<KnowledgeImportPointRow> incoming = repository.selectImportedKnowledgePoints(batchId);
        if (incoming.isEmpty() || incoming.size() != batch.validCount()) {
            throw new IllegalStateException("Validated record count changed before persistence");
        }

        Map<Long, KnowledgeImportDiffRow> decisions = new HashMap<>();
        List<KnowledgeImportDiffRow> allDiffs = repository.selectAllKnowledgeImportDiffs(batchId);
        for (KnowledgeImportDiffRow diff : allDiffs) {
            if (diff.getImportRecordId() != null) decisions.put(diff.getImportRecordId(), diff);
        }
        if (requiresDiffResolution && decisions.size() != incoming.size()) {
            throw new ImportException(409, "KNOWLEDGE_DIFF_UNRESOLVED", "每个新知识点必须且只能有一条人工决议");
        }

        Set<Long> rejectedOldIds = allDiffs.stream()
            .filter(diff -> "reject".equals(diff.getResolutionDecision()) && diff.getOldKnowledgePointId() != null)
            .map(KnowledgeImportDiffRow::getOldKnowledgePointId).collect(java.util.stream.Collectors.toSet());
        Set<Long> skippedRecords = rejectedRecords(incoming, decisions);
        Map<String, Long> finalIds = new HashMap<>();
        Set<Long> retainedIds = new HashSet<>();
        current.stream().filter(point -> rejectedOldIds.contains(point.getKnowledgePointId())).forEach(point -> {
            retainedIds.add(point.getKnowledgePointId());
            finalIds.put(key(point), point.getKnowledgePointId());
        });
        for (KnowledgeImportPointRow point : incoming) {
            KnowledgeImportDiffRow decision = decisions.get(point.getImportRecordId());
            if (requiresDiffResolution && (decision == null || "pending".equals(decision.getResolutionStatus()))) {
                throw new ImportException(409, "KNOWLEDGE_DIFF_UNRESOLVED", "知识点差异尚未全部确认");
            }
            if (skippedRecords.contains(point.getImportRecordId())
                || (decision != null && "reject".equals(decision.getResolutionDecision()))) continue;
            Long id = decision == null ? null : decision.getConfirmedKnowledgePointId();
            if (id == null) {
                if (requiresDiffResolution && !"add".equals(decision.getAction())) {
                    throw new ImportException(409, "KNOWLEDGE_DIFF_MAPPING_CONFLICT", "新知识点缺少继承或新增决议");
                }
                id = IdUtil.getSnowflakeNextId();
            } else if (!retainedIds.add(id)) {
                throw new ImportException(409, "KNOWLEDGE_DIFF_MAPPING_CONFLICT", "一个旧知识点不能被多个新知识点继承");
            }
            if (finalIds.put(key(point), id) != null) {
                throw new IllegalStateException("Duplicate validated syllabus number");
            }
        }

        List<KnowledgePointUpdate> updates = new ArrayList<>();
        List<KnowledgePointInsert> inserts = new ArrayList<>();
        for (KnowledgeImportPointRow point : incoming) {
            KnowledgeImportDiffRow decision = decisions.get(point.getImportRecordId());
            if (skippedRecords.contains(point.getImportRecordId())
                || (decision != null && "reject".equals(decision.getResolutionDecision()))) continue;
            long id = finalIds.get(key(point));
            Long parentId = point.getParentSyllabusNumber() == null ? null
                : finalIds.get(point.getExamSubjectId() + ":" + point.getParentSyllabusNumber());
            if (point.getParentSyllabusNumber() != null && parentId == null) {
                throw new IllegalStateException("Validated parent knowledge point is missing");
            }
            if (decision != null && decision.getConfirmedKnowledgePointId() != null) {
                updates.add(new KnowledgePointUpdate(
                    id, parentId, point.getSyllabusNumber(), point.getSyllabusTitle(), point.getTreeDepth(),
                    point.getSortOrder(), point.getDescription(), point.getImportance(), point.getStatus(), batch.createBy()
                ));
            } else {
                inserts.add(new KnowledgePointInsert(
                    id, syllabusVersionId, point.getExamSubjectId(), parentId, point.getSyllabusNumber(),
                    point.getSyllabusTitle(), point.getTreeDepth(), point.getSortOrder(), point.getDescription(),
                    point.getImportance(), point.getStatus(), batch.createBy(), batch.createDept()
                ));
            }
        }

        for (KnowledgeImportPointRow point : current) {
            if (!rejectedOldIds.contains(point.getKnowledgePointId())) continue;
            updates.add(new KnowledgePointUpdate(point.getKnowledgePointId(), point.getParentKnowledgePointId(),
                point.getSyllabusNumber(), point.getSyllabusTitle(), point.getTreeDepth(), point.getSortOrder(),
                point.getDescription(), point.getImportance(), point.getStatus(), batch.createBy()));
        }

        Set<Long> deletedIds = current.stream().map(KnowledgeImportPointRow::getKnowledgePointId)
            .filter(id -> !retainedIds.contains(id)).collect(java.util.stream.Collectors.toSet());
        for (KnowledgeImportDiffRow diff : allDiffs) {
            if ("delete".equals(diff.getAction()) && "approve".equals(diff.getResolutionDecision())
                && diff.getOldKnowledgePointId() != null
                && retainedIds.contains(diff.getOldKnowledgePointId())) {
                throw new ImportException(409, "KNOWLEDGE_DIFF_MAPPING_CONFLICT", "同一旧知识点不能同时被继承和删除");
            }
        }

        repository.softDeleteActiveKnowledgePoints(syllabusVersionId, batch.createBy());
        for (int start = 0; start < updates.size(); start += INSERT_CHUNK_SIZE) {
            List<KnowledgePointUpdate> chunk = updates.subList(start, Math.min(start + INSERT_CHUNK_SIZE, updates.size()));
            if (repository.updateKnowledgePoints(chunk) != chunk.size()) {
                throw new IllegalStateException("Knowledge point bulk update was incomplete");
            }
        }
        for (int start = 0; start < inserts.size(); start += INSERT_CHUNK_SIZE) {
            List<KnowledgePointInsert> chunk = inserts.subList(
                start,
                Math.min(start + INSERT_CHUNK_SIZE, inserts.size())
            );
            if (repository.insertKnowledgePoints(chunk) != chunk.size()) {
                throw new IllegalStateException("Knowledge point bulk insert was incomplete");
            }
        }
        questionKnowledgeMaintenanceService.maintainAfterKnowledgeDeletion(deletedIds, batchId, batch.createBy());
        if (repository.completeImport(batchId, incoming.size()) != 1) {
            throw new IllegalStateException("Import batch completion failed");
        }
    }

    private static String key(KnowledgeImportPointRow point) {
        return point.getExamSubjectId() + ":" + point.getSyllabusNumber();
    }

    private static Set<Long> rejectedRecords(
        List<KnowledgeImportPointRow> incoming, Map<Long, KnowledgeImportDiffRow> decisions
    ) {
        Set<String> rejectedNumbers = new HashSet<>();
        Set<Long> skipped = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (KnowledgeImportPointRow point : incoming) {
                KnowledgeImportDiffRow decision = decisions.get(point.getImportRecordId());
                boolean rejectAdd = decision != null && "add".equals(decision.getAction())
                    && "reject".equals(decision.getResolutionDecision());
                if (rejectAdd || rejectedNumbers.contains(point.getParentSyllabusNumber())) {
                    if (skipped.add(point.getImportRecordId())) changed = true;
                    rejectedNumbers.add(point.getSyllabusNumber());
                }
            }
        } while (changed);
        return skipped;
    }

    private Map<Integer, Long> subjectMappings(String parseConfig) {
        Map<Integer, Long> mappings = new HashMap<>();
        JsonNode subjects = objectMapper.readTree(parseConfig).path("subject_mappings");
        subjects.forEach(node -> mappings.put(
            node.path("subject_no").intValue(),
            node.path("exam_subject_id").longValue()
        ));
        return mappings;
    }

    private Map<String, Long> textbookKnowledgePointIds(Long syllabusVersionId, List<Long> subjectIds) {
        Map<String, Long> ids = new HashMap<>();
        if (syllabusVersionId == null) {
            return ids;
        }
        for (TextbookKnowledgePointLookup point : repository.selectTextbookKnowledgePointLookups(subjectIds)) {
            if (point.syllabusVersionId() == syllabusVersionId.longValue()) {
                ids.put(point.examSubjectId() + ":" + point.syllabusNumber(), point.id());
            }
        }
        return ids;
    }

    private String textbookHeadingPath(JsonNode headings) {
        Map<String, Object> normalized = jsonDocuments.flatFields(
            ImportJsonSchema.TEXTBOOK_HEADING_PATH, Map.of());
        List<String> values = new ArrayList<>();
        headings.forEach(node -> values.add(node.textValue()));
        normalized.put("headings", values);
        return objectMapper.writeValueAsString(normalized);
    }

    private String textbookSourceLocator(JsonNode row) {
        JsonNode source = row.path("source_locator");
        Map<String, Object> normalized = jsonDocuments.flatFields(
            ImportJsonSchema.TEXTBOOK_SOURCE_LOCATOR, Map.of());
        normalized.put("source_type", "markdown");
        putText(normalized, "source_key", source.get("source_key"), row.path("source_key").textValue());
        putInteger(normalized, "line_start", source.get("markdown_line_start"), null);
        putInteger(normalized, "line_end", source.get("markdown_line_end"), null);
        putInteger(normalized, "source_page_start", source.get("source_page_start"), row.get("page_start"));
        putInteger(normalized, "source_page_end", source.get("source_page_end"), row.get("page_end"));
        putInteger(normalized, "printed_page_start", source.get("printed_page_start"), null);
        putInteger(normalized, "printed_page_end", source.get("printed_page_end"), null);
        return objectMapper.writeValueAsString(normalized);
    }

    private String textbookResultData(long chunkId) {
        return jsonDocuments.flat(ImportJsonSchema.IMPORT_RESULT, Map.of(
            "object_type", "document_chunk",
            "root_ids", List.of(Long.toString(chunkId))
        ));
    }

    private void insertTextbookChunks(List<TextbookChunkInsert> rows) {
        for (int start = 0; start < rows.size(); start += INSERT_CHUNK_SIZE) {
            List<TextbookChunkInsert> part = rows.subList(
                start, Math.min(start + INSERT_CHUNK_SIZE, rows.size())
            );
            if (repository.insertTextbookChunks(part) != part.size()) {
                throw new IllegalStateException("Textbook chunk insert was incomplete");
            }
        }
    }

    private void insertTextbookRelations(List<TextbookChunkKnowledgeInsert> rows) {
        for (int start = 0; start < rows.size(); start += INSERT_CHUNK_SIZE) {
            List<TextbookChunkKnowledgeInsert> part = rows.subList(
                start, Math.min(start + INSERT_CHUNK_SIZE, rows.size())
            );
            if (repository.insertTextbookKnowledgeRelations(part) != part.size()) {
                throw new IllegalStateException("Textbook knowledge insert was incomplete");
            }
        }
    }

    private void updateTextbookResults(List<TextbookRecordResultUpdate> rows) {
        for (int start = 0; start < rows.size(); start += INSERT_CHUNK_SIZE) {
            List<TextbookRecordResultUpdate> part = rows.subList(
                start, Math.min(start + INSERT_CHUNK_SIZE, rows.size())
            );
            if (repository.updateTextbookRecordResults(part) != part.size()) {
                throw new IllegalStateException("Textbook import record update was incomplete");
            }
        }
    }

    private static String nullableText(JsonNode node) {
        return node == null || node.isNull() ? null : node.textValue();
    }

    private static void putText(Map<String, Object> target, String key, JsonNode node, String fallback) {
        String value = node == null || node.isNull() ? fallback : node.textValue();
        if (value != null) {
            target.put(key, value);
        }
    }

    private static void putInteger(
        Map<String, Object> target,
        String key,
        JsonNode primary,
        JsonNode fallback
    ) {
        JsonNode value = primary != null && primary.isIntegralNumber() ? primary : fallback;
        if (value != null && value.isIntegralNumber()) {
            target.put(key, value.intValue());
        }
    }

    private static long requiredSyllabusVersionId(CmImportBatch batch) {
        if (batch.syllabusVersionId() == null) {
            throw new IllegalStateException("Import batch has no syllabus version");
        }
        return batch.syllabusVersionId();
    }

    @Transactional
    public void enqueueCleanup(String objectKey, String expectedHash, String reason) {
        String payload = cleanupPayload(objectKey, expectedHash, reason, false);
        repository.enqueueJob(
            IdUtil.getSnowflakeNextId(),
            ImportProtocol.OBJECT_CLEANUP_JOB,
            Long.toString(IdUtil.getSnowflakeNextId()),
            payload
        );
    }

    @Transactional
    public void enqueueImageCleanup(String objectKey, String expectedHash, String reason) {
        String payload = cleanupPayload(objectKey, expectedHash, reason, true);
        repository.enqueueJobAt(
            IdUtil.getSnowflakeNextId(),
            ImportProtocol.OBJECT_CLEANUP_JOB,
            Long.toString(IdUtil.getSnowflakeNextId()),
            payload,
            OffsetDateTime.now().plusHours(1)
        );
    }

    private String cleanupPayload(String objectKey, String expectedHash, String reason, boolean checkReferences) {
        return jsonDocuments.flat(ImportJsonSchema.OBJECT_CLEANUP_JOB, Map.of(
            "object_key",
            objectKey,
            "expected_hash",
            expectedHash,
            "reason",
            reason,
            "check_references",
            checkReferences
        ));
    }

    private String jobPayload(ImportJsonSchema schema, long batchId) {
        return jsonDocuments.flat(schema, Map.of(
            "batch_id", Long.toString(batchId)
        ));
    }
}
