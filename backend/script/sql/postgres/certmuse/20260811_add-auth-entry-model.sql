-- Phase A: additive schema and seed audit for the unified account entry model.
-- Run through RouteFlow after a dry-run. This migration deliberately does not classify historical roles.
BEGIN;

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS first_login_at timestamp NULL;
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS entry_type varchar(20) NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sys_user GROUP BY user_name HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'cannot create global sys_user.user_name unique index: duplicate usernames exist';
    END IF;
    IF EXISTS (SELECT 1 FROM sys_role WHERE role_id = 1761300000000099001 AND role_key <> 'student') THEN
        RAISE EXCEPTION 'reserved student role id is occupied by another role';
    END IF;
    IF EXISTS (
        SELECT 1 FROM sys_role
        WHERE (role_id = 1761300000000000001 AND role_key <> 'superadmin')
           OR (role_id = 1761300000000000003 AND role_key <> 'test1')
           OR (role_id = 1761300000000000004 AND role_key <> 'test2')
    ) THEN
        RAISE EXCEPTION 'legacy ADMIN role whitelist id is occupied by another role';
    END IF;
    IF EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'student' AND del_flag = '0' GROUP BY role_key HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'multiple active student roles exist';
    END IF;
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000099001 AND perms <> 'certmuse:student')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:student' AND menu_id <> 1761400000000099001)
       OR EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000099002 AND perms <> 'certmuse:entry:admin')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:entry:admin' AND menu_id <> 1761400000000099002)
       OR EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000099003 AND perms <> 'certmuse:learning:onboarding:query')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:learning:onboarding:query' AND menu_id <> 1761400000000099003) THEN
        RAISE EXCEPTION 'reserved CertMuse entry menu id or permission is occupied by another menu';
    END IF;
    IF EXISTS (SELECT 1 FROM sys_config WHERE config_id = 1761700000000099001 AND config_key <> 'sys.account.registerClientIds')
       OR EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.account.registerClientIds' AND config_id <> 1761700000000099001) THEN
        RAISE EXCEPTION 'reserved learner registration config id or key is occupied by another config';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_user_name ON sys_user (user_name);

INSERT INTO sys_role (
    role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
    dept_check_strictly, status, del_flag, create_by, create_time, update_by, update_time, remark, entry_type
)
SELECT 1761300000000099001, '学员', 'student', 999, '5', true, true, '0', '0', 1,
       CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse learner self-registration role', 'LEARNING'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'student' AND del_flag = '0');

-- A pre-existing role named student is unambiguously the learner role; no other historical role is inferred.
UPDATE sys_role SET entry_type = 'LEARNING'
WHERE role_key = 'student' AND del_flag = '0' AND entry_type IS NULL;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
    menu_type, visible, status, perms, icon, active_menu, ext, create_by, create_time, update_by, update_time, remark
)
SELECT 1761400000000099001, '学员入口权限', 0, 999, '', NULL, NULL, 'N', 'N', 'F', '1', '0', 'certmuse:student', '#', '', '', 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden entry permission'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:student');
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
    menu_type, visible, status, perms, icon, active_menu, ext, create_by, create_time, update_by, update_time, remark
)
SELECT 1761400000000099002, '管理入口权限', 0, 999, '', NULL, NULL, 'N', 'N', 'F', '1', '0', 'certmuse:entry:admin', '#', '', '', 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden entry permission'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:entry:admin');
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
    menu_type, visible, status, perms, icon, active_menu, ext, create_by, create_time, update_by, update_time, remark
)
SELECT 1761400000000099003, '学员首次流程查询', 0, 999, '', NULL, NULL, 'N', 'N', 'F', '1', '0', 'certmuse:learning:onboarding:query', '#', '', '', 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden learner permission'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:learning:onboarding:query');

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 1761400000000099001 FROM sys_role WHERE role_key = 'student' AND del_flag = '0'
ON CONFLICT DO NOTHING;
INSERT INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 1761400000000099003 FROM sys_role WHERE role_key = 'student' AND del_flag = '0'
ON CONFLICT DO NOTHING;

-- The current environment contains only these pre-learner management roles. Match both ID and key to avoid inference.
UPDATE sys_role SET entry_type = 'ADMIN'
WHERE entry_type IS NULL
  AND del_flag = '0'
  AND (role_id, role_key) IN (
      (1761300000000000001, 'superadmin'),
      (1761300000000000003, 'test1'),
      (1761300000000000004, 'test2')
  );

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 1761400000000099002
FROM sys_role
WHERE del_flag = '0'
  AND (role_id, role_key) IN (
      (1761300000000000001, 'superadmin'),
      (1761300000000000003, 'test1'),
      (1761300000000000004, 'test2')
  )
ON CONFLICT DO NOTHING;

INSERT INTO sys_config(config_id, config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 1761700000000099001, '学员注册客户端白名单', 'sys.account.registerClientIds', '', 'Y', 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, '逗号、分号或换行分隔；空值表示不允许公共注册'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.account.registerClientIds');

DO $$
DECLARE unclassified_count integer;
DECLARE mixed_user_count integer;
DECLARE missing_admin_permission_count integer;
BEGIN
    SELECT count(*) INTO unclassified_count FROM sys_role WHERE del_flag = '0' AND entry_type IS NULL;
    SELECT count(*) INTO mixed_user_count
    FROM (
        SELECT ur.user_id
        FROM sys_user_role ur JOIN sys_role r ON r.role_id = ur.role_id
        WHERE r.status = '0' AND r.del_flag = '0' AND r.entry_type IS NOT NULL
        GROUP BY ur.user_id HAVING count(DISTINCT r.entry_type) > 1
    ) mixed_users;
    SELECT count(*) INTO missing_admin_permission_count
    FROM sys_role r
    WHERE r.status = '0' AND r.del_flag = '0' AND r.entry_type = 'ADMIN'
      AND NOT EXISTS (
          SELECT 1 FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id
          WHERE rm.role_id = r.role_id AND m.perms = 'certmuse:entry:admin'
      );
    RAISE NOTICE 'auth-entry phase A audit: unclassified_roles=%, mixed_users=%, admin_roles_missing_entry_permission=%',
        unclassified_count, mixed_user_count, missing_admin_permission_count;
END $$;

COMMIT;
