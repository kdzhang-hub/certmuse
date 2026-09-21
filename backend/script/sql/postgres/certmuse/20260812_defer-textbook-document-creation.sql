\set ON_ERROR_STOP on
BEGIN;

-- A new textbook remains import staging data until the user confirms it.
-- Replacement imports still reference their existing draft document.
ALTER TABLE cm_import_batch DROP CONSTRAINT IF EXISTS ck_cm_import_batch_context;
ALTER TABLE cm_import_batch ADD CONSTRAINT ck_cm_import_batch_context CHECK (
    (import_type = 'question' AND certification_id IS NOT NULL AND exam_subject_id IS NULL AND document_id IS NULL)
    OR (import_type = 'paper' AND syllabus_version_id IS NOT NULL AND exam_subject_id IS NULL AND document_id IS NULL)
    OR (import_type = 'knowledge_point' AND syllabus_version_id IS NOT NULL AND document_id IS NULL AND exam_subject_id IS NULL)
    OR (import_type = 'question_knowledge' AND syllabus_version_id IS NOT NULL AND document_id IS NULL)
    OR (import_type = 'document_chunk' AND certification_id IS NOT NULL AND exam_subject_id IS NULL
        AND (document_id IS NOT NULL OR parse_config->>'mode' = 'create'))
);

COMMIT;
