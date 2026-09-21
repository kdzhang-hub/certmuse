\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM cm_exam_certification
        WHERE qualification_level NOT IN ('HIGH', 'MIDDLE', 'LOW')
    ) THEN
        RAISE EXCEPTION 'cm_exam_certification contains unsupported qualification_level values';
    END IF;
    IF EXISTS (
        SELECT lower(certification_code)
        FROM cm_exam_certification
        GROUP BY lower(certification_code)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'cm_exam_certification contains case-insensitive duplicate certification_code values';
    END IF;
    IF EXISTS (
        SELECT lower(certification_name)
        FROM cm_exam_certification
        GROUP BY lower(certification_name)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'cm_exam_certification contains case-insensitive duplicate certification_name values';
    END IF;
    IF EXISTS (
        SELECT certification_id, lower(version_name)
        FROM cm_syllabus_version
        GROUP BY certification_id, lower(version_name)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'cm_syllabus_version contains case-insensitive duplicate version_name values';
    END IF;
END $$;

ALTER TABLE cm_exam_certification
    DROP CONSTRAINT IF EXISTS ck_cm_exam_certification_qualification_level;
ALTER TABLE cm_exam_certification
    ADD CONSTRAINT ck_cm_exam_certification_qualification_level
    CHECK (qualification_level IN ('HIGH', 'MIDDLE', 'LOW'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_exam_certification_code_ci
    ON cm_exam_certification (lower(certification_code));
CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_exam_certification_name_ci
    ON cm_exam_certification (lower(certification_name));
CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_syllabus_version_name_ci
    ON cm_syllabus_version (certification_id, lower(version_name));

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu,
    ext, create_dept, create_by, create_time, update_by, update_time, remark
)
VALUES
    (1761400000000090111, '资格新增', 1761400000000090110, 1, NULL, NULL, NULL, 'N', 'N', 'F', '0', '0', 'certmuse:catalog:subject:add', '#', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse qualification/version button'),
    (1761400000000090112, '资格编辑', 1761400000000090110, 2, NULL, NULL, NULL, 'N', 'N', 'F', '0', '0', 'certmuse:catalog:subject:edit', '#', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse qualification/version button'),
    (1761400000000090113, '资格删除', 1761400000000090110, 3, NULL, NULL, NULL, 'N', 'N', 'F', '0', '0', 'certmuse:catalog:subject:remove', '#', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse qualification/version button')
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    parent_id = EXCLUDED.parent_id,
    order_num = EXCLUDED.order_num,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    perms = EXCLUDED.perms,
    update_by = EXCLUDED.update_by,
    update_time = CURRENT_TIMESTAMP,
    remark = EXCLUDED.remark;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, menu.menu_id
FROM sys_role role
CROSS JOIN (VALUES
    (1761400000000090110::bigint),
    (1761400000000090111::bigint),
    (1761400000000090112::bigint),
    (1761400000000090113::bigint)
) AS menu(menu_id)
WHERE role.role_key = 'superadmin'
ON CONFLICT DO NOTHING;

COMMIT;
