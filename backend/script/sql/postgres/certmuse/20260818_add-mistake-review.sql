-- Student mistake-review V1.
BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_learning_session_active_correction
    ON cm_learning_session(user_id, goal_id)
    WHERE session_type = 'correction' AND status IN ('created','in_progress','submitted','settling');

ALTER TABLE cm_correction_record
    ADD CONSTRAINT fk_cm_correction_record_correction_attempt_id
    FOREIGN KEY (correction_attempt_id) REFERENCES cm_question_attempt(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_cm_error_record_user_goal_status_detected
    ON cm_error_record(user_id, goal_id, status, detected_time DESC, attempt_id DESC, id DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1761400000000099016
               AND perms<>'certmuse:learning:mistake:query')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1761400000000099017
                  AND perms<>'certmuse:learning:mistake:correct')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:learning:mistake:query'
                  AND menu_id<>1761400000000099016)
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:learning:mistake:correct'
                  AND menu_id<>1761400000000099017) THEN
        RAISE EXCEPTION 'mistake-review permission id/code conflict';
    END IF;
END $$;

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,
                     menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark)
VALUES (1761400000000099016,'学员错题查询',0,999,'',NULL,NULL,'N','N','F','1','0',
       'certmuse:learning:mistake:query','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'CertMuse hidden learner permission')
ON CONFLICT(menu_id) DO UPDATE SET menu_name=EXCLUDED.menu_name,perms=EXCLUDED.perms,status='0',
    update_by=1,update_time=CURRENT_TIMESTAMP,remark=EXCLUDED.remark;

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,
                     menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark)
VALUES (1761400000000099017,'学员错题订正',0,999,'',NULL,NULL,'N','N','F','1','0',
       'certmuse:learning:mistake:correct','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'CertMuse hidden learner permission')
ON CONFLICT(menu_id) DO UPDATE SET menu_name=EXCLUDED.menu_name,perms=EXCLUDED.perms,status='0',
    update_by=1,update_time=CURRENT_TIMESTAMP,remark=EXCLUDED.remark;

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms IN
    ('certmuse:learning:mistake:query','certmuse:learning:mistake:correct')
 WHERE r.role_key='student' AND r.del_flag='0' ON CONFLICT DO NOTHING;

COMMIT;
