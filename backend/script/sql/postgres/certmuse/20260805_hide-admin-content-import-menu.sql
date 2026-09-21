-- Keep import routes available to permitted users without exposing the import batch entry in the sidebar.
UPDATE sys_menu
SET visible = '1',
    update_time = CURRENT_TIMESTAMP,
    remark = 'CertMuse hidden business route'
WHERE menu_id = 1761400000000090160
  AND (visible IS DISTINCT FROM '1' OR remark IS DISTINCT FROM 'CertMuse hidden business route');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys_menu
        WHERE menu_id = 1761400000000090160
          AND visible <> '1'
    ) THEN
        RAISE EXCEPTION 'CertMuse content import menu could not be hidden';
    END IF;
END $$;
