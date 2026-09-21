-- U03: retain the learner's target examination batch independently of official schedules.
BEGIN;

ALTER TABLE cm_user_goal
    ADD COLUMN IF NOT EXISTS target_exam_year integer,
    ADD COLUMN IF NOT EXISTS target_exam_month smallint;

ALTER TABLE cm_user_goal
    DROP CONSTRAINT IF EXISTS ck_cm_user_goal_target_exam_batch;

ALTER TABLE cm_user_goal
    ADD CONSTRAINT ck_cm_user_goal_target_exam_batch CHECK (
        (target_exam_year IS NULL AND target_exam_month IS NULL)
        OR (target_exam_year IS NOT NULL AND target_exam_month IN (5, 11))
    );

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000099004 AND perms <> 'certmuse:learning:goal:create')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:learning:goal:create' AND menu_id <> 1761400000000099004) THEN
        RAISE EXCEPTION 'reserved CertMuse learning-goal permission is occupied by another menu';
    END IF;
END $$;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
    menu_type, visible, status, perms, icon, active_menu, ext, create_by, create_time, update_by, update_time, remark
)
SELECT 1761400000000099004, '学员首次目标创建', 0, 999, '', NULL, NULL, 'N', 'N', 'F', '1', '0',
       'certmuse:learning:goal:create', '#', '', '', 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,
       'CertMuse hidden learner permission'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:learning:goal:create');

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 1761400000000099004 FROM sys_role WHERE role_key = 'student' AND del_flag = '0'
ON CONFLICT DO NOTHING;

COMMIT;
