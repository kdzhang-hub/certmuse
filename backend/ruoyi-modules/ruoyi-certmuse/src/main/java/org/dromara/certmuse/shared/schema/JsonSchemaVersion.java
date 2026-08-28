package org.dromara.certmuse.shared.schema;

/**
 * Identifies the exact version string of a persisted or transported JSON contract.
 */
public interface JsonSchemaVersion {

    /**
     * Returns the contract version written to the JSON document.
     *
     * @return exact schema version string
     */
    String version();
}
