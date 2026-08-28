package org.dromara.certmuse.catalog.support;

/** Shared import action, task and wire-version identifiers. */
public final class ImportProtocol {
    public static final String KNOWLEDGE_CREATE_ACTION = "knowledge_point_precheck_create";
    public static final String KNOWLEDGE_CONFIRM_ACTION = "knowledge_point_import_confirm";
    public static final String KNOWLEDGE_DIFF_RESOLVE_ACTION = "knowledge_point_diff_resolve";
    public static final String KNOWLEDGE_DIFF_BATCH_RESOLVE_ACTION = "knowledge_point_diff_batch_resolve";
    public static final String QUESTION_CREATE_ACTION = "question_precheck_create";
    public static final String QUESTION_CONFIRM_ACTION = "question_import_confirm";
    public static final String PAPER_CREATE_ACTION = "paper_precheck_create";
    public static final String PAPER_VALIDATE_ACTION = "paper_precheck_validate";
    public static final String PAPER_CONFIRM_ACTION = "paper_import_confirm";
    public static final String TEXTBOOK_CREATE_ACTION = "document_chunk_precheck_create";
    public static final String TEXTBOOK_CONFIRM_ACTION = "document_chunk_import_confirm";

    public static final String KNOWLEDGE_PRECHECK_JOB = "knowledge_point_precheck";
    public static final String KNOWLEDGE_PERSIST_JOB = "knowledge_point_persist";
    public static final String QUESTION_PRECHECK_JOB = "question_precheck";
    public static final String QUESTION_PERSIST_JOB = "question_persist";
    public static final String PAPER_PRECHECK_JOB = "paper_precheck";
    public static final String PAPER_PERSIST_JOB = "paper_persist";
    public static final String TEXTBOOK_PRECHECK_JOB = "document_chunk_precheck";
    public static final String TEXTBOOK_PERSIST_JOB = "document_chunk_persist";
    public static final String OBJECT_CLEANUP_JOB = "import_object_cleanup";

    private ImportProtocol() {
    }
}
