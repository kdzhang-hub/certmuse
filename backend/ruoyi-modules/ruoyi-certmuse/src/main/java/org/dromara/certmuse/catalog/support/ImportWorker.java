package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;

import org.dromara.certmuse.catalog.service.ImportProgressPublisher;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.KnowledgeImportReconciliationService;
import org.dromara.certmuse.catalog.validation.KnowledgePointValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Executes knowledge point import validation jobs.
 */
@Slf4j
@Component
public class ImportWorker {

    private static final int MAX_LINE_BYTES = 1_048_576;
    private static final BigDecimal MAX_PARSE_PROGRESS = new BigDecimal("30.00");
    private static final int PROGRESS_SCALE = 2;

    private final ImportMapper repository;
    private final ImportStorage storage;
    private final KnowledgePointValidator validator;
    private final JsonMapper objectMapper;
    private final VersionedJsonDocumentFactory jsonDocuments;
    private final KnowledgeImportReconciliationService reconciliationService;
    private final ImportProgressPublisher progressPublisher;

    @Autowired
    public ImportWorker(
        ImportMapper repository,
        ImportStorage storage,
        KnowledgePointValidator validator,
        @Qualifier("importStrictJsonMapper") JsonMapper objectMapper,
        VersionedJsonDocumentFactory jsonDocuments,
        KnowledgeImportReconciliationService reconciliationService,
        ImportProgressPublisher progressPublisher
    ) {
        this.repository = repository;
        this.storage = storage;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.jsonDocuments = jsonDocuments;
        this.reconciliationService = reconciliationService;
        this.progressPublisher = progressPublisher;
    }

    public void validate(long batchId) {
        try {
            CmImportBatch batch = Optional.ofNullable(repository.selectById(batchId)).orElseThrow();
            Set<Integer> mappedSubjects = new HashSet<>();
            objectMapper.readTree(batch.parseConfig())
                .path("subject_mappings")
                .forEach(node -> mappedSubjects.add(node.path("subject_no").asInt()));
            List<KnowledgePointValidator.Row> rows = storage.read(
                batch.sourceFilePath(),
                input -> read(batch, input, mappedSubjects)
            );
            if (
                repository.updateStage(
                    batchId,
                    "parsing",
                    "validating",
                    "validate_schema",
                    new BigDecimal("30.00")
                ) != 1
            ) {
                return;
            }
            progressPublisher.publish(batchId);
            advance(batchId, "validate_schema", rows.size(), 30, 50);
            repository.updateProgress(batchId, "validating", "validate_mapping", new BigDecimal("50.00"));
            progressPublisher.publish(batchId);
            advance(batchId, "validate_mapping", rows.size(), 50, 65);
            repository.updateProgress(batchId, "validating", "validate_tree", new BigDecimal("65.00"));
            progressPublisher.publish(batchId);
            validator.validateTree(rows);
            repository.updateProgress(batchId, "validating", "validate_tree", new BigDecimal("90.00"));
            progressPublisher.publish(batchId);
            for (KnowledgePointValidator.Row row : rows) {
                for (KnowledgePointValidator.Problem problem : row.problems()) {
                    repository.createIssue(
                        batchId,
                        row.recordId(),
                        problem.code(),
                        problem.field(),
                        problem.severity(),
                        problem.message()
                    );
                }
                repository.markRecord(row.recordId(), row.failed() ? "failed" : "success");
            }
            repository.updateProgress(batchId, "validating", "finalize_counts", new BigDecimal("99.00"));
            progressPublisher.publish(batchId);
            if (reconciliationService != null && repository.countIssues(batchId, "error", null) == 0) {
                if (repository.countKnowledgePoints(batch.syllabusVersionId()) == 0) {
                    reconciliationService.initializeEmptyBaseline(batchId);
                } else {
                    reconciliationService.reconcile(batchId);
                }
            }
            if (repository.finish(batchId, "validating") != 1) {
                throw new IllegalStateException("Knowledge point precheck completion failed");
            }
            progressPublisher.publish(batchId);
        } catch (Exception exception) {
            String traceId = UUID.randomUUID().toString();
            log.error("Knowledge point precheck failed, batchId={}, traceId={}", batchId, traceId, exception);
            throw new IllegalStateException("Knowledge point precheck failed", exception);
        }
    }

