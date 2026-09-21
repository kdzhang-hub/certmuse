package org.dromara.certmuse.catalog.validation;

import org.dromara.certmuse.catalog.domain.TextbookKnowledgePointLookup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TextbookImportValidatorTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final TextbookImportValidator validator = new TextbookImportValidator();

    @Test
    void acceptsValidRowAndPreservesDecodedContentHash() throws Exception {
        String content = "body\nline  ";
        JsonNode json = row(content, sha256(content), "[{\"subject_no\":1,\"code\":\"1.1\"}]");

        var validated = validator.validate(101L, 7, json, exactContext());

        assertThat(validated.failed()).isFalse();
        assertThat(validated.problems()).isEmpty();
        assertThat(validated.safeSourceKey()).isEqualTo("chunk-1");
        assertThat(validated.chunkNo()).isEqualTo(1);
    }

    @Test
    void reportsHashMismatchUnmappedChunkAndUnknownField() throws Exception {
        JsonNode invalidHash = row("body", "0".repeat(64), "[]");
        ((tools.jackson.databind.node.ObjectNode) invalidHash).put("future_field", true);

        var validated = validator.validate(101L, 1, invalidHash, exactContext());

        assertThat(codes(validated)).containsExactlyInAnyOrder(
            "IMPORT_CONTENT_HASH_INVALID", "UNMAPPED_CHUNK", "UNKNOWN_FIELD_IGNORED"
        );
        assertThat(validated.failed()).isTrue();
    }

    @Test
    void rejectsDuplicateRelationAndUnsupportedImages() throws Exception {
        JsonNode json = row(
            "body",
            sha256("body"),
            "[{\"subject_no\":1,\"code\":\"1.1\"},{\"subject_no\":1,\"code\":\"1.1\"}]"
        );
        ((tools.jackson.databind.node.ObjectNode) json).set(
            "images", mapper.readTree("[{\"path\":\"image.png\"}]")
        );

        var validated = validator.validate(101L, 1, json, exactContext());

        assertThat(codes(validated)).contains("IMPORT_JSON_LINE_INVALID", "IMPORT_IMAGES_UNSUPPORTED");
        assertThat(validated.failed()).isTrue();
    }

    @Test
    void distinguishesVersionMismatchAndRejectsEmptySourceKey() throws Exception {
        JsonNode json = row("body", sha256("body"), "[{\"subject_no\":1,\"code\":\"1.1\"}]");
        ((tools.jackson.databind.node.ObjectNode) json).put("source_key", "");
        var context = validator.context(
            21L,
            Map.of(1, 11L),
            List.of(new TextbookKnowledgePointLookup(31L, 22L, 11L, "1.1"))
        );

        var validated = validator.validate(101L, 1, json, context);

        assertThat(codes(validated)).contains(
            "KNOWLEDGE_POINT_VERSION_MISMATCH", "IMPORT_JSON_LINE_INVALID"
        );
        assertThat(validated.safeSourceKey()).isNull();
    }

    @Test
    void reportsStructuralErrorsAcrossScalarAndContainerFields() throws Exception {
        var json = (tools.jackson.databind.node.ObjectNode) row("body", sha256("body"), "[]");
        json.put("source_key", 123);
        json.put("chunk_no", 0);
        json.put("heading", 7);
        json.set("heading_path", mapper.readTree("[\"Chapter 1\", 2]"));
        json.put("content", "");
        json.put("page_start", -1);
        json.put("page_end", "last");
        json.put("source_locator", "not-an-object");
        json.put("content_hash", "ABC");
        json.set("knowledge_points", mapper.readTree("[1]"));
        json.set("images", mapper.readTree("{}"));

        var validated = validator.validate(101L, 1, json, exactContext());

        assertThat(codes(validated)).contains(
            "IMPORT_JSON_LINE_INVALID", "IMPORT_CONTENT_HASH_INVALID", "IMPORT_IMAGES_UNSUPPORTED"
        );
        assertThat(validated.failed()).isTrue();
        assertThat(validated.chunkNo()).isNull();
    }

    @Test
    void distinguishesMissingMappingsAndInvalidKnowledgePointShapes() throws Exception {
        JsonNode json = row("body", sha256("body"),
            "[{\"subject_no\":0,\"code\":\"1.1\"},"
                + "{\"subject_no\":2,\"code\":\"1.2\"},"
                + "{\"subject_no\":1,\"code\":\"\"}]");

        var validated = validator.validate(101L, 1, json, exactContext());

        assertThat(codes(validated)).contains(
            "IMPORT_SUBJECT_MAPPING_MISSING", "KNOWLEDGE_POINT_NOT_FOUND"
        );
        assertThat(validated.problems()).anyMatch(problem ->
            problem.field().equals("knowledge_points[0].subject_no"));
        assertThat(validated.failed()).isTrue();
    }

    @Test
    void qualificationScopedTextbookKeepsKnowledgePayloadWithoutMappingIt() throws Exception {
        JsonNode json = row("body", sha256("body"),
            "[{\"subject_no\":99,\"code\":\"not-yet-in-a-syllabus\"}]");
        var context = validator.context(null, Map.of(), List.of());

        var validated = validator.validate(101L, 1, json, context);

        assertThat(validated.failed()).isFalse();
        assertThat(codes(validated)).doesNotContain(
            "IMPORT_SUBJECT_MAPPING_MISSING", "KNOWLEDGE_POINT_NOT_FOUND",
            "KNOWLEDGE_POINT_VERSION_MISMATCH"
        );
    }

    private TextbookImportValidator.Context exactContext() {
        return validator.context(
            21L,
            Map.of(1, 11L),
            List.of(new TextbookKnowledgePointLookup(31L, 21L, 11L, "1.1"))
        );
    }

    private JsonNode row(String content, String hash, String knowledgePoints) throws Exception {
        return mapper.readTree("{"
            + "\"source_key\":\"chunk-1\",\"chunk_no\":1,\"heading\":null,"
            + "\"heading_path\":[\"Chapter 1\"],\"content\":" + mapper.writeValueAsString(content) + ","
            + "\"page_start\":1,\"page_end\":2,\"source_locator\":{},"
            + "\"content_hash\":\"" + hash + "\",\"knowledge_points\":" + knowledgePoints + "}"
        );
    }

    private static Set<String> codes(TextbookImportValidator.Row row) {
        return row.problems().stream().map(TextbookImportValidator.Problem::code).collect(Collectors.toSet());
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}
