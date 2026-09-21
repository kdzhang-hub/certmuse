-- The frozen collection contract performs publication checks only during submit-review.
\set ON_ERROR_STOP on
BEGIN;

UPDATE sys_menu
SET status = '1',
    visible = '1',
    update_time = CURRENT_TIMESTAMP,
    remark = 'Deprecated: collection publish checks run only during submit-review'
WHERE perms = 'certmuse:question:collection:check';

COMMIT;
