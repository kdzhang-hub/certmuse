package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;

import org.dromara.certmuse.catalog.service.ImportProgressPublisher;

import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.QuestionImportPersistenceService;
import org.dromara.certmuse.shared.QuestionSemanticHasher;
import org.dromara.certmuse.shared.schema.SharedJsonSchema;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Orchestrates question ZIP validation, image upload and transactional persistence. */
@Slf4j
@Component
public class QuestionImportWorker {
    static final int MAX_IMAGE_DIMENSION = 10_000;
    static final long MAX_IMAGE_PIXELS = 25_000_000L;
    private static final String QUESTION_IMAGE_PREFIX = "question-imports/";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif");

    private final ImportMapper repository;
    private final ImportStorage storage;
    private final ImportPersistenceService importPersistence;
    private final QuestionImportPersistenceService questionPersistence;
    private final JsonMapper objectMapper;
    private final VersionedJsonDocumentFactory jsonDocuments;
    private final ImportProgressPublisher progressPublisher;

    public QuestionImportWorker(
        ImportMapper repository,
        ImportStorage storage,
        ImportPersistenceService importPersistence,
        QuestionImportPersistenceService questionPersistence,
        @Qualifier("importStrictJsonMapper") JsonMapper objectMapper,
        VersionedJsonDocumentFactory jsonDocuments,
        ImportProgressPublisher progressPublisher
    ) {
        this.repository = repository;
        this.storage = storage;
        this.importPersistence = importPersistence;
        this.questionPersistence = questionPersistence;
        this.objectMapper = objectMapper;
        this.jsonDocuments = jsonDocuments;
        this.progressPublisher = progressPublisher;
    }

    public void validate(long batchId) {
        CmImportBatch batch = require(batchId);
        try {
            storage.read(batch.sourceFilePath(), input -> {
                try (QuestionImportArchive archive = QuestionImportArchive.extract(input)) {
                    validateArchive(batch, archive);
                }
                return null;
            });
        } catch (QuestionImportValidationException exception) {
            issue(batch.id(), null, exception.code(), exception.fieldPath(), "error", exception.getMessage());
        } catch (Exception exception) {
            log.error("Question ZIP precheck failed, batchId={}", batchId, exception);
            throw new IllegalStateException("Question ZIP precheck failed", exception);
        }
        repository.updateStage(batchId, "parsing", "validating", "validate_schema", java.math.BigDecimal.valueOf(95));
        progressPublisher.publish(batchId);
        if ("paper".equals(batch.importType())) {
            repository.finishPaper(batchId, "validating");
        } else {
            repository.finishQuestion(batchId, "validating");
        }
        progressPublisher.publish(batchId);
    }

    public void persist(long batchId) {
        CmImportBatch batch = require(batchId);
        if (!"importing".equals(batch.status())) {
            return;
        }
        List<JsonNode> rows = repository.selectSuccessfulQuestionResult(batchId).stream().map(this::tree).toList();
        if (rows.isEmpty() || rows.size() != batch.validCount()) {
            throw new IllegalStateException("Validated question records changed");
        }
        Set<String> uploadedKeys = new HashSet<>();
        try {
            storage.read(batch.sourceFilePath(), input -> {
                try (QuestionImportArchive archive = QuestionImportArchive.extract(input)) {
                    uploadImages(batch.sourceFileHash(), rows, archive, uploadedKeys);
                }
                return null;
            });
            questionPersistence.persistBatch(batchId);
        } catch (Exception exception) {
            uploadedKeys.forEach(key -> {
                try {
                    importPersistence.enqueueImageCleanup(key, batch.sourceFileHash(), "question_persist_failed");
                } catch (RuntimeException cleanupException) {
                    log.warn("Failed to enqueue imported image cleanup, objectKey={}", key, cleanupException);
                }
            });
            throw exception;
        }
    }

