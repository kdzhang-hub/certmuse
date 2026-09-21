-- Keep authenticated users signed in for up to 72 hours of inactivity.
-- The fixed seven-day token lifetime remains unchanged.
\set ON_ERROR_STOP on
BEGIN;

UPDATE sys_client
SET active_timeout = 259200,
    update_time = CURRENT_TIMESTAMP
WHERE client_key IN ('pc', 'app')
  AND del_flag = '0'
  AND active_timeout IS DISTINCT FROM 259200;

COMMIT;
