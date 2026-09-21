\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM cm_syllabus_version
        GROUP BY certification_id HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'cannot enforce one syllabus per certification: duplicate syllabus records exist';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_syllabus_version_certification
    ON cm_syllabus_version (certification_id);

COMMIT;
