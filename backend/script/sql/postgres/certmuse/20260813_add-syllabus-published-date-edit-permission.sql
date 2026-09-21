BEGIN;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu,
    ext, create_dept, create_by, create_time, update_by, update_time, remark
) VALUES (
    1761400000000090121, '编辑考纲发布日期', 1761400000000090120, 1, '', NULL, NULL,
    'N', 'N', 'F', '0', '0', 'certmuse:catalog:knowledge:edit', '', '', '',
    NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse syllabus published date edit permission'
)
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name, parent_id = EXCLUDED.parent_id, order_num = EXCLUDED.order_num,
    menu_type = EXCLUDED.menu_type, visible = EXCLUDED.visible, status = EXCLUDED.status,
    perms = EXCLUDED.perms, update_by = EXCLUDED.update_by, update_time = CURRENT_TIMESTAMP,
    remark = EXCLUDED.remark;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, 1761400000000090121
FROM sys_role role
WHERE role.role_key = 'superadmin'
ON CONFLICT DO NOTHING;

COMMIT;
