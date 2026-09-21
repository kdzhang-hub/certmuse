package org.dromara.certmuse.catalog.validation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class KnowledgePointValidatorTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final KnowledgePointValidator validator = new KnowledgePointValidator();

    @Test
    void acceptsValidTree() {
        var root = row(1, 1, json("knowledge-tree-v7:1:1.1", 1, "1.1", null, 1, 1));
        var child = row(2, 2, json("knowledge-tree-v7:1:1.1.1", 1, "1.1.1", "1.1", 2, 1, 3));

        validator.validateTree(List.of(root, child));

        assertThat(root.failed()).isFalse();
        assertThat(child.failed()).isFalse();
    }

    @Test
    void reportsUnknownMappingUnknownFieldAndInvalidTypes() {
        JsonNode json = mapper.readTree(
            json("key", 2, "2.1", null, 1, 1).replace("}", ",\"extra\":1,\"tree_depth\":\"1\"}")
        );

        var row = validator.validateSchema(1, 1, json, Set.of(1));

        assertThat(codes(row)).contains(
            "KP_SUBJECT_MAPPING_UNKNOWN",
            "KP_UNKNOWN_FIELD",
            "KP_FIELD_TYPE_INVALID"
        );
    }

    @Test
    void reportsDuplicatesMissingParentAndSortGap() {
        var first = row(1, 1, json("same", 1, "1.1", null, 1, 1));
        var second = row(2, 2, json("same", 1, "1.1", null, 1, 3));
        var child = row(3, 3, json("c", 1, "1.2.1", "1.2", 2, 1));

        validator.validateTree(List.of(first, second, child));

        assertThat(codes(first)).contains(
            "KP_DUPLICATE_SOURCE_KEY",
            "KP_DUPLICATE_SYLLABUS_NUMBER",
            "KP_SORT_ORDER_INVALID"
        );
        assertThat(codes(second)).contains(
            "KP_DUPLICATE_SOURCE_KEY",
            "KP_DUPLICATE_SYLLABUS_NUMBER",
            "KP_SORT_ORDER_INVALID"
        );
        assertThat(codes(child)).contains("KP_PARENT_NOT_FOUND");
    }

    @Test
    void reportsCycleAndDepthMismatch() {
        var first = row(1, 1, json("a", 1, "1.1", "1.2", 2, 1));
        var second = row(2, 2, json("b", 1, "1.2", "1.1", 5, 1));

        validator.validateTree(List.of(first, second));

        assertThat(codes(first)).contains("KP_TREE_CYCLE");
        assertThat(codes(second)).contains("KP_TREE_CYCLE");
    }

    @Test
    void validatesSourceKeyParentOrderAndDepthRange() {
        var child = row(1, 1, json("bad", 1, "1.1.1", "1.1", 2, 1));
        var parent = row(2, 2, json("knowledge-tree-v7:1:1.1", 1, "1.1", null, 1, 1));
        validator.validateTree(List.of(child, parent));
        assertThat(codes(child)).contains("KP_SOURCE_KEY_INVALID", "KP_PARENT_ORDER_INVALID");

        var tooDeep = row(3, 3, json("knowledge-tree-v7:1:1.2", 1, "1.2", null, 32768, 2));
        assertThat(codes(tooDeep)).contains("KP_FIELD_VALUE_INVALID");
    }

    @Test
    void enforcesLeafImportanceAndFixedDefaultValues() {
        String invalid = json("knowledge-tree-v7:1:1.1", 1, "1.1", null, 1, 1)
            .replace("\"diagnostic_enabled\":false", "\"diagnostic_enabled\":true")
            .replace("\"status\":\"0\"", "\"status\":\"1\"");

        assertThat(codes(row(1, 1, invalid))).contains("KP_FIELD_VALUE_INVALID");
    }

    @Test
    void requiresImportanceForLeafNodesButAllowsItForNonLeafNodes() {
        var root = row(1, 1, json("knowledge-tree-v7:1:1.1", 1, "1.1", null, 1, 1, 2));
        var leaf = row(2, 2, json("knowledge-tree-v7:1:1.1.1", 1, "1.1.1", "1.1", 2, 1));

        validator.validateTree(List.of(root, leaf));

        assertThat(codes(root)).doesNotContain("KP_FIELD_VALUE_INVALID");
        assertThat(codes(leaf)).contains("KP_FIELD_VALUE_INVALID");
    }

    @Test
    void allowsImportanceToBeOmittedForNonLeafNodes() {
        String rootJson = json("knowledge-tree-v7:1:1.1", 1, "1.1", null, 1, 1)
            .replace(",\"importance\":null", "");
        var root = row(1, 1, rootJson);
        var leaf = row(2, 2, json("knowledge-tree-v7:1:1.1.1", 1, "1.1.1", "1.1", 2, 1, 3));

        validator.validateTree(List.of(root, leaf));

        assertThat(root.failed()).isFalse();
        assertThat(leaf.failed()).isFalse();
    }

    @Test
    void rejectsInvalidImportanceWhenItIsPresentOnNonLeafNodes() {
        var root = row(1, 1, json("knowledge-tree-v7:1:1.1", 1, "1.1", null, 1, 1, 4));
        var leaf = row(2, 2, json("knowledge-tree-v7:1:1.1.1", 1, "1.1.1", "1.1", 2, 1, 3));

        validator.validateTree(List.of(root, leaf));

        assertThat(codes(root)).contains("KP_FIELD_VALUE_INVALID");
        assertThat(leaf.failed()).isFalse();
    }

    private KnowledgePointValidator.Row row(long id, int line, String json) {
        return validator.validateSchema(id, line, mapper.readTree(json), Set.of(1));
    }

    private static Set<String> codes(KnowledgePointValidator.Row row) {
        Set<String> codes = new HashSet<>();
        row.problems().forEach(problem -> codes.add(problem.code()));
        return codes;
    }

    private static String json(
        String key,
        int subject,
        String number,
        String parent,
        int depth,
        int sort
    ) {
        return json(key, subject, number, parent, depth, sort, null);
    }

    private static String json(
        String key,
        int subject,
        String number,
        String parent,
        int depth,
        int sort,
        Integer importance
    ) {
        String parentValue = parent == null ? "null" : "\"" + parent + "\"";
        String importanceValue = importance == null ? "null" : importance.toString();
        return "{\"schema_version\":\"1.0\",\"source_key\":\"" + key
            + "\",\"subject_no\":" + subject
            + ",\"syllabus_number\":\"" + number
            + "\",\"syllabus_title\":\"标题\",\"parent_syllabus_number\":" + parentValue
            + ",\"tree_depth\":" + depth
            + ",\"sort_order\":" + sort
            + ",\"description\":null,\"importance\":" + importanceValue + ",\"diagnostic_enabled\":false,"
            + "\"recommendation_enabled\":false,\"status\":\"0\"}";
    }
    @Test
    void distinguishesWrongTypesFromOutOfRangeAndOverlongValues() {
        String overlongNumber = "1." + "1".repeat(99);
        var number = row(1, 1, json("knowledge-tree-v7:1:" + overlongNumber, 1, overlongNumber, null, 1, 1));
        assertThat(codes(number)).contains("KP_FIELD_VALUE_INVALID").doesNotContain("KP_SYLLABUS_NUMBER_INVALID");

        String overflow = json("knowledge-tree-v7:1:1.1", 1, "1.1", null, 1, 1)
            .replace("\"sort_order\":1", "\"sort_order\":2147483648");
        assertThat(codes(row(2, 2, overflow))).contains("KP_FIELD_VALUE_INVALID").doesNotContain("KP_FIELD_TYPE_INVALID");

        String wrongType = json("knowledge-tree-v7:1:1.1", 1, "1.1", null, 1, 1)
            .replace("\"sort_order\":1", "\"sort_order\":\"1\"");
        assertThat(codes(row(3, 3, wrongType))).contains("KP_FIELD_TYPE_INVALID");
    }
}
