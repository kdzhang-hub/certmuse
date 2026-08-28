package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.service.ImportProgressPublisher;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.TextbookImportDocument;
import org.dromara.certmuse.catalog.domain.TextbookKnowledgePointLookup;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.validation.TextbookImportValidator;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Executes persistent textbook JSONL precheck jobs. */
@Slf4j
@Component
public class TextbookImportWorker {

    private static final int MAX_LINE_BYTES = 1_048_576;

    private final ImportMapper repository;
    private final ImportStorage storage;
    private final TextbookImportValidator validator;
    private final JsonMapper objectMapper;
    private final ImportProgressPublisher progressPublisher;

    public TextbookImportWorker(
        ImportMapper repository,
        ImportStorage storage,
        TextbookImportValidator validator,
        @Qualifier("importStrictJsonMapper") JsonMapper objectMapper,
        ImportProgressPublisher progressPublisher
    ) {
        this.repository = repository;
        this.storage = storage;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.progressPublisher = progressPublisher;
    }

    public void validate(long batchId) {
        try {
            CmImportBatch batch = Optional.ofNullable(repository.selectById(batchId)).orElseThrow();
            if (!"document_chunk".equals(batch.importType())) {
                throw new IllegalStateException("Batch is not a textbook import");
            }
            TextbookImportDocument document = batch.documentId() == null ? null : Optional
                .ofNullable(repository.selectTextbookDocument(batch.documentId(), null))
                .orElseThrow(() -> new IllegalStateException("Textbook document is unavailable"));
            if (document != null && batch.certificationId() != null
                && batch.certificationId() != document.certificationId()) {
                throw new IllegalStateException("Textbook import certification does not match document");
            }
            Map<Integer, Long> subjectMappings = subjectMappings(batch.parseConfig());
            Long syllabusVersionId = document == null || document.syllabusVersionId() == null
                ? batch.syllabusVersionId() : document.syllabusVersionId();
            List<TextbookKnowledgePointLookup> knowledgePoints = syllabusVersionId == null
                ? List.of()
                : repository.selectTextbookKnowledgePointLookups(new ArrayList<>(subjectMappings.values()));
            TextbookImportValidator.Context context = validator.context(
                syllabusVersionId, subjectMappings, knowledgePoints
            );
            List<TextbookImportValidator.Row> rows = storage.read(
                batch.sourceFilePath(), input -> read(batch, input, context)
            );
            if (repository.updateStage(
                batchId, "parsing", "validating", "validate_schema", new BigDecimal("55.00")
            ) != 1) {
                return;
            }
            progressPublisher.publish(batchId);
            repository.updateProgress(batchId, "validating", "validate_mapping", new BigDecimal("75.00"));
            progressPublisher.publish(batchId);
            addChunkGapWarning(rows);
            repository.updateProgress(batchId, "validating", "finalize_counts", new BigDecimal("99.00"));
            progressPublisher.publish(batchId);
            for (TextbookImportValidator.Row row : rows) {
                for (TextbookImportValidator.Problem problem : row.problems()) {
                    repository.createIssue(
                        batchId, row.recordId(), problem.code(), problem.field(), problem.severity(), problem.message()
                    );
                }
                if (row.failed()) {
                    repository.markRecord(row.recordId(), "failed");
                }
            }
            if (repository.finishTextbook(batchId, "validating") != 1) {
                throw new IllegalStateException("Textbook precheck completion failed");
            }
            progressPublisher.publish(batchId);
        } catch (Exception exception) {
            String traceId = UUID.randomUUID().toString();
            log.error("Textbook precheck failed, batchId={}, traceId={}", batchId, traceId, exception);
            throw new IllegalStateException("Textbook precheck failed", exception);
        }
    }

