\set ON_ERROR_STOP on
BEGIN;

-- U16 management is exposed as two first-class pages under Content Centre.
-- Existing permission nodes remain authoritative for all write operations.
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu,
    ext, create_dept, create_by, create_time, update_by, update_time, remark
)
VALUES
    (1761400000000090170, '官方考试安排', 1761400000000090100, 7,
     'exam-schedules', 'certmuse/catalog/exam-schedule/index', NULL,
     'N', 'Y', 'C', '0', '0', 'certmuse:catalog:exam-schedule:list',
     'date', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,
     'U16 official exam schedule management'),
    (1761400000000090180, '考区报名信息', 1761400000000090100, 8,
     'exam-registrations', 'certmuse/catalog/exam-registration/index', NULL,
     'N', 'Y', 'C', '0', '0', 'certmuse:catalog:exam-schedule:list',
     'documentation', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,
     'U16 regional registration management')
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

UPDATE sys_menu
SET parent_id = 1761400000000090170,
    visible = '1',
    update_by = 1,
    update_time = CURRENT_TIMESTAMP
WHERE menu_id IN (
    1761400000000099030,
    1761400000000099031,
    1761400000000099032,
    1761400000000099033
);

-- Grant the navigation chain wherever the U16 list permission is already
-- assigned. This preserves explicit role ownership instead of granting U16
-- to every administrator.
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, target.menu_id
FROM sys_role_menu granted
CROSS JOIN (
    VALUES
        (1761400000000090100::bigint),
        (1761400000000090170::bigint),
        (1761400000000090180::bigint)
) AS target(menu_id)
WHERE granted.menu_id = 1761400000000099030
ON CONFLICT DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM sys_menu
        WHERE menu_id = 1761400000000090170
          AND parent_id = 1761400000000090100
          AND component = 'certmuse/catalog/exam-schedule/index'
          AND perms = 'certmuse:catalog:exam-schedule:list'
    ) OR NOT EXISTS (
        SELECT 1 FROM sys_menu
        WHERE menu_id = 1761400000000090180
          AND parent_id = 1761400000000090100
          AND component = 'certmuse/catalog/exam-registration/index'
          AND perms = 'certmuse:catalog:exam-schedule:list'
    ) THEN
        RAISE EXCEPTION 'U16 content-centre menus could not be installed';
    END IF;
END $$;

COMMIT;
