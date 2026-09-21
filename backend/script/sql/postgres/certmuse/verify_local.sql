-- CertMuse 一期数据库本地验收（仅用于独立验证库）
-- 前置：001_foundation.sql 至 006_constraints_indexes_seed.sql 已成功执行
-- 事务：所有合成反例数据最终 ROLLBACK，不污染种子或业务数据
\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
    table_count integer;
    id_pk_count integer;
    jsonb_guard_count integer;
    trigger_count integer;
BEGIN
    SELECT count(*) INTO table_count
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name LIKE 'cm\_%' ESCAPE '\';
    IF table_count <> 78 THEN
        RAISE EXCEPTION 'expected 78 cm tables after retired scoring-point tables are removed, got %', table_count;
    END IF;

    SELECT count(*) INTO id_pk_count
      FROM information_schema.table_constraints tc
      JOIN information_schema.key_column_usage kcu
        ON kcu.constraint_schema = tc.constraint_schema
       AND kcu.constraint_name = tc.constraint_name
       AND kcu.table_schema = tc.table_schema
       AND kcu.table_name = tc.table_name
     WHERE tc.table_schema = 'public'
       AND tc.constraint_type = 'PRIMARY KEY'
       AND tc.table_name LIKE 'cm\_%' ESCAPE '\'
       AND kcu.column_name = 'id';
    IF id_pk_count <> 73 THEN
        RAISE EXCEPTION 'expected 73 id primary keys after U16 exam guidance and formal-exam persistence are added, got %', id_pk_count;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.key_column_usage
         WHERE constraint_schema = 'public'
           AND table_name = 'cm_collection_current'
           AND column_name = 'collection_id'
    ) THEN
        RAISE EXCEPTION 'cm_collection_current.collection_id natural primary key is missing';
    END IF;

    SELECT count(*) INTO jsonb_guard_count
      FROM pg_constraint
     WHERE connamespace = 'public'::regnamespace
       AND conname LIKE 'ck_cm\_%\_schema' ESCAPE '\';
    IF jsonb_guard_count <> 53 THEN
        RAISE EXCEPTION 'expected 53 JSONB schema guards after retired scoring-point schemas are removed, got %', jsonb_guard_count;
    END IF;

    SELECT count(*) INTO trigger_count
      FROM pg_trigger t
      JOIN pg_class c ON c.oid = t.tgrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'public' AND c.relname LIKE 'cm\_%' ESCAPE '\' AND NOT t.tgisinternal;
    IF trigger_count < 29 THEN
        RAISE EXCEPTION 'expected at least 29 business triggers, got %', trigger_count;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='uk_cm_user_goal_active')
       OR NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='uk_cm_question_revision_published')
       OR NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='idx_cm_async_job_claim')
       OR NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='idx_cm_import_record_batch_status')
       OR NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='idx_cm_import_issue_batch_severity_id') THEN
        RAISE EXCEPTION 'required concurrency/query indexes are missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'cm_import_batch'::regclass
           AND conname = 'ck_cm_import_batch_import_type'
           AND pg_get_constraintdef(oid) LIKE '%''paper''%'
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'cm_import_batch'::regclass
           AND conname = 'ck_cm_import_batch_context'
           AND pg_get_constraintdef(oid) LIKE '%''paper''%'
    ) THEN
        RAISE EXCEPTION 'paper import batch constraints are missing';
    END IF;

    IF EXISTS (SELECT 1 FROM cm_exam_certification WHERE certification_code='SYSTEM_ARCHITECT')
       OR EXISTS (SELECT 1 FROM cm_exam_subject WHERE certification_id=900000000000000001)
       OR EXISTS (SELECT 1 FROM cm_syllabus_version WHERE id=900000000000000021)
       OR (SELECT count(*) FROM cm_profile_rule_version WHERE version_code='PROFILE_V8') <> 1 THEN
        RAISE EXCEPTION 'default qualification removal or required profile seed is invalid';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                   AND indexname='uk_cm_learning_session_active_correction')
       OR NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                      AND indexname='idx_cm_error_record_user_goal_status_detected') THEN
        RAISE EXCEPTION 'mistake-review indexes are missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_schema='public' AND table_name='cm_correction_record'
                     AND constraint_name='fk_cm_correction_record_correction_attempt_id') THEN
        RAISE EXCEPTION 'mistake-review correction attempt foreign key is missing';
    END IF;
    IF (SELECT count(*) FROM sys_menu WHERE perms IN
        ('certmuse:learning:mistake:query','certmuse:learning:mistake:correct')) <> 2 THEN
        RAISE EXCEPTION 'mistake-review permissions are incomplete';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='uk_cm_user_goal_current') THEN
        RAISE EXCEPTION 'obsolete current-goal index remains';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE table_name='cm_user_goal_change' AND constraint_name='uk_cm_user_goal_change_goal_request_type') THEN
        RAISE EXCEPTION 'goal switch audit uniqueness is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='cm_user_goal' AND column_name='exam_batch_type')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_name='cm_user_goal' AND column_name='target_exam_date') THEN
        RAISE EXCEPTION 'goal exam batch snapshot columns are missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1761400000000099018 AND perms='certmuse:learning:goal:switch') THEN
        RAISE EXCEPTION 'goal switch permission is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1761400000000099015
                   AND perms='certmuse:assessment:knowledge-practice:ai-chat') THEN
        RAISE EXCEPTION 'practice AI chat permission is missing';
    END IF;
