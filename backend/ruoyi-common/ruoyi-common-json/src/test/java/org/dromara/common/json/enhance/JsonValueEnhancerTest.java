package org.dromara.common.json.enhance;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests JavaScript-safe integer handling in enhanced responses.
 */
class JsonValueEnhancerTest {

    @Test
    void preservesUnsafeLongAsStringWhenRenderingEnhancedResponse() {
        JsonFieldProcessor processor = new JsonFieldProcessor() {
            @Override
            public boolean supports(JsonFieldContext fieldContext) {
                return true;
            }
        };
        JsonValueEnhancer enhancer = new JsonValueEnhancer(JsonMapper.builder().build(), List.of(processor));

        JsonNode result = (JsonNode) enhancer.enhance(new IdResponse(1761100000000000001L, 1L));

        assertEquals("1761100000000000001", result.get("unsafeId").stringValue());
        assertEquals(1L, result.get("safeId").longValue());
    }

    private record IdResponse(Long unsafeId, Long safeId) {
    }
}
