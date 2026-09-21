-- U08 learner knowledge-practice setup and atomic session creation.
\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE null_count bigint;
BEGIN
    SELECT count(*) INTO null_count FROM cm_knowledge_point WHERE importance IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'U08 requires governed knowledge-point importance values; % rows remain null', null_count;
    END IF;
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1761400000000099007
               AND perms<>'certmuse:assessment:knowledge-practice:query')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:assessment:knowledge-practice:query'
                  AND menu_id<>1761400000000099007) THEN
        RAISE EXCEPTION 'U08 query permission id conflict';
    END IF;
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1761400000000099008
               AND perms<>'certmuse:assessment:knowledge-practice:start')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:assessment:knowledge-practice:start'
                  AND menu_id<>1761400000000099008) THEN
        RAISE EXCEPTION 'U08 start permission id conflict';
    END IF;
END $$;

ALTER TABLE cm_knowledge_point ALTER COLUMN importance SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_learning_session_active_self_practice
    ON cm_learning_session(user_id, goal_id)
    WHERE session_type='self_practice' AND status IN ('created','in_progress');
CREATE INDEX IF NOT EXISTS idx_cm_question_knowledge_point_revision
    ON cm_question_knowledge(knowledge_point_id, question_revision_id, question_id);
CREATE INDEX IF NOT EXISTS idx_cm_learning_session_self_practice_request
    ON cm_learning_session(request_id)
    WHERE session_type='self_practice';

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,
    menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark)
VALUES
    (1761400000000099007,'学员知识点练习查询',0,999,'',NULL,NULL,'N','N','F','1','0',
     'certmuse:assessment:knowledge-practice:query','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'CertMuse hidden learner permission'),
    (1761400000000099008,'学员知识点练习创建',0,999,'',NULL,NULL,'N','N','F','1','0',
     'certmuse:assessment:knowledge-practice:start','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'CertMuse hidden learner permission')
ON CONFLICT (menu_id) DO UPDATE SET menu_name=EXCLUDED.menu_name,perms=EXCLUDED.perms,status='0',
    update_by=1,update_time=CURRENT_TIMESTAMP,remark=EXCLUDED.remark;

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT role_id, permission_id
  FROM sys_role CROSS JOIN (VALUES (1761400000000099007::bigint),(1761400000000099008::bigint)) p(permission_id)
 WHERE role_key='student' AND del_flag='0'
ON CONFLICT DO NOTHING;

COMMIT;
