package org.dromara.certmuse.catalog.validation;

import org.dromara.certmuse.catalog.support.ImportJsonSchema;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
public class KnowledgePointValidator {
    static final Set<String> REQUIRED_FIELDS = Set.of("schema_version", "source_key", "subject_no", "syllabus_number",
        "syllabus_title", "parent_syllabus_number", "tree_depth", "sort_order", "description",
        "diagnostic_enabled", "recommendation_enabled", "status");
    static final Set<String> FIELDS = Set.of("schema_version", "source_key", "subject_no", "syllabus_number",
        "syllabus_title", "parent_syllabus_number", "tree_depth", "sort_order", "description", "importance",
        "diagnostic_enabled", "recommendation_enabled", "status");
    private static final Pattern NUMBER = Pattern.compile("^[1-3](\\.[1-9][0-9]*)+$");

    public record Problem(String code, String field, String severity, String message) { }

    public static final class Row {
        private final long recordId;
        private final int lineNo;
        private final List<Problem> problems = new ArrayList<>();
        private String sourceKey;
        private Integer subjectNo;
        private String number;
        private String parent;
        private Integer depth;
        private Integer sort;
        private Integer importance;
        private boolean structuralFieldsValid;

        Row(long recordId, int lineNo) { this.recordId = recordId; this.lineNo = lineNo; }
        public long recordId() { return recordId; }
        public int lineNo() { return lineNo; }
        public List<Problem> problems() { return problems; }
        public boolean failed() { return problems.stream().anyMatch(problem -> "error".equals(problem.severity())); }
        public String safeSourceKey() { return validSourceKey() ? sourceKey : null; }
        public Integer importance() { return importance; }
        public String number() { return number; }
        public Integer subjectNo() { return subjectNo; }
        private boolean validSourceKey() {
            return sourceKey != null && subjectNo != null && number != null
                && sourceKey.equals("knowledge-tree-v7:" + subjectNo + ":" + number);
        }
    }

