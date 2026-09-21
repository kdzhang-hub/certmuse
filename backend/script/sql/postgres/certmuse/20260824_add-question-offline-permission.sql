-- Grants the explicit permission required by POST /question-revisions/{revisionId}/offline.
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu,
    ext, create_dept, create_by, create_time, update_by, update_time, remark
) VALUES (
    1761400000000090414, '下架题目', 1761400000000090140, 5, '', NULL, NULL,
    'N', 'N', 'F', '0', '0', 'certmuse:question:offline', '', '', '',
    NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse question offline permission'
)
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    parent_id = EXCLUDED.parent_id,
    order_num = EXCLUDED.order_num,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    status = '0',
    perms = EXCLUDED.perms,
    update_by = EXCLUDED.update_by,
    update_time = CURRENT_TIMESTAMP,
    remark = EXCLUDED.remark;

COMMIT;