END $$;

-- Transaction-local catalog fixtures for the constraint checks below. These
-- deliberately reuse the legacy IDs without restoring a product default.
INSERT INTO cm_exam_certification(
    id,certification_code,certification_name,qualification_level,status,sort_order
) VALUES (
    900000000000000001,'LOCAL_VERIFY','本地验证资格','HIGH','0',1
);

INSERT INTO cm_exam_subject(id,certification_id,subject_code,subject_name) VALUES
    (900000000000000011,900000000000000001,'VERIFY_ONE','验证科目一'),
    (900000000000000012,900000000000000001,'VERIFY_TWO','验证科目二'),
    (900000000000000013,900000000000000001,'VERIFY_THREE','验证科目三');

INSERT INTO cm_syllabus_version(id,certification_id,version_name,published_date)
VALUES (900000000000000021,900000000000000001,'验证版本',NULL);

CREATE OR REPLACE FUNCTION pg_temp.expect_failure(sql_text text, case_name text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE sql_text;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'PASS: % rejected (%).', case_name, SQLSTATE;
        RETURN;
    END;
    RAISE EXCEPTION 'FAIL: % was unexpectedly accepted', case_name;
END $$;

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_user_goal(id,user_id,certification_id,syllabus_version_id,daily_minutes,status)
       VALUES (910000000000000001,910000000000000001,900000000000000001,900000000000000021,30,'invalid')$q$,
    'invalid status');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_question(id,question_code,exam_subject_id)
       VALUES (910000000000000002,'BAD-CODE',900000000000000011)$q$,
    'malformed question_code');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_async_job(id,job_type,business_key,payload)
       VALUES (910000000000000003,'verify','missing-schema','{}'::jsonb)$q$,
    'JSONB without schema_version');

INSERT INTO cm_user_goal(id,user_id,certification_id,syllabus_version_id,daily_minutes,status)
VALUES (910000000000000010,910000000000000010,900000000000000001,900000000000000021,30,'active');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_user_goal(id,user_id,certification_id,syllabus_version_id,daily_minutes,status)
       VALUES (910000000000000011,910000000000000010,900000000000000001,900000000000000021,45,'active')$q$,
    'duplicate active user goal');

UPDATE cm_user_goal SET status='completed', row_version=row_version+1 WHERE id=910000000000000010;
SELECT pg_temp.expect_failure(
    $q$UPDATE cm_user_goal SET status='active', row_version=row_version+1 WHERE id=910000000000000010$q$,
    'illegal completed-to-active transition');

INSERT INTO cm_learning_event(id,user_id,goal_id,event_type,source_type,event_data,request_id,occurred_time)
VALUES (910000000000000020,910000000000000010,910000000000000010,'verify','local',
        '{"schema_version":"1.0"}'::jsonb,'verify-append-only',now());
SELECT pg_temp.expect_failure(
    $q$UPDATE cm_learning_event SET event_type='mutated' WHERE id=910000000000000020$q$,
    'append-only update');
SELECT pg_temp.expect_failure(
    $q$DELETE FROM cm_learning_event WHERE id=910000000000000020$q$,
    'append-only delete');

