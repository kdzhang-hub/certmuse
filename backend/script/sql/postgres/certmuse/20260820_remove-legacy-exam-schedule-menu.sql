\set ON_ERROR_STOP on
BEGIN;

-- The first-class Content Centre page replaces the former hidden placeholder
-- under Qualification Management. Keeping both produces duplicate dynamic
-- route names for super administrators.
DELETE FROM sys_role_menu
WHERE menu_id = 1761400000000090405;

DELETE FROM sys_menu
WHERE menu_id = 1761400000000090405
  AND component = 'certmuse/catalog/exam-schedule/index'
  AND visible = '1';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys_menu
        WHERE component = 'certmuse/catalog/exam-schedule/index'
          AND menu_id <> 1761400000000090170
    ) THEN
        RAISE EXCEPTION 'legacy U16 exam schedule route still exists';
    END IF;
END $$;

COMMIT;
