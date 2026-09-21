\set ON_ERROR_STOP on
BEGIN;

UPDATE cm_async_job
SET attempt_count = GREATEST(max_attempts - 1, 0),
    next_run_time = now(),
    update_time = now()
WHERE job_type = 'DIAGNOSTIC_RESULT_PIPELINE'
  AND status = 'queued'
  AND attempt_count >= max_attempts;

COMMIT;
