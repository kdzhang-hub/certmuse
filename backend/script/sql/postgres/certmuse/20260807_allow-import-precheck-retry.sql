BEGIN;

DROP TRIGGER IF EXISTS trg_cm_import_batch_status ON cm_import_batch;

CREATE TRIGGER trg_cm_import_batch_status
    BEFORE UPDATE OF status ON cm_import_batch
    FOR EACH ROW EXECUTE FUNCTION cm_guard_status_transition(
        'uploaded>parsing,uploaded>cancelled,parsing>validating,parsing>failed,parsing>cancelled,validating>parsing,validating>waiting_confirm,validating>failed,validating>cancelled,waiting_confirm>importing,waiting_confirm>cancelled,importing>completed,importing>partial_failed,importing>failed,importing>cancelled'
    );

COMMIT;
