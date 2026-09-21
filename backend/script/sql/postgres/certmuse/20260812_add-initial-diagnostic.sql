\set ON_ERROR_STOP on
BEGIN;

ALTER TABLE cm_user_goal ADD COLUMN IF NOT EXISTS target_exam_year integer;
ALTER TABLE cm_user_goal ADD COLUMN IF NOT EXISTS target_exam_month smallint;
ALTER TABLE cm_learning_session ADD COLUMN IF NOT EXISTS last_question_order integer;
ALTER TABLE cm_learning_session ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;
ALTER TABLE cm_learning_session ADD COLUMN IF NOT EXISTS certification_id bigint;
ALTER TABLE cm_learning_session ADD COLUMN IF NOT EXISTS syllabus_version_id bigint;
ALTER TABLE cm_learning_session ADD COLUMN IF NOT EXISTS collection_revision_id bigint;
ALTER TABLE cm_learning_session ADD COLUMN IF NOT EXISTS target_exam_year integer;
ALTER TABLE cm_learning_session ADD COLUMN IF NOT EXISTS target_exam_month smallint;

ALTER TABLE cm_learning_session ADD CONSTRAINT ck_cm_learning_session_diagnostic_position
    CHECK (session_type <> 'initial_diagnosis' OR last_question_order BETWEEN 1 AND 50) NOT VALID;
ALTER TABLE cm_learning_session ADD CONSTRAINT ck_cm_learning_session_diagnostic_snapshot
    CHECK (session_type <> 'initial_diagnosis' OR (certification_id IS NOT NULL AND syllabus_version_id IS NOT NULL AND collection_revision_id IS NOT NULL AND target_exam_year IS NOT NULL AND target_exam_month IN (5,11))) NOT VALID;
CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_learning_session_active_initial_diagnosis
    ON cm_learning_session(user_id,goal_id) WHERE session_type='initial_diagnosis' AND status IN ('created','in_progress','submitted','settling');
CREATE INDEX IF NOT EXISTS idx_cm_learning_session_diagnostic_owner ON cm_learning_session(user_id,id) WHERE session_type='initial_diagnosis';
CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_idempotency_diagnostic_request
    ON cm_idempotency_record(request_id) WHERE action_code LIKE 'DIAGNOSTIC_%';

DO $$
DECLARE permission_id bigint := 1761400000000099005;
BEGIN
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=permission_id AND perms<>'certmuse:learning:diagnostic')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:learning:diagnostic' AND menu_id<>permission_id) THEN
        RAISE EXCEPTION 'diagnostic permission id conflict';
    END IF;
    INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,update_by,update_time,remark)
    SELECT permission_id,'学员首次诊断',0,999,'',NULL,NULL,'N','N','F','1','0','certmuse:learning:diagnostic','#',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'CertMuse hidden learner permission'
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:learning:diagnostic');
    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT role_id,permission_id FROM sys_role WHERE role_key='student' AND del_flag='0'
    ON CONFLICT DO NOTHING;
END $$;

COMMIT;
