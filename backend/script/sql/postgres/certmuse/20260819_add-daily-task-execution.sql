-- U11 daily-task execution learner permission.
\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000099019
               AND perms <> 'certmuse:assessment:daily-task:answer')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:assessment:daily-task:answer'
                  AND menu_id <> 1761400000000099019) THEN
        RAISE EXCEPTION 'U11 permission conflict: 1761400000000099019 / certmuse:assessment:daily-task:answer';
    END IF;
END $$;

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,
    menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark)
VALUES (1761400000000099019,'学员每日任务答题',0,999,'',NULL,NULL,'N','N','F','1','0',
    'certmuse:assessment:daily-task:answer','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,
    'U11 hidden learner permission')
ON CONFLICT(menu_id) DO UPDATE SET menu_name=EXCLUDED.menu_name,perms=EXCLUDED.perms,status='0',
    update_by=1,update_time=CURRENT_TIMESTAMP,remark=EXCLUDED.remark;

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT role_id,1761400000000099019 FROM sys_role
WHERE role_key='student' AND del_flag='0' ON CONFLICT DO NOTHING;

COMMIT;
