-- Rename CertMuse admin navigation items without changing routes, permissions, or hierarchy.
UPDATE sys_menu
SET menu_name = CASE menu_id
    WHEN 1761400000000090110 THEN '资格管理'
    WHEN 1761400000000090120 THEN '考纲管理'
    WHEN 1761400000000090130 THEN '教材管理'
    WHEN 1761400000000090150 THEN '题集管理'
END,
    update_time = CURRENT_TIMESTAMP,
    remark = 'CertMuse admin navigation'
WHERE menu_id IN (
    1761400000000090110,
    1761400000000090120,
    1761400000000090130,
    1761400000000090150
)
  AND menu_name IS DISTINCT FROM CASE menu_id
      WHEN 1761400000000090110 THEN '资格管理'
      WHEN 1761400000000090120 THEN '考纲管理'
      WHEN 1761400000000090130 THEN '教材管理'
      WHEN 1761400000000090150 THEN '题集管理'
  END;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys_menu
        WHERE (menu_id = 1761400000000090110 AND menu_name <> '资格管理')
           OR (menu_id = 1761400000000090120 AND menu_name <> '考纲管理')
           OR (menu_id = 1761400000000090130 AND menu_name <> '教材管理')
           OR (menu_id = 1761400000000090150 AND menu_name <> '题集管理')
    ) THEN
        RAISE EXCEPTION 'CertMuse admin navigation items could not be renamed';
    END IF;
END $$;
