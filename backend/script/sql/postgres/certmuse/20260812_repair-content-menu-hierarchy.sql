-- Repair the CertMuse content-navigation hierarchy for databases that missed
-- the original navigation seed migration. The IDs are reserved CertMuse menu IDs.
BEGIN;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu,
    ext, create_dept, create_by, create_time, update_by, update_time, remark
)
VALUES
    (1761400000000090100, '内容中心', 0, 10, 'content', NULL, NULL,
     'N', 'Y', 'M', '0', '0', NULL, 'documentation', '', '',
     NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090120, '考纲管理', 1761400000000090100, 2, 'knowledge', 'certmuse/catalog/knowledge/index', NULL,
     'N', 'Y', 'C', '0', '0', 'certmuse:catalog:knowledge:list', 'tree', '', '',
     NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation')
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    parent_id = EXCLUDED.parent_id,
    order_num = EXCLUDED.order_num,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    query_param = EXCLUDED.query_param,
    is_frame = EXCLUDED.is_frame,
    is_cache = EXCLUDED.is_cache,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    perms = EXCLUDED.perms,
    icon = EXCLUDED.icon,
    active_menu = EXCLUDED.active_menu,
    ext = EXCLUDED.ext,
    update_by = EXCLUDED.update_by,
    update_time = CURRENT_TIMESTAMP,
    remark = EXCLUDED.remark;

-- A non-super-admin can receive the existing child menu without its parent.
-- Add only the required parent grant, preserving all existing role assignments.
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 1761400000000090100
FROM sys_role_menu
WHERE menu_id = 1761400000000090120
ON CONFLICT DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sys_menu
        WHERE menu_id = 1761400000000090100
          AND parent_id = 0
          AND menu_type = 'M'
          AND path = 'content'
    ) OR NOT EXISTS (
        SELECT 1
        FROM sys_menu
        WHERE menu_id = 1761400000000090120
          AND parent_id = 1761400000000090100
          AND menu_type = 'C'
          AND path = 'knowledge'
          AND perms = 'certmuse:catalog:knowledge:list'
    ) THEN
        RAISE EXCEPTION 'CertMuse content menu hierarchy could not be repaired';
    END IF;
END $$;

COMMIT;
