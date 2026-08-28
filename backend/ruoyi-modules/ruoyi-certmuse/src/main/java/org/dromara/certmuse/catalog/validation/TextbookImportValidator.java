package org.dromara.certmuse.catalog.validation;

import org.dromara.certmuse.catalog.domain.TextbookKnowledgePointLookup;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Schema and knowledge-mapping validation for textbook JSONL rows. */
@Component
public class TextbookImportValidator {

    private static final Set<String> FIELDS = Set.of(
        "source_key", "chunk_no", "heading", "heading_path", "content", "page_start", "page_end",
        "source_locator", "content_hash", "knowledge_points", "images"
    );
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    public record Problem(String code, String field, String severity, String message) {
    }

    public static final class Row {
        private final long recordId;
        private final int lineNo;
        private final JsonNode record;
        private final List<Problem> problems = new ArrayList<>();
        private String sourceKey;
        private Integer chunkNo;

        Row(long recordId, int lineNo, JsonNode record) {
            this.recordId = recordId;
            this.lineNo = lineNo;
            this.record = record;
        }

        public long recordId() {
            return recordId;
        }

        public int lineNo() {
            return lineNo;
        }

        public JsonNode record() {
            return record;
        }

        public List<Problem> problems() {
            return problems;
        }

        public String safeSourceKey() {
            return sourceKey != null && !sourceKey.isBlank() && sourceKey.length() <= 200 ? sourceKey : null;
        }

        public Integer chunkNo() {
            return chunkNo;
        }

        public boolean failed() {
            return problems.stream().anyMatch(problem -> "error".equals(problem.severity()));
        }

        public void addProblem(String code, String field, String severity, String message) {
            problems.add(new Problem(code, field, severity, message));
        }
    }

    public record Context(
        Long syllabusVersionId,
        Map<Integer, Long> subjectMappings,
        Map<String, Long> exactKnowledgePoints,
        Set<String> knowledgePointsInOtherVersions
    ) {
    }

    public Context context(
        Long syllabusVersionId,
        Map<Integer, Long> subjectMappings,
        List<TextbookKnowledgePointLookup> knowledgePoints
    ) {
        Map<String, Long> exact = new HashMap<>();
        Set<String> otherVersions = new HashSet<>();
        for (TextbookKnowledgePointLookup point : knowledgePoints) {
            String key = point.examSubjectId() + ":" + point.syllabusNumber();
            if (Objects.equals(point.syllabusVersionId(), syllabusVersionId)) {
                exact.put(key, point.id());
            } else {
                otherVersions.add(key);
            }
        }
        return new Context(syllabusVersionId, Map.copyOf(subjectMappings), Map.copyOf(exact), Set.copyOf(otherVersions));
    }

    public Context context(
        long syllabusVersionId,
        Map<Integer, Long> subjectMappings,
        List<TextbookKnowledgePointLookup> knowledgePoints
    ) {
        return context(Long.valueOf(syllabusVersionId), subjectMappings, knowledgePoints);
    }

    public Row validate(long recordId, int lineNo, JsonNode json, Context context) {
        Row row = new Row(recordId, lineNo, json);
        json.propertyNames().forEach(field -> {
            if (!FIELDS.contains(field)) {
                row.addProblem("UNKNOWN_FIELD_IGNORED", field, "warning", "未知字段已忽略");
            }
        });

        row.sourceKey = requiredText(json, "source_key", row);
        if (row.sourceKey != null && (row.sourceKey.isBlank() || row.sourceKey.length() > 200)) {
            error(row, "IMPORT_JSON_LINE_INVALID", "source_key", "source_key不能为空且长度不能超过200");
        }
        row.chunkNo = requiredPositiveInteger(json, "chunk_no", row);
        String heading = nullableText(json, "heading", row);
        if (heading != null && heading.length() > 500) {
            error(row, "IMPORT_JSON_LINE_INVALID", "heading", "heading长度不能超过500");
        }
        validateHeadingPath(json, row);
        String content = requiredText(json, "content", row);
        if (content != null && content.isEmpty()) {
            error(row, "IMPORT_JSON_LINE_INVALID", "content", "content不能为空");
        }
        Integer pageStart = optionalPositiveInteger(json, "page_start", row);
        Integer pageEnd = optionalPositiveInteger(json, "page_end", row);
        if (pageStart != null && pageEnd != null && pageStart > pageEnd) {
            error(row, "IMPORT_JSON_LINE_INVALID", "page_start", "page_start不能大于page_end");
        }
        if (!json.has("source_locator") || !json.get("source_locator").isObject()) {
            error(row, "IMPORT_JSON_LINE_INVALID", "source_locator", "source_locator必须是对象");
        }
        validateContentHash(json, content, row);
        validateKnowledgePoints(json, context, row);
        validateImages(json, row);
        return row;
    }

    private static void validateHeadingPath(JsonNode json, Row row) {
        JsonNode headings = json.get("heading_path");
        if (headings == null || !headings.isArray()) {
            error(row, "IMPORT_JSON_LINE_INVALID", "heading_path", "heading_path必须是数组");
            return;
        }
        for (int index = 0; index < headings.size(); index++) {
            if (!headings.get(index).isTextual()) {
                error(row, "IMPORT_JSON_LINE_INVALID", "heading_path[" + index + "]", "章节路径项必须是字符串");
            }
        }
    }

