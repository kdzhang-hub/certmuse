\set ON_ERROR_STOP on
BEGIN;

-- Question imports may be scoped directly to a certification. Textbook imports
-- may optionally retain a syllabus while always belonging to a document.
ALTER TABLE cm_import_batch DROP CONSTRAINT IF EXISTS ck_cm_import_batch_context;
ALTER TABLE cm_import_batch ADD CONSTRAINT ck_cm_import_batch_context CHECK (
    (import_type = 'question' AND certification_id IS NOT NULL AND exam_subject_id IS NULL AND document_id IS NULL)
    OR (import_type = 'paper' AND syllabus_version_id IS NOT NULL AND exam_subject_id IS NULL AND document_id IS NULL)
    OR (import_type = 'knowledge_point' AND syllabus_version_id IS NOT NULL AND document_id IS NULL AND exam_subject_id IS NULL)
    OR (import_type = 'question_knowledge' AND syllabus_version_id IS NOT NULL AND document_id IS NULL)
    OR (import_type = 'document_chunk' AND certification_id IS NOT NULL AND document_id IS NOT NULL AND exam_subject_id IS NULL)
);

COMMIT;