    private void validateArchive(CmImportBatch batch, QuestionImportArchive archive) {
        Map<String, Object> config = readObject(batch.parseConfig());
        Map<Integer, SubjectContext> derivedSubjects = derivedSubjects(config);
        Long syllabusVersionId = batch.syllabusVersionId();
        if ("paper".equals(batch.importType()) && syllabusVersionId == null) {
            throw new IllegalStateException("Paper import batch has no syllabus version");
        }
        String certificationName = String.valueOf(config.get("certification_name"));
        Set<String> qids = new HashSet<>();
        Map<String, Integer> semanticFingerprintLines = new HashMap<>();
        Set<String> referencedImages = new HashSet<>();
        boolean paper = "paper".equals(batch.importType());
        readJsonLines(archive.jsonl(), (lineNo, line) -> validateLine(
            batch, archive, lineNo, line, syllabusVersionId, derivedSubjects,
            certificationName, qids, semanticFingerprintLines, referencedImages, paper
        ));
        archive.imageNames().stream()
            .filter(path -> !referencedImages.contains(path))
            .forEach(path -> issue(batch.id(), null, "QUESTION_IMAGE_UNREFERENCED", path, "warning", "图片未被题目引用"));
    }

    private void validateLine(
        CmImportBatch batch,
        QuestionImportArchive archive,
        int lineNo,
        byte[] line,
        Long syllabusVersionId,
        Map<Integer, SubjectContext> derivedSubjects,
        String certificationName,
        Set<String> qids,
        Map<String, Integer> semanticFingerprintLines,
        Set<String> referencedImages,
        boolean paper
    ) {
        if (line == null) {
            issue(batch.id(), null, "QUESTION_JSON_LINE_TOO_LARGE", "$line[" + lineNo + "]", "error", "单行超过1MiB");
            return;
        }
        String decoded;
        try {
            decoded = strictUtf8(line);
        } catch (CharacterCodingException exception) {
            issue(batch.id(), null, "QUESTION_JSON_ENCODING_INVALID", "$line[" + lineNo + "]", "error", "不是合法UTF-8");
            return;
        }
        JsonNode json;
        try {
            json = objectMapper.readTree(decoded);
        } catch (Exception exception) {
            issue(batch.id(), null, "QUESTION_JSON_INVALID", "$line[" + lineNo + "]", "error", "不是合法JSON对象");
            return;
        }
        if (json == null || !json.isObject()) {
            issue(batch.id(), null, "QUESTION_JSON_INVALID", "$line[" + lineNo + "]", "error", "不是JSON对象");
            return;
        }
        long recordId = repository.createRecord(batch.id(), lineNo, json.path("qid").asText(null), rawRecord(json), sha(line));
        List<Problem> problems = new ArrayList<>();
        Map<String, Object> normalized = normalize(
            json, archive, referencedImages, syllabusVersionId, derivedSubjects,
            certificationName, qids, semanticFingerprintLines, lineNo, problems, paper, batch.createBy()
        );
        problems.forEach(problem -> issue(batch.id(), recordId, problem.code(), problem.path(), problem.severity(), problem.message()));
        if (problems.stream().anyMatch(problem -> "error".equals(problem.severity()))) {
            repository.markRecord(recordId, "failed");
        } else if (!problems.isEmpty()) {
            repository.markRecord(recordId, "skipped");
        } else {
            repository.updateQuestionRecordResult(recordId, (int) normalized.get("subject_no"),
                (long) normalized.get("exam_subject_id"), resultData(normalized), "success");
        }
    }

