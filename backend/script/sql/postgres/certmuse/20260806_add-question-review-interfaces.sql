\set ON_ERROR_STOP on
BEGIN;

-- New review actions use a direct pending_review -> published/draft workflow.
-- Keep legacy approved/rejected transitions so historical rows remain operable.
DROP TRIGGER IF EXISTS trg_cm_question_revision_status ON cm_question_revision;
CREATE TRIGGER trg_cm_question_revision_status
    BEFORE UPDATE OF status ON cm_question_revision
    FOR EACH ROW
    EXECUTE FUNCTION cm_guard_status_transition(
        'draft>pending_review,pending_review>approved,pending_review>rejected,rejected>draft,approved>published,pending_review>published,pending_review>draft,published>superseded,published>offline'
    );

-- Scope request-id reuse protection to the two new question review actions only.
CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_idempotency_question_review_request_id
    ON cm_idempotency_record(request_id)
    WHERE action_code IN ('approve_question_revision', 'reject_question_revision');

COMMIT;