    public Row validateSchema(long recordId, int lineNo, JsonNode json, Set<Integer> mappedSubjects) {
        Row row = new Row(recordId, lineNo);
        for (String field : REQUIRED_FIELDS) if (!json.has(field)) add(row, "KP_REQUIRED_FIELD_MISSING", field, "缺少必填字段" + field);
        json.propertyNames().forEach(field -> { if (!FIELDS.contains(field)) add(row, "KP_UNKNOWN_FIELD", field, "出现未知字段" + field); });

        if (json.has("schema_version")) {
            if (!json.get("schema_version").isTextual()) type(row, "schema_version");
            else if (!ImportJsonSchema.KNOWLEDGE_POINT_INPUT.version().equals(json.get("schema_version").textValue()))
                add(row, "KP_SCHEMA_VERSION_UNSUPPORTED", "schema_version", "不支持的Schema版本");
        }
        row.subjectNo = integer(json, "subject_no", row);
        if (row.subjectNo != null) {
            if (row.subjectNo < 1 || row.subjectNo > 3) add(row, "KP_SUBJECT_NO_INVALID", "subject_no", "科目编号只允许1至3");
            else if (!mappedSubjects.contains(row.subjectNo)) add(row, "KP_SUBJECT_MAPPING_UNKNOWN", "subject_no", "科目编号没有显式映射");
        }

        row.number = requiredText(json, "syllabus_number", row);
        boolean numberValid = row.number != null && row.number.length() <= 100 && NUMBER.matcher(row.number).matches();
        if (row.number != null && row.number.length() > 100) value(row, "syllabus_number", "大纲编号长度不能超过100");
        else if (row.number != null && !numberValid) add(row, "KP_SYLLABUS_NUMBER_INVALID", "syllabus_number", "大纲编号格式不正确");
        else if (numberValid && row.subjectNo != null && row.subjectNo >= 1 && row.subjectNo <= 3 && !row.number.startsWith(row.subjectNo + "."))
            add(row, "KP_SUBJECT_NUMBER_MISMATCH", "syllabus_number", "编号首段与科目不一致");

        row.sourceKey = requiredText(json, "source_key", row);
        if (row.sourceKey != null && (row.sourceKey.length() > 200 || !row.validSourceKey())) add(row, "KP_SOURCE_KEY_INVALID", "source_key", "来源键格式不正确");
        String title = requiredText(json, "syllabus_title", row);
        if (title != null && (title.trim().isEmpty() || title.trim().length() > 500)) value(row, "syllabus_title", "知识点标题长度必须为1至500");

        row.parent = nullableText(json, "parent_syllabus_number", row);
        if (row.parent != null && row.parent.length() > 100)
            value(row, "parent_syllabus_number", "父节点编号长度不能超过100");
        else if (row.parent != null && !NUMBER.matcher(row.parent).matches())
            add(row, "KP_SYLLABUS_NUMBER_INVALID", "parent_syllabus_number", "父节点编号格式不正确");
        row.depth = integer(json, "tree_depth", row);
        if (row.depth != null && (row.depth < 1 || row.depth > 32767)) value(row, "tree_depth", "树层级必须为1至32767");
        row.sort = integer(json, "sort_order", row);
        if (row.sort != null && row.sort < 1) value(row, "sort_order", "同级排序必须从1开始");

        String description = nullableText(json, "description", row);
        if (description != null && description.isEmpty()) value(row, "description", "说明不能使用空字符串");
        if (json.has("importance") && !json.get("importance").isNull()) {
            row.importance = integer(json, "importance", row);
            if (row.importance != null && (row.importance < 1 || row.importance > 3)) {
                value(row, "importance", "叶子知识点重要度只能为1、2或3");
            }
        }
        fixedFalse(json, "diagnostic_enabled", row);
        fixedFalse(json, "recommendation_enabled", row);
        if (json.has("status")) {
            if (!json.get("status").isTextual()) type(row, "status");
            else if (!"0".equals(json.get("status").textValue())) value(row, "status", "状态必须为0");
        }

        row.structuralFieldsValid = row.subjectNo != null && row.subjectNo >= 1 && row.subjectNo <= 3
            && !has(row, "KP_SUBJECT_MAPPING_UNKNOWN") && numberValid
            && !has(row, "KP_SUBJECT_NUMBER_MISMATCH") && (row.parent == null || NUMBER.matcher(row.parent).matches())
            && row.depth != null && row.depth >= 1 && row.depth <= 32767;
        return row;
    }

    public void validateTree(List<Row> rows) {
        markAllDuplicates(rows, row -> row.sourceKey, "KP_DUPLICATE_SOURCE_KEY", "source_key", "来源键重复");
        markAllDuplicates(rows, row -> row.subjectNo == null || row.number == null ? null : row.subjectNo + ":" + row.number,
            "KP_DUPLICATE_SYLLABUS_NUMBER", "syllabus_number", "科目内大纲编号重复");
        Map<String, List<Row>> numbers = group(rows, row -> row.number);
        Map<String, Row> validParents = new HashMap<>();
        for (Row row : rows) if (row.structuralFieldsValid && !has(row, "KP_DUPLICATE_SYLLABUS_NUMBER"))
            validParents.put(row.subjectNo + ":" + row.number, row);

        for (Row row : rows) {
            if (!row.structuralFieldsValid) continue;
            if (row.parent == null) {
                if (row.depth != 1) add(row, "KP_TREE_DEPTH_INVALID", "tree_depth", "根节点层级必须为1");
                continue;
            }
            Row parent = validParents.get(row.subjectNo + ":" + row.parent);
            if (parent == null) {
                List<Row> candidates = numbers.getOrDefault(row.parent, List.of());
                if (candidates.stream().anyMatch(candidate -> !Objects.equals(candidate.subjectNo, row.subjectNo)))
                    add(row, "KP_PARENT_SUBJECT_MISMATCH", "parent_syllabus_number", "父节点属于其他科目");
                else if (!candidates.isEmpty()) add(row, "KP_PARENT_INVALID", "parent_syllabus_number", "父节点结构无效");
                else add(row, "KP_PARENT_NOT_FOUND", "parent_syllabus_number", "父节点不存在");
                continue;
            }
            if (parent.lineNo >= row.lineNo) add(row, "KP_PARENT_ORDER_INVALID", "parent_syllabus_number", "父节点必须早于子节点");
            if (hasCycle(row, validParents)) add(row, "KP_TREE_CYCLE", "parent_syllabus_number", "该行形成循环引用");
            else if (row.depth != parent.depth + 1) add(row, "KP_TREE_DEPTH_INVALID", "tree_depth", "层级与父节点不一致");
        }

        Map<String, List<Row>> siblings = group(rows, row -> row.subjectNo == null || row.sort == null || row.sort < 1 ? null : row.subjectNo + ":" + row.parent);
        for (List<Row> siblingGroup : siblings.values()) {
            Set<Integer> values = new HashSet<>();
            boolean invalid = siblingGroup.stream().anyMatch(row -> !values.add(row.sort));
            for (int expected = 1; !invalid && expected <= siblingGroup.size(); expected++) invalid = !values.contains(expected);
            if (invalid) siblingGroup.forEach(row -> add(row, "KP_SORT_ORDER_INVALID", "sort_order", "同级排序重复或不连续"));
        }

        Set<String> nonLeafKeys = new HashSet<>();
        for (Row row : rows) {
            if (row.structuralFieldsValid && row.parent != null) {
                nonLeafKeys.add(row.subjectNo + ":" + row.parent);
            }
        }
        for (Row row : rows) {
            if (!row.structuralFieldsValid || row.subjectNo == null || row.number == null) continue;
            if (!nonLeafKeys.contains(row.subjectNo + ":" + row.number)
                && (row.importance == null || row.importance < 1 || row.importance > 3)) {
                value(row, "importance", "叶子知识点必须配置重要度1、2或3");
            }
        }
    }