    private Map<String, Object> normalize(
        JsonNode json,
        QuestionImportArchive archive,
        Set<String> referencedImages,
        Long syllabusVersionId,
        Map<Integer, SubjectContext> derivedSubjects,
        String certificationName,
        Set<String> qids,
        Map<String, Integer> semanticFingerprintLines,
        int lineNo,
        List<Problem> problems,
        boolean paper,
        Long visibleUserId
    ) {
        String qid = required(json, "qid", 200, problems);
        String source = required(json, "source", 4000, problems);
        String subject = required(json, "subject", 200, problems);
        String stem = required(json, "question", Integer.MAX_VALUE, problems);
        if (subject != null && !certificationName.equals(subject)) {
            problems.add(error("QUESTION_CERTIFICATION_MISMATCH", "subject", "考试资格不匹配"));
        }
        if (qid != null && !qids.add(qid)) {
            problems.add(error("QUESTION_QID_DUPLICATE_IN_FILE", "qid", "文件内qid重复"));
        }
        String sourceHash = source == null || qid == null ? null : sha((source + "\n" + qid).getBytes(StandardCharsets.UTF_8));
        boolean sourceDuplicate = sourceHash != null && repository.countQuestionSource(sourceHash) != 0;
        if (sourceDuplicate && !paper) {
            problems.add(error("QUESTION_QID_DUPLICATE_IN_SYSTEM", "qid", "系统中已存在相同来源题目"));
        }

        String questionType = questionType(text(json, "type"), problems);
        List<Map<String, String>> options = normalizeOptions(json.path("options"), questionType, problems);
        Object answer = normalizeAnswer(json, questionType, options, problems);
        SubjectContext subjectContext = deriveSubject(json.path("knowledge_points"), derivedSubjects, problems);
        List<Map<String, Object>> knowledge = normalizeKnowledge(
            json.path("knowledge_points"), questionType, syllabusVersionId, subjectContext, problems
        );
        validateUnsupportedScoringPoints(json.get("scoring_points"), json.has("scoring_points"), problems);
        List<Map<String, Object>> images = normalizeImages(json.path("images"), archive, referencedImages, problems);
        String normalizedStem = normalizeStem(stem, images);
        String analysis = text(json, "analysis");
        String semanticHash = semanticHash(questionType, normalizedStem, options);
        Map<String, Object> resultReusePlaceholder = new HashMap<>();
        if (subjectContext != null && semanticHash != null && !hasErrors(problems)) {
            Integer firstLine = semanticFingerprintLines.putIfAbsent(semanticHash, lineNo);
            if (firstLine != null) {
                problems.add(error("QUESTION_CONTENT_DUPLICATE_IN_FILE", "question", "与文件第 " + firstLine + " 行题目内容重复"));
            } else if (paper) {
                List<org.dromara.certmuse.catalog.domain.PaperQuestionReuse> reuses = repository.findPaperQuestionReuses(
                    subjectContext.examSubjectId(), syllabusVersionId, semanticHash, visibleUserId
                );
                if (reuses.size() > 1) {
                    problems.add(error("PAPER_DUPLICATE_AMBIGUOUS", "question", "题库中存在多道相同内容题目"));
                } else if (reuses.size() == 1) {
                    var reuse = reuses.getFirst();
                    resultReusePlaceholder.put("revision_id", reuse.revisionId());
                    resultReusePlaceholder.put("answer_schema", reuse.answerSchema());
                }
            } else if (!sourceDuplicate) {
                String duplicateQuestionCode = repository.findQuestionSemanticDuplicate(subjectContext.examSubjectId(), semanticHash);
                if (duplicateQuestionCode != null) {
                    problems.add(error("QUESTION_CONTENT_DUPLICATE_IN_SYSTEM", "question", "与已有题目 " + duplicateQuestionCode + " 内容重复"));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("qid", qid);
        result.put("source", source);
        result.put("subject", subject);
        result.put("source_hash", sourceHash);
        result.put("question_type", questionType);
        result.put("stem", normalizedStem);
        result.put("options", options);
        result.put("answer", answer);
        result.put("analysis", analysis);
        result.put("images", images);
        result.put("knowledge_points", knowledge);
        if (subjectContext != null) {
            result.put("subject_no", subjectContext.subjectNo());
            result.put("exam_subject_id", subjectContext.examSubjectId());
        }
        if (semanticHash != null) {
            result.put("semantic_hash", semanticHash);
        }
        result.put("content_hash", contentHash(questionType, normalizedStem, options, answer, analysis, images, knowledge));
        if (!resultReusePlaceholder.isEmpty()) {
            result.put("resolution", "REUSE");
            result.put("reused_question_revision_id", resultReusePlaceholder.get("revision_id"));
            result.put("reused_answer_schema", resultReusePlaceholder.get("answer_schema"));
        } else {
            result.put("resolution", "CREATE");
        }
        return result;
    }

    private static boolean hasErrors(List<Problem> problems) {
        return problems.stream().anyMatch(problem -> "error".equals(problem.severity()));
    }

    private static String semanticHash(String questionType, String stem, List<Map<String, String>> options) {
        if (questionType == null) return null;
        return QuestionSemanticHasher.hash(questionType, stem, options.stream().map(option -> option.get("text")).toList());
    }

    private String questionType(String type, List<Problem> problems) {
        return switch (type == null ? "" : type) {
            case "single" -> "CHOICE";
            case "subjective" -> "CASE";
            case "essay" -> "ESSAY";
            default -> {
                problems.add(error("QUESTION_TYPE_INVALID", "type", "题型不支持"));
                yield null;
            }
        };
    }

    private List<Map<String, String>> normalizeOptions(JsonNode source, String questionType, List<Problem> problems) {
        List<Map<String, String>> options = new ArrayList<>();
        if ("CHOICE".equals(questionType)) {
            if (!source.isObject() || source.size() < 2 || source.size() > 26) {
                problems.add(error("QUESTION_OPTIONS_INVALID", "options", "单选题选项数量必须为2至26"));
            } else {
                source.properties().forEach(entry -> {
                    String value = text(entry.getValue());
                    if (!entry.getKey().matches("[A-Z]") || value == null) {
                        problems.add(error("QUESTION_OPTIONS_INVALID", "options." + entry.getKey(), "选项标签或文本无效"));
                    } else {
                        options.add(Map.of("label", entry.getKey(), "text", value));
                    }
                });
                options.sort(Comparator.comparing(option -> option.get("label")));
            }
        } else if (!source.isObject() || source.size() != 0) {
            problems.add(error("QUESTION_OPTIONS_INVALID", "options", "非选择题选项必须为空对象"));
        }
        return options;
    }

    private Object normalizeAnswer(
        JsonNode json,
        String questionType,
        List<Map<String, String>> options,
        List<Problem> problems
    ) {
        String rawAnswer = text(json, "answer");
        if (rawAnswer == null) {
            problems.add(warning("QUESTION_ANSWER_MISSING", "answer", "答案为空"));
            return null;
        }
        if ("CHOICE".equals(questionType)) {
            String label = rawAnswer.toUpperCase(Locale.ROOT);
            if (options.stream().noneMatch(option -> label.equals(option.get("label")))) {
                problems.add(error("QUESTION_ANSWER_INVALID", "answer", "答案未命中选项"));
                return null;
            }
            return answerDocument(Map.of(
                "answer_type", "option_keys",
                "selection_mode", "single", "value", List.of(label)
            ));
        }
        return answerDocument(Map.of("answer_type", "reference_text", "value", rawAnswer));
    }

    private List<Map<String, Object>> normalizeKnowledge(
        JsonNode nodes,
        String questionType,
        Long syllabusVersionId,
        SubjectContext subjectContext,
        List<Problem> problems
    ) {
        List<Map<String, Object>> knowledge = new ArrayList<>();
        if (!nodes.isArray() || nodes.isEmpty()) {
            return knowledge;
        }
        if (subjectContext == null) return knowledge;
        if (syllabusVersionId == null) return knowledge;
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < nodes.size(); index++) {
            JsonNode node = nodes.get(index);
            String code = text(node, "code");
            if (code == null || !codes.add(code)) {
                problems.add(error(code == null ? "QUESTION_KNOWLEDGE_NOT_FOUND" : "QUESTION_KNOWLEDGE_DUPLICATE",
                    "knowledge_points[" + index + "].code", "知识点编码无效或重复"));
                continue;
            }
            Long id = repository.findKnowledgePoint(syllabusVersionId, subjectContext.examSubjectId(), code);
            if (id == null) {
                problems.add(error("QUESTION_KNOWLEDGE_NOT_FOUND", "knowledge_points[" + index + "].code", "知识点不存在"));
                continue;
            }
            if (!repository.isKnowledgePointLeaf(id)) {
                problems.add(error("QUESTION_KNOWLEDGE_NOT_LEAF", "knowledge_points[" + index + "].code", "只能关联最末级知识点"));
            }
            knowledge.add(Map.of(
                "id", id,
                "code", code,
                "role", index == 0 ? "primary" : "secondary",
                "sort_order", index
            ));
        }
        return knowledge;
    }

    private void validateUnsupportedScoringPoints(JsonNode source, boolean supplied, List<Problem> problems) {
        if (!supplied || (source != null && source.isArray() && source.isEmpty())) return;
        problems.add(error("QUESTION_SCORING_POINTS_UNSUPPORTED", "scoring_points", "题库已不支持人工评分点；请删除该字段或传空数组"));
    }

    private SubjectContext deriveSubject(JsonNode nodes, Map<Integer, SubjectContext> derivedSubjects, List<Problem> problems) {
        if (!nodes.isArray() || nodes.isEmpty()) {
            problems.add(error("QUESTION_KNOWLEDGE_REQUIRED", "knowledge_points", "知识点不能为空"));
            return null;
        }
        Integer subjectNo = null;
        for (int index = 0; index < nodes.size(); index++) {
            JsonNode value = nodes.get(index).get("subject_no");
            if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
                problems.add(error("QUESTION_SUBJECT_MISMATCH", "knowledge_points[" + index + "].subject_no", "科目编号无效"));
                continue;
            }
            if (subjectNo != null && subjectNo != value.intValue()) {
                problems.add(error("QUESTION_KNOWLEDGE_CROSS_SUBJECT", "knowledge_points[" + index + "].subject_no", "同一题目的知识点必须属于同一科目"));
            }
            subjectNo = value.intValue();
        }
        if (subjectNo == null) return null;
        SubjectContext context = derivedSubjects.get(subjectNo);
        if (context == null) {
            problems.add(error("QUESTION_SUBJECT_MISMATCH", "knowledge_points", "题目科目不属于当前批次"));
        }
        return context;
    }

    private List<Map<String, Object>> normalizeImages(
        JsonNode nodes,
        QuestionImportArchive archive,
        Set<String> referencedImages,
        List<Problem> problems
    ) {
        List<Map<String, Object>> images = new ArrayList<>();
        if (!nodes.isArray()) {
            problems.add(error("QUESTION_IMAGE_INVALID", "images", "images必须为数组"));
            return images;
        }
        for (int index = 0; index < nodes.size(); index++) {
            String sourcePath = text(nodes.get(index));
            Path image = sourcePath == null ? null : archive.image(sourcePath);
            if (image == null) {
                problems.add(error("QUESTION_IMAGE_MISSING", "images[" + index + "]", "图片不存在或路径无效"));
                continue;
            }
            String extension = sourcePath.substring(sourcePath.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            ImageMetadata metadata = inspectImage(image);
            if (!IMAGE_EXTENSIONS.contains(extension) || metadata == null || !sameFormat(extension, metadata.format())) {
                problems.add(error("QUESTION_IMAGE_INVALID", "images[" + index + "]", "图片真实格式与扩展名不匹配"));
                continue;
            }
            if (metadata.width() > MAX_IMAGE_DIMENSION || metadata.height() > MAX_IMAGE_DIMENSION
                || (long) metadata.width() * metadata.height() > MAX_IMAGE_PIXELS) {
                problems.add(error("QUESTION_IMAGE_DIMENSIONS_EXCEEDED", "images[" + index + "]", "图片尺寸超过限制"));
                continue;
            }
            referencedImages.add(sourcePath);
            images.add(Map.of("path", sourcePath, "hash", sha(image), "extension", canonicalExtension(metadata.format())));
        }
        return images;
    }

    private String contentHash(
        String questionType,
        String stem,
        List<Map<String, String>> options,
        Object answer,
        String analysis,
        List<Map<String, Object>> images,
        List<Map<String, Object>> knowledge
    ) {
        List<List<Object>> optionIdentity = options.stream()
            .map(option -> List.<Object>of(option.get("label"), option.get("text")))
            .toList();
        List<List<Object>> imageIdentity = images.stream()
            .map(image -> List.of(image.get("hash"), image.get("extension")))
            .toList();
        List<List<Object>> knowledgeIdentity = knowledge.stream()
            .map(item -> List.of(item.get("id"), item.get("role"), item.get("sort_order")))
            .toList();
        Object answerIdentity = canonicalAnswer(answer);
        return sha(write(List.of(
            Optional.ofNullable(questionType).orElse(""), stem, optionIdentity,
            answerIdentity, Optional.ofNullable(analysis).orElse(""),
            imageIdentity, knowledgeIdentity
        )).getBytes(StandardCharsets.UTF_8));
    }

    private static Integer integer(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToInt() ? node.intValue() : null;
    }

    private static Object canonicalAnswer(Object answer) {
        if (!(answer instanceof Map<?, ?> values)) {
            return "";
        }
        return java.util.Arrays.asList(
            values.get("schema_version"),
            values.get("answer_type"),
            values.get("selection_mode"),
            values.get("value")
        );
    }

    private String rawRecord(JsonNode question) {
        return jsonDocuments.envelope(ImportJsonSchema.QUESTION_RAW, question);
    }

    private String resultData(Map<String, Object> normalized) {
        return jsonDocuments.flat(ImportJsonSchema.QUESTION_IMPORT_RESULT, normalized);
    }

    private Map<String, Object> answerDocument(Map<String, Object> fields) {
        return jsonDocuments.flatFields(SharedJsonSchema.QUESTION_ANSWER, fields);
    }

    private void uploadImages(String zipHash, List<JsonNode> rows, QuestionImportArchive archive, Set<String> uploaded) {
        for (JsonNode row : rows) {
            if ("REUSE".equals(row.path("resolution").asText())) continue;
            for (JsonNode image : row.path("images")) {
                String sourcePath = image.path("path").asText();
                Path source = archive.image(sourcePath);
                if (source == null) {
                    throw new IllegalStateException("Validated image disappeared: " + sourcePath);
                }
                String objectKey = imageKey(zipHash, image.path("hash").asText(), image.path("extension").asText());
                if (uploaded.add(objectKey)) {
                    storage.upload(objectKey, source);
                }
            }
        }
    }

    public static String imageKey(String zipHash, String hash, String extension) {
        return QUESTION_IMAGE_PREFIX + zipHash + "/images/" + hash + "." + extension;
    }

    static void readJsonLines(Path jsonl, LineConsumer consumer) {
        try (InputStream file = Files.newInputStream(jsonl);
             PushbackInputStream input = new PushbackInputStream(new BufferedInputStream(file, 16 * 1024), 3)) {
            byte[] prefix = input.readNBytes(3);
            if (prefix.length == 3 && prefix[0] == (byte) 0xEF && prefix[1] == (byte) 0xBB && prefix[2] == (byte) 0xBF) {
                throw new QuestionImportValidationException("QUESTION_JSON_ENCODING_INVALID", "file", "questions.jsonl 不能包含 BOM");
            }
            input.unread(prefix);
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            boolean tooLong = false;
            int lineNo = 1;
            int value;
            while ((value = input.read()) != -1) {
                if (value == '\n') {
                    consumer.accept(lineNo++, tooLong ? null : trimCarriageReturn(line.toByteArray()));
                    line.reset();
                    tooLong = false;
                } else if (!tooLong) {
                    if (line.size() >= QuestionImportArchive.MAX_LINE_BYTES) {
                        tooLong = true;
                        line.reset();
                    } else {
                        line.write(value);
                    }
                }
            }
            if (tooLong || line.size() > 0) {
                consumer.accept(lineNo, tooLong ? null : trimCarriageReturn(line.toByteArray()));
            }
        } catch (QuestionImportValidationException exception) {
            throw exception;
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("读取 questions.jsonl 失败", exception);
        }
    }

    private static byte[] trimCarriageReturn(byte[] line) {
        if (line.length == 0 || line[line.length - 1] != '\r') {
            return line;
        }
        return java.util.Arrays.copyOf(line, line.length - 1);
    }

    static String strictUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
    }

    private static ImageMetadata inspectImage(Path path) {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                return null;
            }
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new ImageMetadata(reader.getFormatName().toLowerCase(Locale.ROOT), reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean sameFormat(String extension, String format) {
        return canonicalExtension(extension).equals(canonicalExtension(format));
    }

    private static String canonicalExtension(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "jpeg", "jpg" -> "jpg";
            default -> format.toLowerCase(Locale.ROOT);
        };
    }

    private CmImportBatch require(long id) {
        return Optional.ofNullable(repository.selectById(id)).orElseThrow(() -> new IllegalStateException("Import batch not found"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObject(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("配置无效", exception);
        }
    }

    private static Map<Integer, SubjectContext> derivedSubjects(Map<String, Object> config) {
        Object value = config.get("derived_subjects");
        if (!(value instanceof List<?> values) || values.isEmpty()) throw new IllegalArgumentException("配置缺少 derived_subjects");
        Map<Integer, SubjectContext> subjects = new LinkedHashMap<>();
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> row) || !(row.get("subject_no") instanceof Number no)
                || !(row.get("exam_subject_id") instanceof Number id)) throw new IllegalArgumentException("derived_subjects 无效");
            subjects.put(no.intValue(), new SubjectContext(no.intValue(), id.longValue()));
        }
        return subjects;
    }

