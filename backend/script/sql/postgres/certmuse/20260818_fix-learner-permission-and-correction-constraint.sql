-- Repair already-applied learner permission IDs and make the correction FK safe
-- for databases that completed the original mistake-review migration.
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM sys_menu
        WHERE menu_id IN (1761400000000099015, 1761400000000099018)
          AND perms NOT IN (
              'certmuse:learning:goal:switch',
              'certmuse:assessment:knowledge-practice:ai-chat'
          )
    ) OR EXISTS (
        SELECT 1 FROM sys_menu
        WHERE perms IN (
            'certmuse:learning:goal:switch',
            'certmuse:assessment:knowledge-practice:ai-chat'
        )
          AND menu_id NOT IN (1761400000000099015, 1761400000000099018)
    ) THEN
        RAISE EXCEPTION 'learner permission IDs are occupied by incompatible records';
    END IF;

    UPDATE sys_menu
       SET perms = 'certmuse:internal:learner-permission-swap:' || menu_id
     WHERE menu_id IN (1761400000000099015, 1761400000000099018)
       AND perms IN (
           'certmuse:learning:goal:switch',
           'certmuse:assessment:knowledge-practice:ai-chat'
       );

    UPDATE sys_menu
       SET menu_name = '学员题目AI助教',
           perms = 'certmuse:assessment:knowledge-practice:ai-chat',
           status = '0',
           remark = 'CertMuse hidden learner permission',
           update_by = 1,
           update_time = CURRENT_TIMESTAMP
     WHERE menu_id = 1761400000000099015;

    UPDATE sys_menu
       SET menu_name = '学员学习目标切换',
           perms = 'certmuse:learning:goal:switch',
           status = '0',
           remark = 'CertMuse hidden learner permission',
           update_by = 1,
           update_time = CURRENT_TIMESTAMP
     WHERE menu_id = 1761400000000099018;

    INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,
                         menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark)
    SELECT 1761400000000099015,'学员题目AI助教',0,999,'',NULL,NULL,'N','N','F','1','0',
           'certmuse:assessment:knowledge-practice:ai-chat','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,
           'CertMuse hidden learner permission'
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1761400000000099015);

    INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,
                         menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark)
    SELECT 1761400000000099018,'学员学习目标切换',0,999,'',NULL,NULL,'N','N','F','1','0',
           'certmuse:learning:goal:switch','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,
           'CertMuse hidden learner permission'
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1761400000000099018);

    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT role_id,1761400000000099015 FROM sys_role WHERE role_key='student' AND del_flag='0'
    ON CONFLICT DO NOTHING;
    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT role_id,1761400000000099018 FROM sys_role WHERE role_key='student' AND del_flag='0'
    ON CONFLICT DO NOTHING;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_cm_correction_record_correction_attempt_id'
          AND conrelid = 'cm_correction_record'::regclass
    ) THEN
        ALTER TABLE cm_correction_record
            ADD CONSTRAINT fk_cm_correction_record_correction_attempt_id
            FOREIGN KEY (correction_attempt_id) REFERENCES cm_question_attempt(id) ON DELETE RESTRICT;
    END IF;
END $$;

COMMIT;
