\set ON_ERROR_STOP on
BEGIN;

-- Regional registration is maintained inside the exam-period drawer.
-- The former route redirects to the unified page; remove the duplicate sidebar entry.
UPDATE sys_menu
SET visible = '1',
    update_by = 1,
    update_time = CURRENT_TIMESTAMP,
    remark = 'U16 legacy route: redirects to official exam schedule'
WHERE menu_id = 1761400000000090180
  AND component = 'certmuse/catalog/exam-registration/index';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM sys_menu
        WHERE menu_id = 1761400000000090180
          AND visible = '1'
          AND component = 'certmuse/catalog/exam-registration/index'
    ) THEN
        RAISE EXCEPTION 'U16 regional-registration menu could not be hidden';
    END IF;
END $$;

COMMIT;
