\set ON_ERROR_STOP on
BEGIN;

ALTER TABLE cm_document ADD COLUMN IF NOT EXISTS certification_id bigint;
UPDATE cm_document d
SET certification_id = sv.certification_id
FROM cm_syllabus_version sv
WHERE d.syllabus_version_id = sv.id AND d.certification_id IS NULL;
ALTER TABLE cm_document ALTER COLUMN certification_id SET NOT NULL;
ALTER TABLE cm_document ALTER COLUMN syllabus_version_id DROP NOT NULL;
ALTER TABLE cm_document DROP CONSTRAINT IF EXISTS fk_cm_document_certification;
ALTER TABLE cm_document ADD CONSTRAINT fk_cm_document_certification
    FOREIGN KEY (certification_id) REFERENCES cm_exam_certification(id);
CREATE INDEX IF NOT EXISTS idx_cm_document_certification
    ON cm_document(certification_id, document_type, del_flag, update_time DESC);

ALTER TABLE cm_import_batch ADD COLUMN IF NOT EXISTS certification_id bigint;
UPDATE cm_import_batch b
SET certification_id = COALESCE(
    (SELECT sv.certification_id FROM cm_syllabus_version sv WHERE sv.id = b.syllabus_version_id),
    (SELECT d.certification_id FROM cm_document d WHERE d.id = b.document_id),
    (SELECT es.certification_id FROM cm_exam_subject es WHERE es.id = b.exam_subject_id)
)
WHERE b.certification_id IS NULL;
ALTER TABLE cm_import_batch DROP CONSTRAINT IF EXISTS fk_cm_import_batch_certification;
ALTER TABLE cm_import_batch ADD CONSTRAINT fk_cm_import_batch_certification
    FOREIGN KEY (certification_id) REFERENCES cm_exam_certification(id);
CREATE INDEX IF NOT EXISTS idx_cm_import_batch_certification
    ON cm_import_batch(certification_id, import_type, create_time DESC);

COMMIT;
