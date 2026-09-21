\set ON_ERROR_STOP on
BEGIN;

ALTER TABLE cm_import_batch DROP CONSTRAINT IF EXISTS ck_cm_import_batch_import_type;
ALTER TABLE cm_import_batch ADD CONSTRAINT ck_cm_import_batch_import_type CHECK (
    import_type IN ('question', 'paper', 'question_knowledge', 'knowledge_point', 'document_chunk')
);

ALTER TABLE cm_import_batch DROP CONSTRAINT IF EXISTS ck_cm_import_batch_context;
ALTER TABLE cm_import_batch ADD CONSTRAINT ck_cm_import_batch_context CHECK (
    (import_type IN ('question', 'paper') AND syllabus_version_id IS NOT NULL AND exam_subject_id IS NULL AND document_id IS NULL)
    OR (import_type = 'knowledge_point' AND syllabus_version_id IS NOT NULL AND document_id IS NULL AND exam_subject_id IS NULL)
    OR (import_type = 'question_knowledge' AND syllabus_version_id IS NOT NULL AND document_id IS NULL)
    OR (import_type = 'document_chunk' AND document_id IS NOT NULL AND syllabus_version_id IS NULL AND exam_subject_id IS NULL)
);

COMMIT;
