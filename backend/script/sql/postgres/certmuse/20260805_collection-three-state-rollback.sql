-- Collection revisions use three states; current pointer determines the active publication.
\set ON_ERROR_STOP on
BEGIN;

DROP TRIGGER IF EXISTS trg_cm_collection_revision_status ON cm_collection_revision;
DROP INDEX IF EXISTS uk_cm_collection_revision_published;

UPDATE cm_collection_revision SET status='published', update_time=now()
WHERE status IN ('superseded','offline');
UPDATE cm_collection_revision SET status='pending_review', update_time=now()
WHERE status='approved';
UPDATE cm_collection_revision SET status='draft', update_time=now()
WHERE status='rejected';

ALTER TABLE cm_collection_revision DROP CONSTRAINT IF EXISTS ck_cm_collection_revision_status;
ALTER TABLE cm_collection_revision ADD CONSTRAINT ck_cm_collection_revision_status
    CHECK (status IN ('draft','pending_review','published'));

CREATE TRIGGER trg_cm_collection_revision_status
BEFORE UPDATE OF status ON cm_collection_revision
FOR EACH ROW EXECUTE FUNCTION cm_guard_status_transition(
    'draft>pending_review,pending_review>draft,pending_review>published'
);

CREATE OR REPLACE FUNCTION cm_validate_collection_current()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM cm_collection_revision r
        WHERE r.id=NEW.collection_revision_id
          AND r.collection_id=NEW.collection_id
          AND r.status='published'
    ) THEN
        RAISE EXCEPTION 'collection current must reference a published revision of the same collection';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_cm_collection_current_validate ON cm_collection_current;
CREATE TRIGGER trg_cm_collection_current_validate
BEFORE INSERT OR UPDATE OF collection_id, collection_revision_id ON cm_collection_current
FOR EACH ROW EXECUTE FUNCTION cm_validate_collection_current();

COMMIT;
