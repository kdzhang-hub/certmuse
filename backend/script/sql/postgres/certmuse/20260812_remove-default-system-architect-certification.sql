\set ON_ERROR_STOP on
BEGIN;

-- The qualification catalog is administrator-owned. Remove the historical
-- bootstrap qualification so a fresh or upgraded installation starts empty.
SELECT set_config('certmuse.qualification_cascade_delete', 'on', true);

DELETE FROM cm_exam_certification
WHERE id = 900000000000000001
  AND certification_code = 'SYSTEM_ARCHITECT';

SELECT set_config('certmuse.qualification_cascade_delete', 'off', true);

COMMIT;
