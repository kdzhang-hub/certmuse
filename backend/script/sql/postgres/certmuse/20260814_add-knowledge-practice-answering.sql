-- U09 learner knowledge-practice answering permissions and idempotency scope.
\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE permission_id bigint := 1761400000000099009;
BEGIN
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=permission_id
               AND perms<>'certmuse:assessment:knowledge-practice:answer')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:assessment:knowledge-practice:answer'
                  AND menu_id<>permission_id) THEN
        RAISE EXCEPTION 'knowledge-practice answer permission id conflict';
    END IF;
    INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,
        menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark)
    VALUES (permission_id,'学员知识点练习答题',0,999,'',NULL,NULL,'N','N','F','1','0',
        'certmuse:assessment:knowledge-practice:answer','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,
        'CertMuse hidden learner permission')
    ON CONFLICT (menu_id) DO UPDATE SET menu_name=EXCLUDED.menu_name,perms=EXCLUDED.perms,status='0',
        update_by=1,update_time=CURRENT_TIMESTAMP,remark=EXCLUDED.remark;
    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT role_id,permission_id FROM sys_role WHERE role_key='student' AND del_flag='0'
    ON CONFLICT DO NOTHING;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_idempotency_knowledge_practice_request
    ON cm_idempotency_record(request_id)
    WHERE action_code IN ('START_KNOWLEDGE_PRACTICE','SUBMIT_KNOWLEDGE_PRACTICE_ITEM','COMPLETE_KNOWLEDGE_PRACTICE');

COMMIT;
