package org.dromara.certmuse.catalog.validation;

import org.dromara.certmuse.catalog.domain.TextbookKnowledgePointLookup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/** Additional public-contract coverage for textbook JSONL validation boundaries. */
@Tag("dev")
class TextbookImportValidatorBranchCoverageTest {

    private static final String BODY_SHA_256 =
        "230d8358dc8e8890b4c58deeb62912ee2f20357ae92a5cc861b98e68fe31acb5";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final TextbookImportValidator validator = new TextbookImportValidator();

    @Test
    void preservesRecordMetadataAndRejectsOversizedTextFields() throws Exception {
        ObjectNode json = validRow();
        json.put("source_key", "s".repeat(201));
        json.put("heading", "h".repeat(501));

        TextbookImportValidator.Row row = validator.validate(701L, 19, json, exactContext());

        assertThat(row.recordId()).isEqualTo(701L);
        assertThat(row.lineNo()).isEqualTo(19);
        assertThat(row.record()).isSameAs(json);
        assertThat(row.safeSourceKey()).isNull();
        assertThat(row.chunkNo()).isEqualTo(1);
        assertThat(row.failed()).isTrue();
        assertThat(row.problems())
            .extracting(TextbookImportValidator.Problem::code, TextbookImportValidator.Problem::field,
                TextbookImportValidator.Problem::severity, TextbookImportValidator.Problem::message)
            .containsExactlyInAnyOrder(
                tuple("IMPORT_JSON_LINE_INVALID", "source_key", "error",
                    "source_key不能为空且长度不能超过200"),
                tuple("IMPORT_JSON_LINE_INVALID", "heading", "error", "heading长度不能超过500")
            );
    }

    @Test
    void reportsEveryMissingRequiredFieldWithoutRejectingAbsentOptionalFields() throws Exception {
        ObjectNode json = (ObjectNode) jsonMapper.readTree("{}");

        TextbookImportValidator.Row row = validator.validate(702L, 20, json, exactContext());

        assertThat(row.safeSourceKey()).isNull();
        assertThat(row.chunkNo()).isNull();
        assertThat(row.failed()).isTrue();
        assertThat(row.problems())
            .extracting(TextbookImportValidator.Problem::code, TextbookImportValidator.Problem::field,
                TextbookImportValidator.Problem::severity)
            .containsExactlyInAnyOrder(
                tuple("IMPORT_JSON_LINE_INVALID", "source_key", "error"),
                tuple("IMPORT_JSON_LINE_INVALID", "chunk_no", "error"),
                tuple("IMPORT_JSON_LINE_INVALID", "heading_path", "error"),
                tuple("IMPORT_JSON_LINE_INVALID", "content", "error"),
                tuple("IMPORT_JSON_LINE_INVALID", "source_locator", "error"),
                tuple("IMPORT_JSON_LINE_INVALID", "content_hash", "error"),
                tuple("UNMAPPED_CHUNK", "knowledge_points", "warning")
            );
    }

    @Test
    void acceptsExplicitNullOptionalFieldsAndTreatsNullKnowledgePointsAsUnmapped() throws Exception {
        ObjectNode json = validRow();
        json.putNull("heading");
        json.putNull("page_start");
        json.putNull("page_end");
        json.putNull("knowledge_points");
        json.putNull("images");

        TextbookImportValidator.Row row = validator.validate(703L, 21, json, exactContext());

        assertThat(row.failed()).isFalse();
        assertThat(row.problems())
            .containsExactly(new TextbookImportValidator.Problem(
                "UNMAPPED_CHUNK", "knowledge_points", "warning", "内容块未关联知识点"));
    }

    @Test
    void rejectsWrongContainerTypesAndReversedPageRangeButAllowsAnEmptyImageArray() throws Exception {
        ObjectNode json = validRow();
        json.put("heading_path", "Chapter 1");
        json.put("page_start", 3);
        json.put("page_end", 2);
        json.set("knowledge_points", jsonMapper.readTree("{}"));
        json.set("images", jsonMapper.readTree("[]"));

        TextbookImportValidator.Row row = validator.validate(704L, 22, json, exactContext());

        assertThat(row.problems())
            .extracting(TextbookImportValidator.Problem::code, TextbookImportValidator.Problem::field,
                TextbookImportValidator.Problem::message)
            .containsExactlyInAnyOrder(
                tuple("IMPORT_JSON_LINE_INVALID", "heading_path", "heading_path必须是数组"),
                tuple("IMPORT_JSON_LINE_INVALID", "page_start", "page_start不能大于page_end"),
                tuple("IMPORT_JSON_LINE_INVALID", "knowledge_points", "knowledge_points必须是数组")
            );
        assertThat(row.problems()).noneMatch(problem -> "IMPORT_IMAGES_UNSUPPORTED".equals(problem.code()));
    }