    private List<KnowledgePointValidator.Row> read(
        CmImportBatch batch,
        InputStream input,
        Set<Integer> mappedSubjects
    ) {
        List<KnowledgePointValidator.Row> rows = new ArrayList<>();
        int lineNumber = 0;
        long consumedBytes = 0;
        try (BufferedInputStream reader = new BufferedInputStream(input, 64 * 1024)) {
            ImportJsonlLineReader.Line line;
            while ((line = ImportJsonlLineReader.read(reader, MAX_LINE_BYTES)) != null) {
                lineNumber++;
                consumedBytes += line.consumedBytes();
                String field = "$line[" + lineNumber + "]";
                if (line.tooLarge()) {
                    repository.createIssue(
                        batch.id(),
                        null,
                        "KP_JSON_LINE_TOO_LARGE",
                        field,
                        "error",
                        "第" + lineNumber + "行超过单行大小限制"
                    );
                    continue;
                }
                JsonNode json = parseLine(batch.id(), lineNumber, field, line.bytes());
                if (json == null) {
                    continue;
                }
                String rawRecord = jsonDocuments.envelope(ImportJsonSchema.KNOWLEDGE_POINT_RAW, json);
                String rawHash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(line.bytes())
                );
                long recordId = IdUtil.getSnowflakeNextId();
                KnowledgePointValidator.Row row = validator.validateSchema(
                    recordId,
                    lineNumber,
                    json,
                    mappedSubjects
                );
                repository.insertRecord(
                    recordId,
                    batch.id(),
                    lineNumber,
                    row.safeSourceKey(),
                    rawRecord,
                    rawHash
                );
                rows.add(row);
                if (lineNumber % 100 == 0) {
                    repository.updateProgress(
                        batch.id(),
                        "parsing",
                        "read_jsonl",
                        calculateReadProgress(consumedBytes, batch.sourceFileSize())
                    );
                    progressPublisher.publish(batch.id());
                }
            }
            return rows;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to stream JSONL object", exception);
        }
    }

    private JsonNode parseLine(long batchId, int lineNumber, String field, byte[] bytes) throws IOException {
        try {
            JsonNode json = objectMapper.readTree(bytes);
            if (json == null || !json.isObject()) {
                throw new IOException();
            }
            return json;
        } catch (JacksonException exception) {
            String code = exception.getMessage().contains("Duplicate field")
                ? "KP_JSON_DUPLICATE_KEY"
                : "KP_JSON_INVALID";
            repository.createIssue(
                batchId,
                null,
                code,
                field,
                "error",
                "第" + lineNumber + "行不是合法JSON对象"
            );
            return null;
        } catch (IOException exception) {
            repository.createIssue(
                batchId,
                null,
                "KP_JSON_INVALID",
                field,
                "error",
                "第" + lineNumber + "行不是合法JSON对象"
            );
            return null;
        }
    }

    private void advance(long batchId, String stage, int total, int lower, int upper) {
        if (total == 0) {
            repository.updateProgress(batchId, "validating", stage, BigDecimal.valueOf(upper));
            progressPublisher.publish(batchId);
            return;
        }
        for (int completed = 100; completed < total; completed += 100) {
            repository.updateProgress(
                batchId,
                "validating",
                stage,
                calculateStageProgress(completed, total, lower, upper)
            );
            progressPublisher.publish(batchId);
        }
        repository.updateProgress(batchId, "validating", stage, BigDecimal.valueOf(upper));
        progressPublisher.publish(batchId);
    }

    static BigDecimal calculateReadProgress(long consumedBytes, long sourceFileSize) {
        BigDecimal progress = BigDecimal.valueOf(consumedBytes)
            .multiply(BigDecimal.valueOf(30))
            .divide(BigDecimal.valueOf(sourceFileSize), PROGRESS_SCALE, RoundingMode.DOWN);
        return progress.min(MAX_PARSE_PROGRESS).setScale(PROGRESS_SCALE, RoundingMode.DOWN);
    }

    static BigDecimal calculateStageProgress(int completed, int total, int lower, int upper) {
        BigDecimal completedProgress = BigDecimal.valueOf(upper - lower)
            .multiply(BigDecimal.valueOf(completed))
            .divide(BigDecimal.valueOf(total), PROGRESS_SCALE, RoundingMode.DOWN);
        return BigDecimal.valueOf(lower)
            .add(completedProgress)
            .setScale(PROGRESS_SCALE, RoundingMode.DOWN);
    }

}
