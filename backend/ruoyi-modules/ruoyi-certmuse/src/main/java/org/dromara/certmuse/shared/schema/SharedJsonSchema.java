package org.dromara.certmuse.shared.schema;

/**
 * JSON contracts shared by multiple CertMuse domains.
 */
public enum SharedJsonSchema implements JsonSchemaVersion {
    QUESTION_ANSWER("1.0");

    private final String version;

    SharedJsonSchema(String version) {
        this.version = version;
    }

    @Override
    public String version() {
        return version;
    }
}
