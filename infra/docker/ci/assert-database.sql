\set ON_ERROR_STOP on

DO $$
DECLARE
    business_table_count integer;
    waiting_batch_count integer;
    persisted_record_count bigint;
    persisted_issue_count bigint;
BEGIN
    SELECT count(*)
    INTO business_table_count
    FROM pg_tables
    WHERE schemaname = 'public'
      AND tablename LIKE 'cm\_%' ESCAPE '\';

    IF business_table_count < 10 THEN
        RAISE EXCEPTION 'CertMuse schema is incomplete: only % business tables found', business_table_count;
    END IF;

    SELECT count(*)
    INTO waiting_batch_count
    FROM cm_import_batch
    WHERE status = 'waiting_confirm'
      AND valid_count = 1001
      AND failed_count = 0;

    IF waiting_batch_count < 1 THEN
        RAISE EXCEPTION 'No successfully validated 1001-row knowledge-point batch was persisted';
    END IF;

    SELECT count(*) INTO persisted_record_count FROM cm_import_record;
    SELECT count(*) INTO persisted_issue_count FROM cm_import_issue;

    IF persisted_record_count < 2000 THEN
        RAISE EXCEPTION 'Too few import records persisted: %', persisted_record_count;
    END IF;

    IF persisted_issue_count < 1 THEN
        RAISE EXCEPTION 'Edge-case import issues were not persisted';
    END IF;
END
$$;

SELECT json_build_object(
    'businessTables', (
        SELECT count(*) FROM pg_tables
        WHERE schemaname = 'public' AND tablename LIKE 'cm\_%' ESCAPE '\'
    ),
    'importBatches', (SELECT count(*) FROM cm_import_batch),
    'importRecords', (SELECT count(*) FROM cm_import_record),
    'importIssues', (SELECT count(*) FROM cm_import_issue),
    'knowledgePoints', (SELECT count(*) FROM cm_knowledge_point)
) AS database_assertion_summary;

