package org.dromara.certmuse.catalog.validation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class KnowledgePointValidatorBranchCoverageTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final KnowledgePointValidator validator = new KnowledgePointValidator();

    @Test
    void exposesRowIdentityFailureStateAndValidatedSourceKey() {
        var valid = validate(41L, 7, validNode(1, "1.1", null, 1, 1), Set.of(1));

        assertThat(valid.recordId()).isEqualTo(41L);
        assertThat(valid.lineNo()).isEqualTo(7);
        assertThat(valid.failed()).isFalse();
        assertThat(valid.problems()).isEmpty();
        assertThat(valid.safeSourceKey()).isEqualTo("knowledge-tree-v7:1:1.1");

        ObjectNode invalidJson = validNode(1, "1.1", null, 1, 1);
        invalidJson.put("source_key", "wrong-key");
        var invalid = validate(42L, 8, invalidJson, Set.of(1));

        assertThat(invalid.failed()).isTrue();
        assertThat(invalid.safeSourceKey()).isNull();
        assertThat(problems(invalid)).contains("KP_SOURCE_KEY_INVALID:source_key");
    }

    @Test
    void reportsAllMissingFieldsAndUnknownFieldsWithoutInventingValues() {
        ObjectNode json = (ObjectNode) mapper.readTree("{\"future\":true}");

        var row = validate(1L, 1, json, Set.of());

        assertThat(row.problems()).hasSize(KnowledgePointValidator.REQUIRED_FIELDS.size() + 1);
        assertThat(problems(row)).contains(
            "KP_REQUIRED_FIELD_MISSING:schema_version",
            "KP_REQUIRED_FIELD_MISSING:source_key",
            "KP_REQUIRED_FIELD_MISSING:subject_no",
            "KP_REQUIRED_FIELD_MISSING:status",
            "KP_UNKNOWN_FIELD:future"
        ).doesNotContain("KP_REQUIRED_FIELD_MISSING:importance");
        assertThat(row.safeSourceKey()).isNull();
    }

    @Test
    void distinguishesSchemaVersionTypeFromUnsupportedValue() {
        ObjectNode wrongType = validNode(1, "1.1", null, 1, 1);
        wrongType.put("schema_version", 1);
        ObjectNode unsupported = validNode(1, "1.1", null, 1, 1);
        unsupported.put("schema_version", "2.0");

        assertThat(problems(validate(wrongType))).contains("KP_FIELD_TYPE_INVALID:schema_version");
        assertThat(problems(validate(unsupported))).contains("KP_SCHEMA_VERSION_UNSUPPORTED:schema_version");
    }

    @Test
    void validatesSubjectRangeMappingAndNumberOwnershipIndependently() {
        ObjectNode belowRange = validNode(1, "1.1", null, 1, 1);
        belowRange.put("subject_no", 0);
        belowRange.put("source_key", "knowledge-tree-v7:0:1.1");
        ObjectNode aboveRange = validNode(1, "1.1", null, 1, 1);
        aboveRange.put("subject_no", 4);
        aboveRange.put("source_key", "knowledge-tree-v7:4:1.1");
        ObjectNode unmapped = validNode(2, "2.1", null, 1, 1);
        ObjectNode mismatch = validNode(2, "1.1", null, 1, 1);

        assertThat(problems(validate(belowRange))).contains("KP_SUBJECT_NO_INVALID:subject_no");
        assertThat(problems(validate(aboveRange))).contains("KP_SUBJECT_NO_INVALID:subject_no");
        assertThat(problems(validate(3L, 3, unmapped, Set.of(1)))).contains("KP_SUBJECT_MAPPING_UNKNOWN:subject_no");
        assertThat(problems(validate(4L, 4, mismatch, Set.of(1, 2)))).contains(
            "KP_SUBJECT_NUMBER_MISMATCH:syllabus_number"
        );
    }

    @Test
    void distinguishesNumberTypeLengthAndPatternFailures() {
        ObjectNode wrongType = validNode(1, "1.1", null, 1, 1);
        wrongType.put("syllabus_number", 11);
        ObjectNode overlong = validNode(1, "1." + "1".repeat(99), null, 1, 1);
        ObjectNode malformed = validNode(1, "1.0", null, 1, 1);

        assertThat(problems(validate(wrongType))).contains("KP_FIELD_TYPE_INVALID:syllabus_number");
        assertThat(problems(validate(overlong)))
            .contains("KP_FIELD_VALUE_INVALID:syllabus_number")
            .doesNotContain("KP_SYLLABUS_NUMBER_INVALID:syllabus_number");
        assertThat(problems(validate(malformed))).contains("KP_SYLLABUS_NUMBER_INVALID:syllabus_number");
    }

    @Test
    void validatesSourceKeyAndTitleBoundaries() {
        ObjectNode overlongKey = validNode(1, "1.1", null, 1, 1);
        overlongKey.put("source_key", "k".repeat(201));
        ObjectNode wrongTitleType = validNode(1, "1.1", null, 1, 1);
        wrongTitleType.put("syllabus_title", true);
        ObjectNode blankTitle = validNode(1, "1.1", null, 1, 1);
        blankTitle.put("syllabus_title", "   ");
        ObjectNode overlongTitle = validNode(1, "1.1", null, 1, 1);
        overlongTitle.put("syllabus_title", "题".repeat(501));

        assertThat(problems(validate(overlongKey))).contains("KP_SOURCE_KEY_INVALID:source_key");
        assertThat(problems(validate(wrongTitleType))).contains("KP_FIELD_TYPE_INVALID:syllabus_title");
        assertThat(problems(validate(blankTitle))).contains("KP_FIELD_VALUE_INVALID:syllabus_title");
        assertThat(problems(validate(overlongTitle))).contains("KP_FIELD_VALUE_INVALID:syllabus_title");
    }

    @Test
    void validatesNullableParentTypeLengthAndPattern() {
        ObjectNode wrongType = validNode(1, "1.1.1", null, 2, 1);
        wrongType.put("parent_syllabus_number", 11);
        ObjectNode overlong = validNode(1, "1.1.1", null, 2, 1);
        overlong.put("parent_syllabus_number", "1." + "1".repeat(99));
        ObjectNode malformed = validNode(1, "1.1.1", null, 2, 1);
        malformed.put("parent_syllabus_number", "1.0");

        assertThat(problems(validate(wrongType))).contains("KP_FIELD_TYPE_INVALID:parent_syllabus_number");
        assertThat(problems(validate(overlong))).contains("KP_FIELD_VALUE_INVALID:parent_syllabus_number");
        assertThat(problems(validate(malformed))).contains("KP_SYLLABUS_NUMBER_INVALID:parent_syllabus_number");
    }

    @Test
    void distinguishesIntegerTypesOverflowAndRangeFailures() {
        ObjectNode nonIntegral = validNode(1, "1.1", null, 1, 1);
        nonIntegral.put("tree_depth", 1.5);
        ObjectNode overflow = validNode(1, "1.1", null, 1, 1);
        overflow.put("sort_order", 2_147_483_648L);
        ObjectNode shallow = validNode(1, "1.1", null, 0, 1);
        ObjectNode deep = validNode(1, "1.1", null, 32_768, 1);
        ObjectNode zeroSort = validNode(1, "1.1", null, 1, 0);

        assertThat(problems(validate(nonIntegral))).contains("KP_FIELD_TYPE_INVALID:tree_depth");
        assertThat(problems(validate(overflow)))
            .contains("KP_FIELD_VALUE_INVALID:sort_order")
            .doesNotContain("KP_FIELD_TYPE_INVALID:sort_order");
        assertThat(problems(validate(shallow))).contains("KP_FIELD_VALUE_INVALID:tree_depth");
        assertThat(problems(validate(deep))).contains("KP_FIELD_VALUE_INVALID:tree_depth");
        assertThat(problems(validate(zeroSort))).contains("KP_FIELD_VALUE_INVALID:sort_order");
    }

    @Test
    void validatesOptionalTextNullOnlyValueFlagsAndStatus() {
        ObjectNode json = validNode(1, "1.1", null, 1, 1);
        json.put("description", "");
        json.put("importance", 3);
        json.put("diagnostic_enabled", "false");
        json.put("recommendation_enabled", true);
        json.put("status", 0);

        assertThat(problems(validate(json))).contains(
            "KP_FIELD_VALUE_INVALID:description",
            "KP_FIELD_TYPE_INVALID:diagnostic_enabled",
            "KP_FIELD_VALUE_INVALID:recommendation_enabled",
            "KP_FIELD_TYPE_INVALID:status"
        );

        ObjectNode wrongStatus = validNode(1, "1.1", null, 1, 1);
        wrongStatus.put("status", "1");
        assertThat(problems(validate(wrongStatus))).contains("KP_FIELD_VALUE_INVALID:status");
    }

    @Test
    void acceptsDocumentedMaximumsAndNonEmptyOptionalText() {
        String number = "1." + "1".repeat(98);
        ObjectNode json = validNode(1, number, null, 32_767, 1);
        json.put("syllabus_title", "题".repeat(500));
        json.put("description", "说明");

        var row = validate(json);

        assertThat(row.failed()).isFalse();
        assertThat(row.safeSourceKey()).isEqualTo("knowledge-tree-v7:1:" + number);
    }

    @Test
    void rejectsNonRootDepthAndSkipsStructurallyInvalidRows() {
        var wrongRootDepth = validate(1L, 1, validNode(1, "1.1", null, 2, 1), Set.of(1));
        ObjectNode invalidJson = validNode(1, "1.2", null, 0, 1);
        var structurallyInvalid = validate(2L, 2, invalidJson, Set.of(1));

        validator.validateTree(List.of(wrongRootDepth, structurallyInvalid));

        assertThat(problems(wrongRootDepth)).contains("KP_TREE_DEPTH_INVALID:tree_depth");
        assertThat(problems(structurallyInvalid))
            .contains("KP_FIELD_VALUE_INVALID:tree_depth")
            .doesNotContain("KP_TREE_DEPTH_INVALID:tree_depth");
    }

    @Test
    void distinguishesCrossSubjectInvalidAndMissingParents() {
        var otherSubjectParent = validate(1L, 1, validNode(2, "2.1", null, 1, 1), Set.of(1, 2));
        var crossSubjectChild = validate(2L, 2, validNode(1, "1.1", "2.1", 2, 1), Set.of(1, 2));
        var invalidParent = validate(3L, 3, validNode(1, "1.2", null, 0, 2), Set.of(1, 2));
        var invalidParentChild = validate(4L, 4, validNode(1, "1.2.1", "1.2", 2, 1), Set.of(1, 2));
        var missingParentChild = validate(5L, 5, validNode(1, "1.3.1", "1.3", 2, 1), Set.of(1, 2));

        validator.validateTree(List.of(
            otherSubjectParent,
            crossSubjectChild,
            invalidParent,
            invalidParentChild,
            missingParentChild
        ));

        assertThat(problems(crossSubjectChild)).contains("KP_PARENT_SUBJECT_MISMATCH:parent_syllabus_number");
        assertThat(problems(invalidParentChild)).contains("KP_PARENT_INVALID:parent_syllabus_number");
        assertThat(problems(missingParentChild)).contains("KP_PARENT_NOT_FOUND:parent_syllabus_number");
    }

    @Test
    void validatesParentOrderAndChildDepthAgainstResolvedParent() {
        var childBeforeParent = validate(1L, 1, validNode(1, "1.1.1", "1.1", 3, 1), Set.of(1));
        var parentAfterChild = validate(2L, 2, validNode(1, "1.1", null, 1, 1), Set.of(1));

        validator.validateTree(List.of(childBeforeParent, parentAfterChild));

        assertThat(problems(childBeforeParent)).contains(
            "KP_PARENT_ORDER_INVALID:parent_syllabus_number",
            "KP_TREE_DEPTH_INVALID:tree_depth"
        );
    }

    @Test
    void detectsCyclesThroughThePublicTreeValidationSeam() {
        var first = validate(1L, 1, validNode(1, "1.1", "1.2", 2, 1), Set.of(1));
        var second = validate(2L, 2, validNode(1, "1.2", "1.1", 2, 1), Set.of(1));

        validator.validateTree(List.of(first, second));

        assertThat(problems(first)).contains("KP_TREE_CYCLE:parent_syllabus_number");
        assertThat(problems(second)).contains("KP_TREE_CYCLE:parent_syllabus_number");
    }

    @Test
    void marksDuplicateKeysAndNumbersButIgnoresAbsentDuplicateKeys() {
        var first = validate(1L, 1, validNode(1, "1.1", null, 1, 1), Set.of(1));
        var second = validate(2L, 2, validNode(1, "1.1", null, 1, 2), Set.of(1));
        var missingFirst = validate(3L, 3, (ObjectNode) mapper.readTree("{}"), Set.of(1));
        var missingSecond = validate(4L, 4, (ObjectNode) mapper.readTree("{}"), Set.of(1));

        validator.validateTree(List.of(first, second, missingFirst, missingSecond));

        assertThat(problems(first)).contains(
            "KP_DUPLICATE_SOURCE_KEY:source_key",
            "KP_DUPLICATE_SYLLABUS_NUMBER:syllabus_number"
        );
        assertThat(problems(second)).contains(
            "KP_DUPLICATE_SOURCE_KEY:source_key",
            "KP_DUPLICATE_SYLLABUS_NUMBER:syllabus_number"
        );
        assertThat(problems(missingFirst)).noneMatch(problem -> problem.startsWith("KP_DUPLICATE_"));
        assertThat(problems(missingSecond)).noneMatch(problem -> problem.startsWith("KP_DUPLICATE_"));
    }

    @Test
    void validatesSiblingSortDuplicatesAndGapsWhileAcceptingASequence() {
        var duplicateFirst = validate(1L, 1, validNode(1, "1.1", null, 1, 1), Set.of(1));
        var duplicateSecond = validate(2L, 2, validNode(1, "1.2", null, 1, 1), Set.of(1));
        validator.validateTree(List.of(duplicateFirst, duplicateSecond));
        assertThat(problems(duplicateFirst)).contains("KP_SORT_ORDER_INVALID:sort_order");
        assertThat(problems(duplicateSecond)).contains("KP_SORT_ORDER_INVALID:sort_order");

        var gapFirst = validate(3L, 3, validNode(1, "1.3", null, 1, 1), Set.of(1));
        var gapSecond = validate(4L, 4, validNode(1, "1.4", null, 1, 3), Set.of(1));
        validator.validateTree(List.of(gapFirst, gapSecond));
        assertThat(problems(gapFirst)).contains("KP_SORT_ORDER_INVALID:sort_order");
        assertThat(problems(gapSecond)).contains("KP_SORT_ORDER_INVALID:sort_order");

        var orderedFirst = validate(5L, 5, validNode(1, "1.5", null, 1, 1), Set.of(1));
        var orderedSecond = validate(6L, 6, validNode(1, "1.6", null, 1, 2), Set.of(1));
        validator.validateTree(List.of(orderedFirst, orderedSecond));
        assertThat(problems(orderedFirst)).doesNotContain("KP_SORT_ORDER_INVALID:sort_order");
        assertThat(problems(orderedSecond)).doesNotContain("KP_SORT_ORDER_INVALID:sort_order");

        validator.validateTree(List.of());
    }

    private KnowledgePointValidator.Row validate(ObjectNode json) {
        return validate(1L, 1, json, Set.of(1));
    }

    private KnowledgePointValidator.Row validate(long id, int line, ObjectNode json, Set<Integer> subjects) {
        return validator.validateSchema(id, line, json, subjects);
    }

    private ObjectNode validNode(int subject, String number, String parent, int depth, int sort) {
        ObjectNode json = (ObjectNode) mapper.readTree("{} ");
        json.put("schema_version", "1.0");
        json.put("source_key", "knowledge-tree-v7:" + subject + ":" + number);
        json.put("subject_no", subject);
        json.put("syllabus_number", number);
        json.put("syllabus_title", "标题");
        if (parent == null) json.putNull("parent_syllabus_number");
        else json.put("parent_syllabus_number", parent);
        json.put("tree_depth", depth);
        json.put("sort_order", sort);
        json.putNull("description");
        json.putNull("importance");
        json.put("diagnostic_enabled", false);
        json.put("recommendation_enabled", false);
        json.put("status", "0");
        return json;
    }

    private static List<String> problems(KnowledgePointValidator.Row row) {
        return row.problems().stream()
            .map(problem -> problem.code() + ":" + problem.field())
            .toList();
    }
}
