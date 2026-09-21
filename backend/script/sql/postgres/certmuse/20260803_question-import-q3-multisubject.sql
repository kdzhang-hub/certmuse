BEGIN;

ALTER TABLE cm_import_batch DROP CONSTRAINT IF EXISTS ck_cm_import_batch_context;
ALTER TABLE cm_import_batch ADD CONSTRAINT ck_cm_import_batch_context CHECK (
    (import_type = 'question' AND syllabus_version_id IS NOT NULL AND exam_subject_id IS NULL AND document_id IS NULL)
    OR (import_type = 'knowledge_point' AND syllabus_version_id IS NOT NULL AND document_id IS NULL AND exam_subject_id IS NULL)
    OR (import_type = 'question_knowledge' AND syllabus_version_id IS NOT NULL AND document_id IS NULL)
    OR (import_type = 'document_chunk' AND document_id IS NOT NULL AND syllabus_version_id IS NULL AND exam_subject_id IS NULL)
);

ALTER TABLE cm_import_record ADD COLUMN IF NOT EXISTS subject_no smallint;
ALTER TABLE cm_import_record ADD COLUMN IF NOT EXISTS exam_subject_id bigint;
ALTER TABLE cm_import_record DROP CONSTRAINT IF EXISTS fk_cm_import_record_exam_subject_id;
ALTER TABLE cm_import_record ADD CONSTRAINT fk_cm_import_record_exam_subject_id
    FOREIGN KEY (exam_subject_id) REFERENCES cm_exam_subject(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_cm_import_record_batch_subject_no ON cm_import_record(batch_id,subject_no);

COMMIT;
