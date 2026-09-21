-- Adds the dedicated permission required by POST /collection-revisions/{revisionId}/offline.
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu,
    ext, create_dept, create_by, create_time, update_by, update_time, remark
) VALUES (
    1761400000000090517, '下架题集', 1761400000000090150, 7, '', NULL, NULL,
    'N', 'N', 'F', '0', '0', 'certmuse:question:collection:offline', '', '', '',
    NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse collection offline permission'
)
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    perms = EXCLUDED.perms,
    status = '0',
    update_time = CURRENT_TIMESTAMP,
    remark = EXCLUDED.remark;

COMMIT;