    private static boolean hasCycle(Row start, Map<String, Row> parents) {
        Set<Row> seen = new HashSet<>(); Row current = start;
        while (current != null && current.parent != null) {
            if (!seen.add(current)) return true;
            current = parents.get(current.subjectNo + ":" + current.parent);
        }
        return false;
    }
    private static void markAllDuplicates(List<Row> rows, Function<Row, String> keyFunction, String code, String field, String message) {
        group(rows, keyFunction).values().stream().filter(values -> values.size() > 1).flatMap(Collection::stream)
            .forEach(row -> add(row, code, field, message));
    }
    private static Map<String, List<Row>> group(List<Row> rows, Function<Row, String> keyFunction) {
        Map<String, List<Row>> result = new HashMap<>();
        for (Row row : rows) { String key = keyFunction.apply(row); if (key != null) result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row); }
        return result;
    }
    private static String requiredText(JsonNode json, String field, Row row) {
        if (!json.has(field)) return null;
        if (!json.get(field).isTextual()) { type(row, field); return null; }
        return json.get(field).textValue();
    }
    private static String nullableText(JsonNode json, String field, Row row) {
        if (!json.has(field) || json.get(field).isNull()) return null;
        if (!json.get(field).isTextual()) { type(row, field); return null; }
        return json.get(field).textValue();
    }
    private static Integer integer(JsonNode json, String field, Row row) {
        if (!json.has(field)) return null;
        if (!json.get(field).isIntegralNumber()) { type(row, field); return null; }
        if (!json.get(field).canConvertToInt()) { value(row, field, "整数超出32位有符号整数范围"); return null; }
        return json.get(field).intValue();
    }
    private static void fixedFalse(JsonNode json, String field, Row row) {
        if (!json.has(field)) return;
        if (!json.get(field).isBoolean()) type(row, field);
        else if (json.get(field).booleanValue()) value(row, field, "开关必须为false");
    }
    private static boolean has(Row row, String code) { return row.problems.stream().anyMatch(problem -> code.equals(problem.code())); }
    private static void add(Row row, String code, String field, String message) {
        if (row.problems.stream().noneMatch(problem -> code.equals(problem.code()) && Objects.equals(field, problem.field())))
            row.problems.add(new Problem(code, field, "error", message));
    }
    private static void type(Row row, String field) { add(row, "KP_FIELD_TYPE_INVALID", field, "字段类型不正确"); }
    private static void value(Row row, String field, String message) { add(row, "KP_FIELD_VALUE_INVALID", field, message); }
}
