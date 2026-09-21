-- U12: learner switches an active goal to a different certification.
BEGIN;

DROP INDEX IF EXISTS uk_cm_user_goal_current;
CREATE UNIQUE INDEX uk_cm_user_goal_active ON cm_user_goal(user_id) WHERE status='active';

ALTER TABLE cm_user_goal_change DROP CONSTRAINT IF EXISTS uk_cm_user_goal_change_request_id;
ALTER TABLE cm_user_goal_change
    ADD CONSTRAINT uk_cm_user_goal_change_goal_request_type UNIQUE(goal_id, request_id, change_type);

ALTER TABLE cm_user_goal
    ADD COLUMN IF NOT EXISTS exam_batch_type varchar(20) NOT NULL DEFAULT 'legacy_unknown',
    ADD COLUMN IF NOT EXISTS target_exam_date date;

ALTER TABLE cm_user_goal DROP CONSTRAINT IF EXISTS ck_cm_user_goal_exam_batch_snapshot;
ALTER TABLE cm_user_goal ADD CONSTRAINT ck_cm_user_goal_exam_batch_snapshot CHECK (
    (exam_batch_type='official' AND target_exam_date IS NOT NULL
        AND extract(year from target_exam_date)=target_exam_year
        AND extract(month from target_exam_date)=target_exam_month)
    OR (exam_batch_type IN ('estimated','legacy_unknown') AND target_exam_date IS NULL)
);

DO $$
DECLARE permission_id bigint := 1761400000000099015;
BEGIN
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=permission_id AND perms<>'certmuse:learning:goal:switch')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:learning:goal:switch' AND menu_id<>permission_id) THEN
        RAISE EXCEPTION 'reserved CertMuse learning-goal switch permission is occupied';
    END IF;
END $$;

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,
    menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark)
SELECT 1761400000000099015,'学员学习目标切换',0,999,'',NULL,NULL,'N','N','F','1','0',
       'certmuse:learning:goal:switch','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,
       'CertMuse hidden learner permission'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:learning:goal:switch');

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT role_id,1761400000000099015 FROM sys_role WHERE role_key='student' AND del_flag='0'
ON CONFLICT DO NOTHING;

COMMIT;
