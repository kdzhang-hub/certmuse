-- U07: hidden learner permission for current-goal knowledge-point directory lookup.
BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000099006
        AND perms <> 'certmuse:learning:knowledge-point:query')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:learning:knowledge-point:query'
        AND menu_id <> 1761400000000099006) THEN
        RAISE EXCEPTION 'knowledge-point-directory permission id conflict';
    END IF;
END $$;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
    menu_type, visible, status, perms, icon, active_menu, ext, create_by, create_time, update_by, update_time, remark
)
SELECT 1761400000000099006, '学员知识点目录查询', 0, 999, '', NULL, NULL, 'N', 'N', 'F', '1', '0',
       'certmuse:learning:knowledge-point:query', '#', '', '', 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,
       'CertMuse hidden learner permission'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:learning:knowledge-point:query');

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 1761400000000099006
FROM sys_role
WHERE role_key = 'student' AND del_flag = '0'
ON CONFLICT DO NOTHING;

COMMIT;
