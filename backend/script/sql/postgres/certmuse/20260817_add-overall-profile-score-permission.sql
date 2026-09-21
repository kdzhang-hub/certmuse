-- Hidden learner permission for reading the current overall profile score.
\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM sys_menu
         WHERE menu_id = 1761400000000099010
           AND perms IS DISTINCT FROM 'certmuse:profile:overall-score:query'
    ) OR EXISTS (
        SELECT 1
          FROM sys_menu
         WHERE perms = 'certmuse:profile:overall-score:query'
           AND menu_id <> 1761400000000099010
    ) THEN
        RAISE EXCEPTION 'overall-profile-score permission id conflict';
    END IF;
END $$;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
    menu_type, visible, status, perms, icon, active_menu, ext,
    create_by, create_time, update_by, update_time, remark
)
SELECT 1761400000000099010, '学员学习画像总分查询', 0, 999, '', NULL, NULL, 'N', 'N',
       'F', '1', '0', 'certmuse:profile:overall-score:query', '#', '', '',
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden learner permission'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'certmuse:profile:overall-score:query'
);

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 1761400000000099010
  FROM sys_role
 WHERE role_key = 'student'
   AND status = '0'
   AND del_flag = '0'
ON CONFLICT DO NOTHING;

COMMIT;