INSERT INTO cm_document(id,certification_id,syllabus_version_id,title,document_type)
VALUES (910000000000000100,900000000000000001,900000000000000021,'导入约束验证文档','notes');

INSERT INTO cm_import_batch(
    id,request_id,syllabus_version_id,source_file_path,source_file_hash,
    source_file_name,source_file_size,source_file_mime,import_type,template_version,parse_config
) VALUES (
    910000000000000110,'verify-import-knowledge',900000000000000021,
    'imports/knowledge-point/910000000000000110/source.jsonl',repeat('a',64),
    'knowledge-point.jsonl',1,NULL,'knowledge_point','knowledge_point/1.0',
    '{"schema_version":"import_parse_config/1.0","subject_mappings":[{"subject_no":1,"exam_subject_id":900000000000000011}]}'::jsonb
);

DO $$
BEGIN
    IF (SELECT started_time IS NOT NULL FROM cm_import_batch WHERE id=910000000000000110) THEN
        RAISE EXCEPTION 'uploaded import batch unexpectedly has started_time';
    END IF;
END $$;

INSERT INTO cm_import_batch(
    id,request_id,certification_id,source_file_path,source_file_hash,
    source_file_name,source_file_size,source_file_mime,import_type
) VALUES (
    910000000000000111,'verify-import-question',900000000000000001,
    'imports/question/910000000000000111/source.jsonl',repeat('b',64),
    'question.jsonl',10485760,'application/x-ndjson','question'
);

INSERT INTO cm_import_batch(
    id,request_id,syllabus_version_id,source_file_path,source_file_hash,
    source_file_name,source_file_size,import_type
) VALUES (
    910000000000000112,'verify-import-question-knowledge',900000000000000021,
    'imports/question-knowledge/910000000000000112/source.jsonl',repeat('c',64),
    'question-knowledge.jsonl',128,'question_knowledge'
);

