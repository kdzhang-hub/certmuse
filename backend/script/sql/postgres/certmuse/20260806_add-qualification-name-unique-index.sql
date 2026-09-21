\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT lower(certification_name)
        FROM cm_exam_certification
        GROUP BY lower(certification_name)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'cm_exam_certification contains case-insensitive duplicate certification_name values';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_exam_certification_name_ci
    ON cm_exam_certification (lower(certification_name));

COMMIT;
