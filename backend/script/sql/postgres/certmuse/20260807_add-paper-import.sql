\set ON_ERROR_STOP on
BEGIN;

ALTER TABLE cm_import_batch DROP CONSTRAINT IF EXISTS ck_cm_import_batch_context;
ALTER TABLE cm_import_batch ADD CONSTRAINT ck_cm_import_batch_context CHECK (
    (import_type IN ('question', 'paper') AND syllabus_version_id IS NOT NULL AND exam_subject_id IS NULL AND document_id IS NULL)
    OR (import_type = 'knowledge_point' AND syllabus_version_id IS NOT NULL AND document_id IS NULL AND exam_subject_id IS NULL)
    OR (import_type = 'question_knowledge' AND syllabus_version_id IS NOT NULL AND document_id IS NULL)
    OR (import_type = 'document_chunk' AND document_id IS NOT NULL AND syllabus_version_id IS NULL AND exam_subject_id IS NULL)
);

CREATE TABLE cm_paper_import (
    import_batch_id bigint PRIMARY KEY REFERENCES cm_import_batch(id),
    certification_id bigint NOT NULL REFERENCES cm_exam_certification(id),
    collection_name varchar(200) NOT NULL,
    collection_type varchar(30) NOT NULL,
    duration_minutes integer NOT NULL CHECK (duration_minutes BETWEEN 1 AND 1440),
    collection_id bigint REFERENCES cm_collection(id),
    collection_revision_id bigint REFERENCES cm_collection_revision(id),
    CONSTRAINT ck_cm_paper_import_collection_type CHECK (collection_type IN ('FIRST_DIAGNOSTIC', 'PRACTICE', 'SIMULATION'))
);

COMMIT;