INSERT INTO cm_import_batch(
    id,request_id,certification_id,document_id,source_file_path,source_file_hash,
    source_file_name,source_file_size,import_type,template_version,parse_config
) VALUES (
    910000000000000113,'verify-import-document',900000000000000001,910000000000000100,
    'imports/document/910000000000000113/source.jsonl',repeat('d',64),
    'document.jsonl',128,'document_chunk','document_chunk/1.0',
    '{"schema_version":"import_parse_config/1.0","import_type":"document_chunk","mode":"create","title":"导入约束验证文档","edition":null,"subject_mappings":[{"subject_no":1,"exam_subject_id":900000000000000011}]}'::jsonb
);

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_import_batch(
           id,request_id,source_file_path,source_file_hash,source_file_name,source_file_size,
           import_type,template_version,parse_config
       ) VALUES (
           910000000000000120,'verify-import-missing-syllabus','imports/test/source.jsonl',
           repeat('e',64),'test.jsonl',128,'knowledge_point','knowledge_point/1.0',
           '{"schema_version":"import_parse_config/1.0","subject_mappings":[{"subject_no":1,"exam_subject_id":900000000000000011}]}'::jsonb
       )$q$,
    'knowledge_point without syllabus');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_import_batch(
           id,request_id,syllabus_version_id,exam_subject_id,source_file_path,source_file_hash,
           source_file_name,source_file_size,import_type,template_version,parse_config
       ) VALUES (
           910000000000000121,'verify-import-batch-subject',900000000000000021,900000000000000011,
           'imports/test/source.jsonl',repeat('f',64),'test.jsonl',128,'knowledge_point','knowledge_point/1.0',
           '{"schema_version":"import_parse_config/1.0","subject_mappings":[{"subject_no":1,"exam_subject_id":900000000000000011}]}'::jsonb
       )$q$,
    'knowledge_point with batch subject');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_import_batch(
           id,request_id,document_id,syllabus_version_id,source_file_path,source_file_hash,
           source_file_name,source_file_size,import_type,template_version,parse_config
       ) VALUES (
           910000000000000122,'verify-import-document-context',910000000000000100,900000000000000021,
           'imports/test/source.jsonl',repeat('1',64),'test.jsonl',128,'knowledge_point','knowledge_point/1.0',
           '{"schema_version":"import_parse_config/1.0","subject_mappings":[{"subject_no":1,"exam_subject_id":900000000000000011}]}'::jsonb
       )$q$,
    'knowledge_point with document');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_import_batch(
           id,request_id,syllabus_version_id,source_file_path,source_file_hash,
           source_file_name,source_file_size,import_type,template_version,parse_config
       ) VALUES (
           910000000000000123,'verify-import-bad-config',900000000000000021,
           'imports/test/source.jsonl',repeat('2',64),'test.jsonl',128,'knowledge_point','knowledge_point/1.0',
           '{"schema_version":"1.0","subject_mappings":[]}'::jsonb
       )$q$,
    'invalid knowledge_point parse_config');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_import_batch(
           id,request_id,syllabus_version_id,source_file_path,source_file_hash,
           source_file_name,source_file_size,import_type,template_version
       ) VALUES (
           910000000000000127,'verify-import-missing-config',900000000000000021,
           'imports/test/source.jsonl',repeat('7',64),'test.jsonl',128,
           'knowledge_point','knowledge_point/1.0'
       )$q$,
    'missing knowledge_point parse_config');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_import_batch(
           id,request_id,retry_from_batch_id,syllabus_version_id,source_file_path,source_file_hash,
           source_file_name,source_file_size,import_type
       ) VALUES (
           910000000000000124,'verify-import-self-retry',910000000000000124,900000000000000021,
           'imports/test/source.jsonl',repeat('3',64),'test.jsonl',128,'question'
       )$q$,
    'self-referencing import retry');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_import_batch(
           id,request_id,syllabus_version_id,source_file_path,source_file_hash,
           source_file_name,source_file_size,import_type
       ) VALUES (
           910000000000000125,'verify-import-empty-file',900000000000000021,
           'imports/test/source.jsonl',repeat('4',64),'test.jsonl',0,'question'
       )$q$,
    'zero-byte import file');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_import_batch(
           id,request_id,syllabus_version_id,source_file_path,source_file_hash,
           source_file_name,source_file_size,import_type
       ) VALUES (
           910000000000000126,'verify-import-oversized-file',900000000000000021,
           'imports/test/source.jsonl',repeat('5',64),'test.jsonl',67108865,'question'
       )$q$,
    'oversized import file');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_import_record(id,batch_id,source_line_no,raw_record,raw_hash)
       VALUES (910000000000000130,910000000000000110,0,
               '{"schema_version":"1.0"}'::jsonb,repeat('6',64))$q$,
    'non-positive import source line');

INSERT INTO cm_knowledge_point(
    id,syllabus_version_id,exam_subject_id,syllabus_number,syllabus_title,tree_depth,sort_order,importance
) VALUES (
    910000000000000140,900000000000000021,900000000000000011,'verify.1','编号唯一性验证',1,0,2
);

INSERT INTO cm_knowledge_point(
    id,syllabus_version_id,exam_subject_id,syllabus_number,syllabus_title,tree_depth,sort_order,importance
) VALUES (
    910000000000000141,900000000000000021,900000000000000012,'verify.1','跨科目编号复用验证',1,0,2
);

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_knowledge_point(
           id,syllabus_version_id,exam_subject_id,syllabus_number,syllabus_title,tree_depth,sort_order,importance
       ) VALUES (
           910000000000000139,900000000000000021,900000000000000011,'verify.0','非法层级',0,0,2
       )$q$,
    'non-positive knowledge point tree depth');

SELECT pg_temp.expect_failure(
    $q$INSERT INTO cm_knowledge_point(
           id,syllabus_version_id,exam_subject_id,syllabus_number,syllabus_title,tree_depth,sort_order,importance
       ) VALUES (
           910000000000000142,900000000000000021,900000000000000011,'verify.1','同科目重复编号',1,1,2
       )$q$,
    'duplicate active knowledge point number in the same syllabus and subject');

UPDATE cm_knowledge_point SET del_flag='1' WHERE id=910000000000000140;
INSERT INTO cm_knowledge_point(
    id,syllabus_version_id,exam_subject_id,syllabus_number,syllabus_title,tree_depth,sort_order,importance
) VALUES (
    910000000000000143,900000000000000021,900000000000000011,'verify.1','逻辑删除后编号复用验证',1,1,2
);

