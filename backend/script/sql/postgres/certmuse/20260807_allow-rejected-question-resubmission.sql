\set ON_ERROR_STOP on
BEGIN;

-- A rejected question revision can be resubmitted directly after its author
-- addresses the review feedback.
DROP TRIGGER IF EXISTS trg_cm_question_revision_status ON cm_question_revision;
CREATE TRIGGER trg_cm_question_revision_status
    BEFORE UPDATE OF status ON cm_question_revision
    FOR EACH ROW EXECUTE FUNCTION cm_guard_status_transition(
        'draft>pending_review,pending_review>published,pending_review>rejected,rejected>draft,rejected>pending_review,published>draft'
    );

COMMIT;
