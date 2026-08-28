package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.shared.schema.JsonSchemaVersion;

/**
 * Versioned JSON contracts owned by the catalog domain outside the import workflow.
 */
public enum CatalogJsonSchema implements JsonSchemaVersion {
    SYLLABUS_PUBLISHED_DATE_RESPONSE("syllabus_published_date_response/1.0"),
    QUALIFICATION_VERSION_RESPONSE("qualification_version_response/1.0"),
    EXAM_GUIDANCE_IDEMPOTENCY("exam_guidance_idempotency/1.0");

    private final String version;

    CatalogJsonSchema(String version) {
        this.version = version;
    }

    @Override
    public String version() {
        return version;
    }
}
