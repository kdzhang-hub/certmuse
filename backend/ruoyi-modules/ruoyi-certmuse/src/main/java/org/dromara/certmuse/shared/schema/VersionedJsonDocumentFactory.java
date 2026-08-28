package org.dromara.certmuse.shared.schema;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds JSON documents whose schema version field must have a stable name and position.
 */
@Component
public class VersionedJsonDocumentFactory {

    public static final String SCHEMA_VERSION_FIELD = "schema_version";
    public static final String IDEMPOTENCY_SCHEMA_VERSION_FIELD = "schemaVersion";

    private final JsonMapper defaultMapper;

    public VersionedJsonDocumentFactory(@Qualifier("importStrictJsonMapper") JsonMapper defaultMapper) {
        this.defaultMapper = defaultMapper;
    }

    public static Map<String, Object> flatFields(JsonSchemaVersion schema, Map<String, ?> fields) {
        return flatFields(SCHEMA_VERSION_FIELD, schema, fields);
    }

    public static Map<String, Object> flatFields(
        String versionField,
        JsonSchemaVersion schema,
        Map<String, ?> fields
    ) {
        if (fields.containsKey(versionField)) {
            throw new IllegalArgumentException(versionField + " is managed by VersionedJsonDocumentFactory");
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(versionField, schema.version());
        document.putAll(fields);
        return document;
    }

    public static String json(JsonMapper mapper, JsonSchemaVersion schema, Map<String, ?> fields) {
        return json(mapper, SCHEMA_VERSION_FIELD, schema, fields);
    }

    public static String json(JsonSchemaVersion schema, Map<String, ?> fields) {
        return json(JsonMapper.builder().build(), schema, fields);
    }

    public static String json(
        JsonMapper mapper,
        String versionField,
        JsonSchemaVersion schema,
        Map<String, ?> fields
    ) {
        Map<String, Object> document = flatFields(versionField, schema, fields);
        try {
            return mapper.writeValueAsString(document);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize versioned JSON document", exception);
        }
    }

    public static String envelope(JsonMapper mapper, JsonSchemaVersion schema, Object payload) {
        return json(mapper, schema, Map.of("payload", payload));
    }

    public String flat(JsonSchemaVersion schema, Map<String, ?> fields) {
        return json(defaultMapper, schema, fields);
    }

    public String envelope(JsonSchemaVersion schema, Object payload) {
        return envelope(defaultMapper, schema, payload);
    }
}
