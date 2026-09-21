\set ON_ERROR_STOP on
BEGIN;

DROP TRIGGER IF EXISTS trg_cm_document_status ON cm_document;

CREATE TRIGGER trg_cm_document_status
    BEFORE UPDATE OF status ON cm_document
    FOR EACH ROW EXECUTE FUNCTION cm_guard_status_transition(
        'draft>pending_review,draft>published,pending_review>approved,pending_review>rejected,rejected>draft,approved>published,published>offline,published>draft'
    );

COMMIT;
