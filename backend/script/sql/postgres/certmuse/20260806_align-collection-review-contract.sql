-- Align collection revisions with collection-state-design contract 1.1.
\set ON_ERROR_STOP on
BEGIN;

UPDATE sys_menu
SET status = '1', visible = '1', update_time = CURRENT_TIMESTAMP,
    remark = 'Deprecated: collection publish check is executed by submit-review'
WHERE perms = 'certmuse:question:collection:check';

DROP INDEX IF EXISTS uk_cm_idempotency_collection_review_request_id;
CREATE UNIQUE INDEX uk_cm_idempotency_collection_request_id
    ON cm_idempotency_record(request_id)
    WHERE action_code IN (
        'create_collection', 'save_collection_revision', 'delete_collection_revision',
        'create_collection_revision', 'submit_collection_review', 'approve_collection',
        'reject_collection', 'offline_collection_revision'
    );

-- A collection can have many drafts, but only one revision awaiting review.
DROP INDEX IF EXISTS uk_cm_collection_revision_working;
CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_collection_revision_pending_review
    ON cm_collection_revision(collection_id)
    WHERE status='pending_review';

-- Earlier three-state migration retained superseded publications as published. They are drafts now.
DROP TRIGGER IF EXISTS trg_cm_collection_revision_status ON cm_collection_revision;
UPDATE cm_collection_revision r
SET status='draft', row_version=row_version+1, update_time=now()
WHERE r.status='published'
  AND NOT EXISTS (
      SELECT 1 FROM cm_collection_current cur
      WHERE cur.collection_id=r.collection_id AND cur.collection_revision_id=r.id
  );
CREATE TRIGGER trg_cm_collection_revision_status
BEFORE UPDATE OF status ON cm_collection_revision
FOR EACH ROW EXECUTE FUNCTION cm_guard_status_transition(
    'draft>pending_review,pending_review>draft,pending_review>published,published>draft'
);

COMMIT;
