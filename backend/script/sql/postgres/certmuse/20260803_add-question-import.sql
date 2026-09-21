BEGIN;

ALTER TABLE cm_import_batch DROP CONSTRAINT IF EXISTS ck_cm_import_batch_file_size;
ALTER TABLE cm_import_batch ADD CONSTRAINT ck_cm_import_batch_file_size CHECK (source_file_size BETWEEN 1 AND 67108864);

ALTER TABLE cm_question ADD COLUMN IF NOT EXISTS source_identity_hash varchar(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_question_source_identity_hash
    ON cm_question(source_identity_hash)
    WHERE source_identity_hash IS NOT NULL AND del_flag = '0';

COMMIT;
