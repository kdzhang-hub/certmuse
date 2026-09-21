BEGIN;

ALTER TABLE cm_async_job ADD COLUMN IF NOT EXISTS lease_until timestamptz;
CREATE INDEX IF NOT EXISTS idx_cm_async_job_lease ON cm_async_job(status, lease_until) WHERE status = 'running';

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_cm_import_record_batch_id_id') THEN
        ALTER TABLE cm_import_record ADD CONSTRAINT uk_cm_import_record_batch_id_id UNIQUE (batch_id, id);
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cm_import_issue_batch_record') THEN
        ALTER TABLE cm_import_issue ADD CONSTRAINT fk_cm_import_issue_batch_record
            FOREIGN KEY (import_batch_id, import_record_id)
            REFERENCES cm_import_record(batch_id, id) ON DELETE RESTRICT;
    END IF;
END $$;

COMMIT;
