BEGIN;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu,
    ext, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
    (1761400000000090406, '教材查询', 1761400000000090130, 3, '', NULL, NULL,
     'N', 'N', 'F', '0', '0', 'certmuse:catalog:resource:query', '', '', '',
     NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse textbook query permission'),
    (1761400000000090407, '删除教材', 1761400000000090130, 4, '', NULL, NULL,
     'N', 'N', 'F', '0', '0', 'certmuse:catalog:resource:remove', '', '', '',
     NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse textbook removal permission')
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name=EXCLUDED.menu_name, parent_id=EXCLUDED.parent_id, order_num=EXCLUDED.order_num,
    menu_type=EXCLUDED.menu_type, visible=EXCLUDED.visible, status=EXCLUDED.status,
    perms=EXCLUDED.perms, update_by=EXCLUDED.update_by, update_time=CURRENT_TIMESTAMP,
    remark=EXCLUDED.remark;

CREATE INDEX IF NOT EXISTS idx_cm_document_textbook_visible
    ON cm_document (create_by, syllabus_version_id, status, update_time DESC)
    WHERE document_type = 'textbook' AND del_flag = '0';
CREATE INDEX IF NOT EXISTS idx_cm_import_batch_document_created
    ON cm_import_batch (document_id, create_time DESC, id DESC)
    WHERE import_type = 'document_chunk';

COMMIT;
