package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.shared.schema.JsonSchemaVersion;

/**
 * Versioned JSON document contracts written by the catalog import workflow.
 */
public enum ImportJsonSchema implements JsonSchemaVersion {
    IMPORT_PARSE_CONFIG("import_parse_config/1.0"),
    QUESTION_ZIP_CONFIG("question_zip/2.0"),
    KNOWLEDGE_POINT_RAW("knowledge_point_raw/1.0"),
    QUESTION_RAW("question_raw/1.0"),
    QUESTION_IMPORT_RESULT("question_import_result/1.0"),
    IMPORT_RESULT("import_result/1.0"),
    KNOWLEDGE_POINT_INPUT("1.0"),
    TEXTBOOK_HEADING_PATH("1.0"),
    TEXTBOOK_SOURCE_LOCATOR("1.0"),
    KNOWLEDGE_IMPORT_MATCH_EVIDENCE("knowledge_import_match_evidence/1.0"),
    KNOWLEDGE_IMPORT_CHANGED_FIELDS("knowledge_import_changed_fields/1.0"),
    KNOWLEDGE_IMPORT_DIFF_RESOLUTION_RESPONSE("knowledge_import_diff_resolution_response/1.0"),
    DOCUMENT_CHUNK_RAW_RECORD("document_chunk_raw_record/1.0"),
    QUESTION_PRECHECK_JOB("question_precheck_job/1.0"),
    PAPER_PRECHECK_JOB("paper_precheck_job/1.0"),
    TEXTBOOK_PRECHECK_JOB("document_chunk_precheck_job/1.0"),
    KNOWLEDGE_POINT_PRECHECK_JOB("knowledge_point_precheck_job/1.0"),
    KNOWLEDGE_POINT_PERSIST_JOB("knowledge_point_persist_job/1.0"),
    QUESTION_PERSIST_JOB("question_persist_job/1.0"),
    PAPER_PERSIST_JOB("paper_persist_job/1.0"),
    TEXTBOOK_PERSIST_JOB("document_chunk_persist_job/1.0"),
    OBJECT_CLEANUP_JOB("import_object_cleanup/1.0"),
    KNOWLEDGE_POINT_PRECHECK_CREATE("knowledge_point_precheck_create/1.0"),
    QUESTION_PRECHECK_CREATE("question_precheck_create/1.0"),
    PAPER_PRECHECK_CREATE("paper_precheck_create/1.0"),
    TEXTBOOK_PRECHECK_CREATE("document_chunk_precheck_create/1.0"),
    QUESTION_IMPORT_CONFIRM("question_import_confirm/1.0"),
    PAPER_IMPORT_CONFIRM("paper_import_confirm/1.0"),
    TEXTBOOK_IMPORT_CONFIRM("document_chunk_import_confirm/1.0"),
    KNOWLEDGE_POINT_IMPORT_CONFIRM("knowledge_point_import_confirm/1.0");

    private final String version;

    ImportJsonSchema(String version) {
        this.version = version;
    }

    @Override
    public String version() {
        return version;
    }
}
