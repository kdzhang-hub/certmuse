\set ON_ERROR_STOP on
BEGIN;

-- The import UI selects qualifications through their syllabus versions. Keep every
-- qualification immediately selectable, including rows created before the service
-- began creating the initial version transactionally.
LOCK TABLE cm_exam_certification IN SHARE MODE;
LOCK TABLE cm_syllabus_version IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    base_id bigint;
    missing_count bigint;
BEGIN
    SELECT coalesce(max(id), 0) INTO base_id FROM cm_syllabus_version;
    SELECT count(*) INTO missing_count
    FROM cm_exam_certification certification
    WHERE NOT EXISTS (
        SELECT 1
        FROM cm_syllabus_version syllabus
        WHERE syllabus.certification_id = certification.id
    );

    IF missing_count = 0 THEN
        RETURN;
    END IF;
    IF base_id > 9223372036854775807 - missing_count THEN
        RAISE EXCEPTION 'Unable to allocate syllabus version identifiers';
    END IF;

    INSERT INTO cm_syllabus_version (
        id, certification_id, version_name, published_date, source_file_path,
        create_dept, create_by, create_time, update_by, update_time
    )
    SELECT base_id + row_number() OVER (ORDER BY certification.id),
           certification.id,
           '第一版',
           NULL,
           NULL,
           certification.create_dept,
           certification.create_by,
           now(),
           certification.create_by,
           now()
    FROM cm_exam_certification certification
    WHERE NOT EXISTS (
        SELECT 1
        FROM cm_syllabus_version syllabus
        WHERE syllabus.certification_id = certification.id
    )
    ORDER BY certification.id;
END $$;

COMMIT;