SET LOCAL enable_seqscan = off;
EXPLAIN SELECT id FROM cm_import_record WHERE batch_id=910000000000000110 AND status='pending';
EXPLAIN SELECT id FROM cm_import_issue
 WHERE import_batch_id=910000000000000110 AND severity='error'
 ORDER BY id;

DO $$
BEGIN
    IF (SELECT count(*) FROM information_schema.columns
        WHERE table_schema='public' AND table_name='cm_collection_revision'
          AND column_name IN ('collection_name','collection_type','certification_id','syllabus_version_id')) <> 4 THEN
        RAISE EXCEPTION 'collection revision snapshot columns are incomplete';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                   AND indexname='uk_cm_collection_revision_pending_review'
                   AND indexdef LIKE '%pending_review%') THEN
        RAISE EXCEPTION 'collection working revision index is missing or invalid';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                   AND indexname='uk_cm_learning_session_active_initial_diagnosis') THEN
        RAISE EXCEPTION 'initial diagnostic active-session uniqueness is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                   AND indexname='uk_cm_idempotency_diagnostic_request') THEN
        RAISE EXCEPTION 'initial diagnostic global idempotency index is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:learning:diagnostic') THEN
        RAISE EXCEPTION 'initial diagnostic learner permission is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_cm_collection_revision_status' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'collection revision status trigger is missing';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
               AND indexname='uk_cm_collection_revision_published') THEN
        RAISE EXCEPTION 'collection published revision must not be unique per collection';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_cm_collection_current_validate' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'collection current validation trigger is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_cm_collection_revision_status'
                   AND pg_get_constraintdef(oid) LIKE '%rejected%') THEN
        RAISE EXCEPTION 'collection rejected status constraint is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                   AND indexname='idx_cm_collection_item_question_revision_collection') THEN
        RAISE EXCEPTION 'collection item reverse lookup index is missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE table_schema='public' AND table_name='cm_diagnostic_timer_lease'
           AND constraint_type='PRIMARY KEY'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.key_column_usage
         WHERE constraint_schema='public' AND table_name='cm_diagnostic_timer_lease'
           AND constraint_name='pk_cm_diagnostic_timer_lease' AND column_name='session_id'
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE schemaname='public'
         AND indexname='idx_cm_diagnostic_timer_lease_attempt'
    ) THEN
        RAISE EXCEPTION 'diagnostic timer lease primary key or attempt index is missing';
    END IF;
    IF (SELECT count(*) FROM information_schema.table_constraints
        WHERE table_schema='public' AND table_name='cm_diagnostic_timer_lease'
          AND constraint_type='FOREIGN KEY') <> 2 THEN
        RAISE EXCEPTION 'diagnostic timer lease foreign keys are incomplete';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid='cm_diagnostic_timer_lease'::regclass
           AND conname='ck_cm_diagnostic_timer_lease_question_order'
           AND pg_get_constraintdef(oid) LIKE '%question_order >= 1%'
    ) THEN
        RAISE EXCEPTION 'diagnostic timer lease question-order constraint is missing';
    END IF;
    IF (SELECT count(*) FROM sys_menu WHERE perms IN (
        'certmuse:question:collection:add',
        'certmuse:question:collection:edit',
        'certmuse:question:collection:submit-review',
        'certmuse:question:collection:review')) <> 4 THEN
        RAISE EXCEPTION 'collection management permissions are incomplete';
    END IF;
    IF EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:question:collection:check' AND status='0') THEN
        RAISE EXCEPTION 'collection standalone publish-check permission must be disabled';
    END IF;
    IF (SELECT count(*) FROM sys_menu WHERE perms = 'certmuse:question:submit-review') <> 1 THEN
        RAISE EXCEPTION 'question submit-review permission is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema='public' AND table_name='cm_question_revision'
                     AND column_name='semantic_hash' AND is_nullable='NO') THEN
        RAISE EXCEPTION 'question revision semantic hash column is missing';
    END IF;
    IF (SELECT count(*) FROM information_schema.columns
        WHERE table_schema='public' AND table_name='cm_question_revision'
          AND column_name IN ('reviewed_by', 'reviewed_time', 'review_opinion', 'published_by', 'published_time')) <> 5 THEN
        RAISE EXCEPTION 'question revision review metadata columns are missing';
    END IF;
    IF EXISTS (SELECT 1 FROM cm_question_revision
               WHERE status NOT IN ('draft', 'pending_review', 'rejected', 'published')) THEN
        RAISE EXCEPTION 'question revision contains unsupported status data';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger
                   WHERE tgname='trg_cm_question_revision_status' AND NOT tgisinternal
                     AND pg_get_triggerdef(oid) LIKE '%rejected>pending_review%') THEN
        RAISE EXCEPTION 'rejected question revision resubmission transition is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                   AND indexname='uk_cm_learning_session_active_self_practice') THEN
        RAISE EXCEPTION 'active self-practice uniqueness index is missing';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='public' AND table_name='cm_knowledge_point'
                 AND column_name='importance' AND is_nullable<>'NO') THEN
        RAISE EXCEPTION 'knowledge-point importance must be not null';
    END IF;
    IF (SELECT count(*) FROM sys_menu WHERE perms IN (
        'certmuse:assessment:knowledge-practice:query',
        'certmuse:assessment:knowledge-practice:start',
        'certmuse:assessment:knowledge-practice:answer')) <> 3 THEN
        RAISE EXCEPTION 'knowledge-practice permissions are incomplete';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                   AND indexname='uk_cm_idempotency_knowledge_practice_request') THEN
        RAISE EXCEPTION 'knowledge-practice request idempotency index is missing';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
               AND table_name='cm_learning_task' AND column_name IN
                   ('task_date','status','status_changed_by','status_changed_time','status_reason')) THEN
        RAISE EXCEPTION 'U10 obsolete learning-task date/status columns remain';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
               AND table_name='cm_task_item' AND column_name='status') THEN
        RAISE EXCEPTION 'U10 obsolete task-item status column remains';
    END IF;
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema='public'
           AND table_name='cm_task_attempt'
           AND column_name='update_time'
           AND data_type='timestamp with time zone'
           AND is_nullable='NO'
           AND column_default IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'daily-task attempt update timestamp is missing';
    END IF;
    IF (SELECT count(*) FROM information_schema.columns WHERE table_schema='public'
        AND table_name='cm_learning_task' AND column_name IN ('replenishment_batch_id','display_snapshot')) <> 2
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
                      AND table_name='cm_learning_session' AND column_name='source_task_id') THEN
        RAISE EXCEPTION 'U10 task/session relationship columns are incomplete';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public'
                   AND table_name='cm_task_replenishment_batch')
       OR NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public'
                      AND table_name='cm_task_question') THEN
        RAISE EXCEPTION 'U10 replenishment or frozen-question table is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                   AND indexname='uk_cm_learning_session_daily_task_source')
       OR NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                      AND indexname='uk_cm_learning_session_active_daily_task_goal') THEN
        RAISE EXCEPTION 'U10 daily-task session uniqueness indexes are missing';
    END IF;
    IF (SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgname IN
        ('ctr_cm_learning_task_u10_bundle','ctr_cm_task_item_u10_bundle',
         'ctr_cm_task_question_u10_bundle')) <> 3 THEN
        RAISE EXCEPTION 'U10 exact two-item/one-to-eight-question deferred guards are incomplete';
    END IF;
    IF (SELECT count(*) FROM sys_menu WHERE menu_id IN
        (1761400000000099011,1761400000000099012,1761400000000099013)
        AND perms IN ('certmuse:learning:task:query','certmuse:learning:task:launch',
                      'certmuse:learning:task:supplement')) <> 3 THEN
        RAISE EXCEPTION 'U10 learner task permissions are incomplete';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public'
                   AND table_name='cm_textbook_original_pdf')
       OR NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                      AND indexname='idx_cm_textbook_original_pdf_document') THEN
        RAISE EXCEPTION 'textbook original PDF storage is incomplete';
    END IF;
    IF NOT has_table_privilege('certmuse_app', 'public.cm_textbook_original_pdf', 'SELECT')
       OR NOT has_table_privilege('certmuse_app', 'public.cm_textbook_original_pdf', 'INSERT')
       OR NOT has_table_privilege('certmuse_app', 'public.cm_textbook_original_pdf', 'UPDATE')
       OR NOT has_table_privilege('certmuse_app', 'public.cm_textbook_original_pdf', 'DELETE') THEN
        RAISE EXCEPTION 'textbook original PDF application privileges are incomplete';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM sys_menu
                   WHERE menu_id=1761400000000099014
                     AND perms='certmuse:learning:textbook:query') THEN
        RAISE EXCEPTION 'textbook original PDF learner permission is missing';
    END IF;
    IF EXISTS (SELECT 1 FROM cm_task_question
               WHERE jsonb_typeof(presentation_snapshot)<>'object'
                  OR NOT presentation_snapshot ? 'schema_version'
                  OR jsonb_typeof(grading_snapshot)<>'object'
                  OR NOT grading_snapshot ? 'schema_version') THEN
        RAISE EXCEPTION 'U10 frozen question JSONB is invalid';
    END IF;
    IF (SELECT count(*) FROM information_schema.columns
        WHERE table_schema='public' AND table_name='cm_learning_session'
          AND column_name IN ('timing_mode','duration_seconds_snapshot','deadline_time','formal_attempt_no')) <> 4 THEN
        RAISE EXCEPTION 'U13 simulation session columns are incomplete';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid='cm_learning_session'::regclass
           AND conname='fk_cm_learning_session_collection_revision'
           AND contype='f'
           AND confrelid='cm_collection_revision'::regclass
    ) THEN
        RAISE EXCEPTION 'U13 simulation collection-revision foreign key is missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid='cm_learning_session'::regclass
           AND conname='ck_cm_learning_session_simulation_fields'
           AND contype='c' AND convalidated
           AND pg_get_constraintdef(oid) LIKE '%timing_mode%standard%'
           AND pg_get_constraintdef(oid) LIKE '%deadline_time > started_time%'
    ) THEN
        RAISE EXCEPTION 'U13 validated simulation session check is missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname='public'
           AND indexname='uk_cm_learning_session_active_simulation_goal'
           AND indexdef LIKE '%user_id, goal_id%'
           AND indexdef ILIKE '%session_type%'
           AND indexdef ILIKE '%simulation%'
           AND indexdef ILIKE '%created%'
           AND indexdef ILIKE '%in_progress%'
           AND indexdef ILIKE '%submitted%'
           AND indexdef ILIKE '%settling%'
    ) THEN
        RAISE EXCEPTION 'U13 active simulation uniqueness index is missing or invalid';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname='public'
           AND indexname='uk_cm_learning_session_active_past_paper_exam'
           AND indexdef LIKE '%user_id, goal_id%'
           AND indexdef NOT LIKE '%collection_revision_id%'
           AND indexdef ILIKE '%past_paper_exam%'
           AND indexdef ILIKE '%created%'
           AND indexdef ILIKE '%in_progress%'
           AND indexdef ILIKE '%submitted%'
           AND indexdef ILIKE '%settling%'
    ) THEN
        RAISE EXCEPTION 'active past-paper exam uniqueness index is missing or invalid';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname='public'
           AND indexname='uk_cm_learning_session_simulation_attempt'
           AND indexdef LIKE '%user_id, goal_id, collection_revision_id, formal_attempt_no%'
           AND indexdef ILIKE '%session_type%'
           AND indexdef ILIKE '%simulation%'
    ) THEN
        RAISE EXCEPTION 'U13 simulation attempt uniqueness index is missing or invalid';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema='public' AND table_name='cm_formal_exam_timer_lease')
       OR NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public'
                      AND indexname='idx_cm_formal_exam_timer_lease_attempt') THEN
        RAISE EXCEPTION 'formal exam timer lease persistence is incomplete';
    END IF;
    IF (SELECT count(*) FROM information_schema.columns
        WHERE table_schema='public' AND table_name='cm_attempt_answer'
          AND column_name IN ('grading_source','grading_revision_no')) <> 2 THEN
        RAISE EXCEPTION 'formal exam grading provenance columns are incomplete';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
         WHERE tgrelid='cm_async_job'::regclass
           AND tgname='trg_cm_async_job_status'
           AND NOT tgisinternal
           AND pg_get_triggerdef(oid) LIKE '%failed>queued%'
    ) THEN
        RAISE EXCEPTION 'async job retry transition is missing';
    END IF;
END $$;

ROLLBACK;
\echo 'CertMuse local verification passed; synthetic test data rolled back.'
