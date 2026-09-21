package org.dromara.certmuse.catalog.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.dromara.certmuse.shared.schema.SharedJsonSchema;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ImportJsonDocumentFactoryTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final ImportJsonDocumentFactory documents = new ImportJsonDocumentFactory(objectMapper);

    @Test
    void writesFlatDocumentWithTheSelectedSchemaVersion() throws Exception {
        var document = objectMapper.readTree(documents.flat(
            ImportJsonSchema.QUESTION_IMPORT_RESULT,
            Map.of("qid", "q-1", "exam_subject_id", 11L)
        ));

        assertThat(document.path("schema_version").asText()).isEqualTo("question_import_result/1.0");
        assertThat(document.path("qid").asText()).isEqualTo("q-1");
        assertThat(document.path("exam_subject_id").asLong()).isEqualTo(11L);
    }

    @Test
    void wrapsPayloadWithTheSelectedSchemaVersion() throws Exception {
        var document = objectMapper.readTree(documents.envelope(
            ImportJsonSchema.KNOWLEDGE_POINT_RAW,
            Map.of("source_key", "1.1")
        ));

        assertThat(document.path("schema_version").asText()).isEqualTo("knowledge_point_raw/1.0");
        assertThat(document.path("payload").path("source_key").asText()).isEqualTo("1.1");
    }

    @Test
    void rejectsCallerSuppliedSchemaVersion() {
        assertThatThrownBy(() -> documents.flat(
            ImportJsonSchema.QUESTION_RAW,
            Map.of("schema_version", "overridden")
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("schema_version");
    }

    @Test
    void preservesExactFlatSerializationAndAcceptsSharedSchemas() {
        assertThat(documents.flat(
            SharedJsonSchema.QUESTION_ANSWER,
            Map.of("answer_type", "CHOICE")
        )).isEqualTo("{\"schema_version\":\"1.0\",\"answer_type\":\"CHOICE\"}");
    }
}
