-- Rename the existing CertMuse admin navigation item without changing its route or permission.
UPDATE sys_menu
SET menu_name = '资格与版本',
    update_time = CURRENT_TIMESTAMP,
    remark = 'CertMuse admin navigation'
WHERE menu_id = 1761400000000090110
  AND menu_name <> '资格与版本';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM sys_menu
        WHERE menu_id = 1761400000000090110
          AND menu_name = '资格与版本'
    ) THEN
        RAISE EXCEPTION 'CertMuse qualification/version menu was not found or could not be renamed';
    END IF;
END $$;