    private List<TextbookImportValidator.Row> read(
        CmImportBatch batch,
        InputStream input,
        TextbookImportValidator.Context context
    ) {
        List<TextbookImportValidator.Row> rows = new ArrayList<>();
        Map<Integer, Integer> firstChunkLines = new HashMap<>();
        int lineNumber = 0;
        long consumedBytes = 0;
        try (BufferedInputStream reader = new BufferedInputStream(input, 64 * 1024)) {
            ImportJsonlLineReader.Line line;
            while ((line = ImportJsonlLineReader.read(reader, MAX_LINE_BYTES)) != null) {
                lineNumber++;
                consumedBytes += line.consumedBytes();
                String lineField = "$line[" + lineNumber + "]";
                if (line.tooLarge()) {
                    repository.createIssue(
                        batch.id(), null, "IMPORT_JSON_LINE_INVALID", lineField, "error",
                        "第" + lineNumber + "行超过1MiB"
                    );
                    continue;
                }
                if (isBlank(line.bytes())) {
                    continue;
                }
                JsonNode json = parseLine(batch.id(), lineNumber, lineField, line.bytes());
                if (json == null) {
                    continue;
                }
                long recordId = IdUtil.getSnowflakeNextId();
                TextbookImportValidator.Row row = validator.validate(recordId, lineNumber, json, context);
                if (row.chunkNo() != null) {
                    Integer firstLine = firstChunkLines.putIfAbsent(row.chunkNo(), lineNumber);
                    if (firstLine != null) {
                        row.addProblem(
                            "IMPORT_CHUNK_NO_DUPLICATE", "chunk_no", "error",
                            "chunk_no与第" + firstLine + "行重复"
                        );
                    }
                }
                repository.insertRecord(
                    recordId,
                    batch.id(),
                    lineNumber,
                    row.safeSourceKey(),
                    rawEnvelope(json),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(line.rawBytes()))
                );
                rows.add(row);
                if (lineNumber % 100 == 0) {
                    repository.updateProgress(
                        batch.id(), "parsing", "read_jsonl", readProgress(consumedBytes, batch.sourceFileSize())
                    );
                    progressPublisher.publish(batch.id());
                }
            }
            return rows;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to stream textbook JSONL", exception);
        }
    }

    private JsonNode parseLine(long batchId, int lineNumber, String field, byte[] bytes) {
        try {
            JsonNode json = objectMapper.readTree(bytes);
            if (json == null || !json.isObject()) {
                throw new IOException();
            }
            return json;
        } catch (JacksonException exception) {
            repository.createIssue(
                batchId, null, "IMPORT_JSON_LINE_INVALID", field, "error",
                "第" + lineNumber + "行不是合法JSON对象"
            );
            return null;
        } catch (IOException exception) {
            repository.createIssue(
                batchId, null, "IMPORT_JSON_LINE_INVALID", field, "error",
                "第" + lineNumber + "行不是合法JSON对象"
            );
            return null;
        }
    }

    private String rawEnvelope(JsonNode record) {
        Map<String, Object> envelope = VersionedJsonDocumentFactory.flatFields(
            ImportJsonSchema.DOCUMENT_CHUNK_RAW_RECORD, Map.of());
        envelope.put("record", record);
        return objectMapper.writeValueAsString(envelope);
    }

    private Map<Integer, Long> subjectMappings(String parseConfig) {
        Map<Integer, Long> mappings = new HashMap<>();
        objectMapper.readTree(parseConfig).path("subject_mappings").forEach(node -> mappings.put(
            node.path("subject_no").intValue(), node.path("exam_subject_id").longValue()
        ));
        return mappings;
    }

    private void addChunkGapWarning(List<TextbookImportValidator.Row> rows) {
        List<TextbookImportValidator.Row> ordered = rows.stream()
            .filter(row -> row.chunkNo() != null && row.chunkNo() > 0)
            .sorted(Comparator.comparing(TextbookImportValidator.Row::chunkNo))
            .toList();
        if (ordered.isEmpty()) {
            return;
        }
        List<String> gaps = new ArrayList<>();
        int previous = 0;
        for (TextbookImportValidator.Row row : ordered) {
            int current = row.chunkNo();
            if (current > previous + 1) {
                gaps.add(previous + 1 == current - 1
                    ? Integer.toString(previous + 1)
                    : (previous + 1) + "-" + (current - 1));
            }
            previous = Math.max(previous, current);
        }
        if (!gaps.isEmpty()) {
            ordered.getFirst().addProblem(
                "IMPORT_CHUNK_NO_GAP", "chunk_no", "warning",
                "chunk_no存在缺号：" + String.join(",", gaps)
            );
        }
    }

    private static BigDecimal readProgress(long consumedBytes, long sourceFileSize) {
        if (sourceFileSize <= 0) {
            return BigDecimal.ZERO;
        }
        long percent = Math.min(50, consumedBytes * 50 / sourceFileSize);
        return BigDecimal.valueOf(percent).setScale(2);
    }

    private static boolean isBlank(byte[] bytes) {
        for (byte value : bytes) {
            if (!Character.isWhitespace(value & 0xff)) {
                return false;
            }
        }
        return true;
    }

}
