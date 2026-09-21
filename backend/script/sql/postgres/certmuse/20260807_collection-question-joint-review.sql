-- Collection/question joint review and real rejected collection state.
-- Deploy together with a backend version that understands the rejected state.
\set ON_ERROR_STOP on
BEGIN;

DROP TRIGGER IF EXISTS trg_cm_collection_revision_status ON cm_collection_revision;
ALTER TABLE cm_collection_revision DROP CONSTRAINT IF EXISTS ck_cm_collection_revision_status;

UPDATE cm_collection_revision
SET status = 'rejected', update_time = now()
WHERE status = 'draft'
  AND nullif(btrim(review_opinion), '') IS NOT NULL;

ALTER TABLE cm_collection_revision ADD CONSTRAINT ck_cm_collection_revision_status
    CHECK (status IN ('draft', 'pending_review', 'published', 'rejected'));

CREATE TRIGGER trg_cm_collection_revision_status
BEFORE UPDATE OF status ON cm_collection_revision
FOR EACH ROW EXECUTE FUNCTION cm_guard_status_transition(
    'draft>pending_review,rejected>pending_review,pending_review>rejected,pending_review>published,published>draft'
);

CREATE INDEX IF NOT EXISTS idx_cm_collection_item_question_revision_collection
    ON cm_collection_item(question_revision_id, collection_revision_id);

COMMIT;
