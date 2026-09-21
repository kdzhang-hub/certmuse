\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
    legacy_statuses text;
BEGIN
    SELECT string_agg(status || '=' || status_count, ', ' ORDER BY status)
      INTO legacy_statuses
      FROM (
          SELECT status, count(*) AS status_count
            FROM cm_question_revision
           WHERE status NOT IN ('draft', 'pending_review', 'rejected', 'published')
           GROUP BY status
      ) legacy;
    IF legacy_statuses IS NOT NULL THEN
        RAISE EXCEPTION 'cm_question_revision contains unsupported legacy statuses: %', legacy_statuses;
    END IF;
END $$;

ALTER TABLE cm_question_revision DROP CONSTRAINT IF EXISTS ck_cm_question_revision_status;
ALTER TABLE cm_question_revision ADD CONSTRAINT ck_cm_question_revision_status
    CHECK (status IN ('draft', 'pending_review', 'rejected', 'published'));

DROP INDEX IF EXISTS uk_cm_question_revision_working;
CREATE UNIQUE INDEX uk_cm_question_revision_working ON cm_question_revision(question_id)
    WHERE status IN ('draft', 'pending_review', 'rejected');

DROP TRIGGER IF EXISTS trg_cm_question_revision_status ON cm_question_revision;
CREATE TRIGGER trg_cm_question_revision_status
    BEFORE UPDATE OF status ON cm_question_revision
    FOR EACH ROW EXECUTE FUNCTION cm_guard_status_transition(
        'draft>pending_review,pending_review>published,pending_review>rejected,rejected>draft,published>draft'
    );

COMMIT;