    private static void validateContentHash(JsonNode json, String content, Row row) {
        String hash = requiredText(json, "content_hash", row);
        if (hash == null) {
            return;
        }
        if (!SHA_256.matcher(hash).matches()) {
            error(row, "IMPORT_CONTENT_HASH_INVALID", "content_hash", "content_hash必须是64位小写SHA-256");
            return;
        }
        if (content != null && !hash.equals(sha256(content))) {
            error(row, "IMPORT_CONTENT_HASH_INVALID", "content_hash", "content_hash与正文不一致");
        }
    }

    private static void validateKnowledgePoints(JsonNode json, Context context, Row row) {
        JsonNode points = json.get("knowledge_points");
        if (points == null || points.isNull()) {
            row.addProblem("UNMAPPED_CHUNK", "knowledge_points", "warning", "内容块未关联知识点");
            return;
        }
        if (!points.isArray()) {
            error(row, "IMPORT_JSON_LINE_INVALID", "knowledge_points", "knowledge_points必须是数组");
            return;
        }
        if (context.syllabusVersionId() == null) {
            return;
        }
        if (points.isEmpty()) {
            row.addProblem("UNMAPPED_CHUNK", "knowledge_points", "warning", "内容块未关联知识点");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < points.size(); index++) {
            JsonNode point = points.get(index);
            String prefix = "knowledge_points[" + index + "]";
            if (!point.isObject()) {
                error(row, "IMPORT_JSON_LINE_INVALID", prefix, "知识点项必须是对象");
                continue;
            }
            Integer subjectNo = integral(point.get("subject_no"));
            String code = textual(point.get("code"));
            if (subjectNo == null || subjectNo <= 0) {
                error(row, "IMPORT_SUBJECT_MAPPING_MISSING", prefix + ".subject_no", "subject_no必须是正整数");
                continue;
            }
            Long subjectId = context.subjectMappings().get(subjectNo);
            if (subjectId == null) {
                error(row, "IMPORT_SUBJECT_MAPPING_MISSING", prefix + ".subject_no", "subject_no没有显式科目映射");
                continue;
            }
            if (code == null || code.isBlank() || code.length() > 100) {
                error(row, "KNOWLEDGE_POINT_NOT_FOUND", prefix + ".code", "知识点编号不能为空且不能超过100");
                continue;
            }
            String relationKey = subjectNo + ":" + code;
            if (!seen.add(relationKey)) {
                error(row, "IMPORT_JSON_LINE_INVALID", prefix, "同一内容块不能重复关联相同知识点");
                continue;
            }
            String lookupKey = subjectId + ":" + code;
            if (!context.exactKnowledgePoints().containsKey(lookupKey)) {
                if (context.knowledgePointsInOtherVersions().contains(lookupKey)) {
                    error(row, "KNOWLEDGE_POINT_VERSION_MISMATCH", prefix + ".code", "知识点不属于教材大纲版本");
                } else {
                    error(row, "KNOWLEDGE_POINT_NOT_FOUND", prefix + ".code", "当前大纲版本和科目下不存在该知识点");
                }
            }
        }
    }

    private static void validateImages(JsonNode json, Row row) {
        JsonNode images = json.get("images");
        if (images == null || images.isNull()) {
            return;
        }
        if (!images.isArray()) {
            error(row, "IMPORT_IMAGES_UNSUPPORTED", "images", "images必须为空数组");
        } else if (!images.isEmpty()) {
            error(row, "IMPORT_IMAGES_UNSUPPORTED", "images", "当前版本不支持教材图片导入");
        }
    }

    private static String requiredText(JsonNode json, String field, Row row) {
        JsonNode node = json.get(field);
        if (node == null || !node.isTextual()) {
            error(row, "IMPORT_JSON_LINE_INVALID", field, "缺少字符串字段" + field);
            return null;
        }
        return node.textValue();
    }

    private static String nullableText(JsonNode json, String field, Row row) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            error(row, "IMPORT_JSON_LINE_INVALID", field, field + "必须是字符串或null");
            return null;
        }
        return node.textValue();
    }

    private static Integer requiredPositiveInteger(JsonNode json, String field, Row row) {
        JsonNode node = json.get(field);
        Integer value = integral(node);
        if (value == null || value <= 0) {
            error(row, "IMPORT_JSON_LINE_INVALID", field, field + "必须是正整数");
            return null;
        }
        return value;
    }

    private static Integer optionalPositiveInteger(JsonNode json, String field, Row row) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        Integer value = integral(node);
        if (value == null || value <= 0) {
            error(row, "IMPORT_JSON_LINE_INVALID", field, field + "必须是正整数或null");
            return null;
        }
        return value;
    }

    private static Integer integral(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            return null;
        }
        return node.intValue();
    }

    private static String textual(JsonNode node) {
        return node != null && node.isTextual() ? node.textValue() : null;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void error(Row row, String code, String field, String message) {
        row.addProblem(code, field, "error", message);
    }
}
