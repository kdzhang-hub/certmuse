\set ON_ERROR_STOP on
BEGIN;

-- Textbook, question, and review actions are implemented in their list views.
-- Remove only the duplicate dynamic-menu routes that still render placeholders.
DELETE FROM sys_role_menu
WHERE menu_id IN (
    1761400000000090403,
    1761400000000090404,
    1761400000000090410,
    1761400000000090411,
    1761400000000090420
);

DELETE FROM sys_menu AS menu
USING (
    VALUES
        (1761400000000090403::bigint, 'certmuse/catalog/resource/edit'),
        (1761400000000090404::bigint, 'certmuse/catalog/resource/preview'),
        (1761400000000090410::bigint, 'certmuse/question/question/edit'),
        (1761400000000090411::bigint, 'certmuse/question/question/preview'),
        (1761400000000090420::bigint, 'certmuse/question/review/detail')
) AS legacy(menu_id, component)
WHERE menu.menu_id = legacy.menu_id
  AND menu.component = legacy.component;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys_menu
        WHERE menu_id IN (
            1761400000000090403,
            1761400000000090404,
            1761400000000090410,
            1761400000000090411,
            1761400000000090420
        )
    ) THEN
        RAISE EXCEPTION 'duplicate CertMuse placeholder menus still exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM sys_menu
        WHERE menu_id = 1761400000000090130
          AND component = 'certmuse/catalog/resource/index'
    ) OR NOT EXISTS (
        SELECT 1 FROM sys_menu
        WHERE menu_id = 1761400000000090140
          AND component = 'certmuse/question/question/index'
    ) OR NOT EXISTS (
        SELECT 1 FROM sys_menu
        WHERE menu_id = 1761400000000090210
          AND component = 'certmuse/question/review/index'
    ) THEN
        RAISE EXCEPTION 'implemented CertMuse management menu is missing';
    END IF;
END $$;

COMMIT;
