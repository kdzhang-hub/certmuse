\set ON_ERROR_STOP on
BEGIN;

DROP TRIGGER IF EXISTS trg_cm_async_job_status ON cm_async_job;

CREATE TRIGGER trg_cm_async_job_status
    BEFORE UPDATE OF status ON cm_async_job
    FOR EACH ROW EXECUTE FUNCTION cm_guard_status_transition(
        'queued>running,queued>cancelled,running>queued,running>succeeded,running>failed,running>cancelled,failed>queued'
    );

COMMIT;
