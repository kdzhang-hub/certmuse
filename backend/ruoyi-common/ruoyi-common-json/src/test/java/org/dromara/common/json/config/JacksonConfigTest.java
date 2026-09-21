package org.dromara.common.json.config;

import org.junit.jupiter.api.Test;
import org.dromara.common.json.handler.BigNumberSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the JavaScript-safe integer JSON contract.
 */
class JacksonConfigTest {

    private final JsonMapper mapper = configuredMapper();

    @Test
    void serializesUnsafeLongAsString() {
        assertEquals("\"1761100000000000001\"", mapper.writeValueAsString(1761100000000000001L));
    }

    @Test
    void keepsSafeLongAsNumber() {
        assertEquals("9007199254740991", mapper.writeValueAsString(9007199254740991L));
    }

    @Test
    void instantiatesBigNumberSerializerDeclaredByAnnotation() {
        assertEquals(
            "{\"id\":\"1761300000000000001\"}",
            mapper.writeValueAsString(new AnnotatedId(1761300000000000001L))
        );
    }

    private static JsonMapper configuredMapper() {
        JacksonConfig config = new JacksonConfig();
        JsonMapper.Builder builder = JsonMapper.builder();
        config.jsonInitCustomizer().customize(builder);
        return builder.build();
    }

    private record AnnotatedId(@JsonSerialize(using = BigNumberSerializer.class) Long id) {
    }
}
