-- U15 learner permission for past-paper practice and formal examination.
\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
    permission_id constant bigint := 1761400000000099021;
BEGIN
    IF EXISTS (
        SELECT 1 FROM sys_menu
         WHERE menu_id = permission_id
           AND perms <> 'certmuse:assessment:past-paper:answer'
    ) OR EXISTS (
        SELECT 1 FROM sys_menu
         WHERE perms = 'certmuse:assessment:past-paper:answer'
           AND menu_id <> permission_id
    ) THEN
        RAISE EXCEPTION 'past-paper answer permission ID is occupied by an incompatible record';
    END IF;

    INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,
                         menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark)
    SELECT permission_id,'学员历年真题答题',0,999,'',NULL,NULL,'N','N','F','1','0',
           'certmuse:assessment:past-paper:answer','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,
           'U15 hidden learner permission'
     WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = permission_id);

    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT role_id,permission_id FROM sys_role
     WHERE role_key = 'student' AND del_flag = '0'
    ON CONFLICT DO NOTHING;
END $$;

COMMIT;
