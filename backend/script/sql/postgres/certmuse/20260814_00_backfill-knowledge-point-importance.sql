\set ON_ERROR_STOP on
BEGIN;

-- Repair the August 2026 production import before the guarded U08 migration.
-- The weights are frozen from docs/testing/knowledge-point-jsonl/fixtures/knowledge-point-v7-valid.jsonl.
-- fixture_sha256=d03f81fc8b64be5c300773782977e5390c95f467388fc475aac4091e5e16a6d3
DO $$
DECLARE
    target record;
    expected_key_count constant integer := 1001;
    expected_key_fingerprint constant text := '708c3e7f22675c1bad65ecb2d63240fa';
    importance_stream constant text := '32233333231332221113222332322232332122223221133132312112222122222221331232233221233333232223222223221121121333221213233223332232223333332221212122222123332322333332221123222233333332222222222233322222222222222222222221222211111113333322232212323233333223323212333223233233333333323322332233333332333233223333222223322332233332333323333332333333223222333333333323332333333333332222223333233332111211211122222222123313333333333323333322233223232313333333332333333223333333223333333232232132211121122222222221122333323332333333333333333333223333333233233333222222322222222222222222211222333323233333333333332133333333333323312223333333221333332332233333333333233323223233333333223322333323232222222111111322222221222222222222222211222212223232223332233233233232222333323333221211333333211322223221322212211133233333221111113332232333222322222221222222312212122121111113332111122222232222332233313333221111333323222223333333332322333332223323311132223333322211111133333333333333333333333333333333333333333';
    imported_count bigint;
    imported_fingerprint text;
    expected_count bigint;
    matched_count bigint;
    conflicting_count bigint;
    invalid_count bigint;
    target_point_count bigint;
BEGIN
    IF length(importance_stream) <> expected_key_count THEN
        RAISE EXCEPTION 'knowledge-point importance fixture length mismatch';
    END IF;

    FOR target IN
        SELECT batch.id, batch.syllabus_version_id
        FROM cm_import_batch batch
        WHERE batch.import_type = 'knowledge_point'
          AND batch.status = 'completed'
    LOOP
        SELECT count(*), md5(string_agg(source_record.source_key, ',' ORDER BY source_record.source_key COLLATE "C"))
        INTO imported_count, imported_fingerprint
        FROM cm_import_record source_record
        WHERE source_record.batch_id = target.id;

        IF imported_count <> expected_key_count OR imported_fingerprint <> expected_key_fingerprint THEN
            CONTINUE;
        END IF;

        SELECT count(*)
        INTO target_point_count
        FROM cm_knowledge_point point
        WHERE point.syllabus_version_id = target.syllabus_version_id;
        IF target_point_count <> expected_key_count THEN
            RAISE EXCEPTION 'knowledge-point importance target count mismatch for syllabus %, expected %, got %',
                target.syllabus_version_id, expected_key_count, target_point_count;
        END IF;

        WITH ordered_record AS (
            SELECT source_record.source_key,
                   split_part(source_record.source_key, ':', 2)::integer AS subject_no,
                   split_part(source_record.source_key, ':', 3) AS syllabus_number,
                   row_number() OVER (ORDER BY source_record.source_key COLLATE "C")::integer AS row_no
            FROM cm_import_record source_record
            WHERE source_record.batch_id = target.id
        ), subject_mapping AS (
            SELECT (mapping.value ->> 'subject_no')::integer AS subject_no,
                   (mapping.value ->> 'exam_subject_id')::bigint AS exam_subject_id
            FROM cm_import_batch batch
            CROSS JOIN LATERAL jsonb_array_elements(batch.parse_config -> 'subject_mappings') AS mapping(value)
            WHERE batch.id = target.id
        ), expected AS (
            SELECT ordered_record.syllabus_number,
                   subject_mapping.exam_subject_id,
                   substring(importance_stream FROM ordered_record.row_no FOR 1)::smallint AS importance
            FROM ordered_record
            JOIN subject_mapping ON subject_mapping.subject_no = ordered_record.subject_no
        )
        SELECT count(*), count(point.id),
               count(*) FILTER (WHERE point.importance IS NOT NULL AND point.importance IS DISTINCT FROM expected.importance)
        INTO expected_count, matched_count, conflicting_count
        FROM expected
        LEFT JOIN cm_knowledge_point point
          ON point.syllabus_version_id = target.syllabus_version_id
         AND point.exam_subject_id = expected.exam_subject_id
         AND point.syllabus_number = expected.syllabus_number;

        IF expected_count <> expected_key_count OR matched_count <> expected_key_count THEN
            RAISE EXCEPTION 'knowledge-point importance mapping mismatch for syllabus %, expected %, matched %',
                target.syllabus_version_id, expected_key_count, matched_count;
        END IF;
        IF conflicting_count <> 0 THEN
            RAISE EXCEPTION 'knowledge-point importance conflict for syllabus %; existing non-null data differs from the approved fixture',
                target.syllabus_version_id;
        END IF;

        WITH ordered_record AS (
            SELECT source_record.source_key,
                   split_part(source_record.source_key, ':', 2)::integer AS subject_no,
                   split_part(source_record.source_key, ':', 3) AS syllabus_number,
                   row_number() OVER (ORDER BY source_record.source_key COLLATE "C")::integer AS row_no
            FROM cm_import_record source_record
            WHERE source_record.batch_id = target.id
        ), subject_mapping AS (
            SELECT (mapping.value ->> 'subject_no')::integer AS subject_no,
                   (mapping.value ->> 'exam_subject_id')::bigint AS exam_subject_id
            FROM cm_import_batch batch
            CROSS JOIN LATERAL jsonb_array_elements(batch.parse_config -> 'subject_mappings') AS mapping(value)
            WHERE batch.id = target.id
        ), expected AS (
            SELECT ordered_record.syllabus_number,
                   subject_mapping.exam_subject_id,
                   substring(importance_stream FROM ordered_record.row_no FOR 1)::smallint AS importance
            FROM ordered_record
            JOIN subject_mapping ON subject_mapping.subject_no = ordered_record.subject_no
        )
        UPDATE cm_knowledge_point point
        SET importance = expected.importance,
            row_version = point.row_version + 1,
            update_by = 1,
            update_time = CURRENT_TIMESTAMP
        FROM expected
        WHERE point.syllabus_version_id = target.syllabus_version_id
          AND point.exam_subject_id = expected.exam_subject_id
          AND point.syllabus_number = expected.syllabus_number
          AND point.importance IS NULL;

        SELECT count(*) FILTER (WHERE point.importance IS NULL OR point.importance NOT IN (1, 2, 3))
        INTO invalid_count
        FROM cm_knowledge_point point
        WHERE point.syllabus_version_id = target.syllabus_version_id;
        IF invalid_count <> 0 THEN
            RAISE EXCEPTION 'knowledge-point importance repair left % invalid rows for syllabus %',
                invalid_count, target.syllabus_version_id;
        END IF;
    END LOOP;
END $$;

COMMIT;
