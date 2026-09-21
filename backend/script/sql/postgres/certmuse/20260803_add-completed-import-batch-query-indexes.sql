BEGIN;

CREATE INDEX IF NOT EXISTS idx_cm_import_batch_creator_completed_query
    ON cm_import_batch(create_by, status, import_type, finished_time DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_cm_import_batch_completed_query
    ON cm_import_batch(status, import_type, finished_time DESC, id DESC);

COMMIT;