    @Test
    void doesNotReportAHashMismatchWhenRequiredContentIsMissing() throws Exception {
        ObjectNode json = validRow();
        json.remove("content");
        json.putNull("page_end");

        TextbookImportValidator.Row row = validator.validate(705L, 23, json, exactContext());

        assertThat(row.problems())
            .containsExactly(new TextbookImportValidator.Problem(
                "IMPORT_JSON_LINE_INVALID", "content", "error", "缺少字符串字段content"));
        assertThat(row.problems()).noneMatch(problem -> "IMPORT_CONTENT_HASH_INVALID".equals(problem.code()));
    }

    @Test
    void rejectsNonIntegralAndOverflowingPositiveIntegerFields() throws Exception {
        ObjectNode json = validRow();
        json.put("chunk_no", (long) Integer.MAX_VALUE + 1);
        json.put("page_start", 1.5D);
        json.put("page_end", Long.MAX_VALUE);
        json.set("knowledge_points", jsonMapper.readTree(
            "[{\"subject_no\":9223372036854775807,\"code\":\"1.1\"}]"));

        TextbookImportValidator.Row row = validator.validate(706L, 24, json, exactContext());

        assertThat(row.chunkNo()).isNull();
        assertThat(row.problems())
            .extracting(TextbookImportValidator.Problem::code, TextbookImportValidator.Problem::field)
            .containsExactlyInAnyOrder(
                tuple("IMPORT_JSON_LINE_INVALID", "chunk_no"),
                tuple("IMPORT_JSON_LINE_INVALID", "page_start"),
                tuple("IMPORT_JSON_LINE_INVALID", "page_end"),
                tuple("IMPORT_SUBJECT_MAPPING_MISSING", "knowledge_points[0].subject_no")
            );
    }

    @Test
    void rejectsMissingNonTextAndOversizedKnowledgePointCodes() throws Exception {
        ObjectNode json = validRow();
        String oversizedCode = "K".repeat(101);
        json.set("knowledge_points", jsonMapper.readTree("""
            [
              {"subject_no":1},
              {"subject_no":1,"code":7},
              {"subject_no":1,"code":%s},
              {"code":"1.1"}
            ]
            """.formatted(jsonMapper.writeValueAsString(oversizedCode))));

        TextbookImportValidator.Row row = validator.validate(707L, 25, json, exactContext());

        assertThat(row.problems())
            .extracting(TextbookImportValidator.Problem::code, TextbookImportValidator.Problem::field)
            .containsExactlyInAnyOrder(
                tuple("KNOWLEDGE_POINT_NOT_FOUND", "knowledge_points[0].code"),
                tuple("KNOWLEDGE_POINT_NOT_FOUND", "knowledge_points[1].code"),
                tuple("KNOWLEDGE_POINT_NOT_FOUND", "knowledge_points[2].code"),
                tuple("IMPORT_SUBJECT_MAPPING_MISSING", "knowledge_points[3].subject_no")
            );
    }

    @Test
    void reportsAWellFormedKnowledgePointThatDoesNotExistInAnyVersion() throws Exception {
        ObjectNode json = validRow();
        json.set("knowledge_points", jsonMapper.readTree(
            "[{\"subject_no\":1,\"code\":\"9.9\"}]"));

        TextbookImportValidator.Row row = validator.validate(708L, 26, json, exactContext());

        assertThat(row.problems())
            .containsExactly(new TextbookImportValidator.Problem(
                "KNOWLEDGE_POINT_NOT_FOUND", "knowledge_points[0].code", "error",
                "当前大纲版本和科目下不存在该知识点"));
    }

    @Test
    void qualificationScopedRowsMayCarryAnEmptyKnowledgeArrayWithoutAnUnmappedWarning() throws Exception {
        ObjectNode json = validRow();
        json.set("knowledge_points", jsonMapper.readTree("[]"));
        TextbookImportValidator.Context context = validator.context(null, Map.of(), List.of());

        TextbookImportValidator.Row row = validator.validate(709L, 27, json, context);

        assertThat(row.failed()).isFalse();
        assertThat(row.problems()).isEmpty();
    }

    private TextbookImportValidator.Context exactContext() {
        return validator.context(
            21L,
            Map.of(1, 11L),
            List.of(new TextbookKnowledgePointLookup(31L, 21L, 11L, "1.1"))
        );
    }

    private ObjectNode validRow() throws Exception {
        JsonNode json = jsonMapper.readTree("""
            {
              "source_key":"chunk-1",
              "chunk_no":1,
              "heading":"Chapter 1",
              "heading_path":["Chapter 1"],
              "content":"body",
              "page_start":1,
              "page_end":2,
              "source_locator":{},
              "content_hash":"%s",
              "knowledge_points":[{"subject_no":1,"code":"1.1"}]
            }
            """.formatted(BODY_SHA_256));
        return (ObjectNode) json;
    }
}