    private JsonNode tree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String text(JsonNode node, String field) {
        return text(node.get(field));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static String required(JsonNode node, String field, int maxLength, List<Problem> problems) {
        String value = text(node, field);
        if (value == null || value.length() > maxLength) {
            problems.add(error("QUESTION_FIELD_INVALID", field, "字段不能为空或超过长度限制"));
            return null;
        }
        return value;
    }

    private static String normalizeStem(String stem, List<Map<String, Object>> images) {
        if (stem == null) {
            return "";
        }
        String value = stem;
        for (int index = 0; index < images.size(); index++) {
            value = value.replace((String) images.get(index).get("path"), "{{question-image:" + (index + 1) + "}}");
        }
        return value;
    }

    private static String sha(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sha(Path path) {
        try (InputStream input = Files.newInputStream(path);
             DigestInputStream digestInput = new DigestInputStream(input, MessageDigest.getInstance("SHA-256"))) {
            digestInput.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(digestInput.getMessageDigest().digest());
        } catch (Exception exception) {
            throw new IllegalStateException("计算图片哈希失败", exception);
        }
    }

    private static Problem error(String code, String path, String message) {
        return new Problem(code, path, "error", message);
    }

    private static Problem warning(String code, String path, String message) {
        return new Problem(code, path, "warning", message);
    }

    private void issue(long batchId, Long recordId, String code, String path, String severity, String message) {
        repository.createIssue(batchId, recordId, code, path, severity, message);
    }

    private record Problem(String code, String path, String severity, String message) {
    }

    private record SubjectContext(int subjectNo, long examSubjectId) {
    }

    private record ImageMetadata(String format, int width, int height) {
    }

    @FunctionalInterface
    interface LineConsumer {
        void accept(int lineNo, byte[] line);
    }

}
