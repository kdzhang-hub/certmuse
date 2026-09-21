BEGIN;

ALTER TABLE cm_knowledge_point
    ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_cm_knowledge_point_row_version'
    ) THEN
        ALTER TABLE cm_knowledge_point
            ADD CONSTRAINT ck_cm_knowledge_point_row_version CHECK (row_version >= 0);
    END IF;
END $$;

COMMIT;
