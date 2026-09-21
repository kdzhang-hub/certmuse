BEGIN;

-- A rejected revision may be edited only as part of reopening it as a draft.
CREATE OR REPLACE FUNCTION cm_guard_question_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE old_content jsonb; new_content jsonb;
BEGIN
  IF TG_OP='DELETE' AND OLD.status <> 'draft' THEN
    RAISE EXCEPTION 'non-draft question revision is immutable';
  END IF;

  IF TG_OP='UPDATE' AND OLD.status = 'rejected' THEN
    IF NEW.status <> 'draft' THEN
      RAISE EXCEPTION 'rejected question revision must be reopened as draft before content can change';
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP='UPDATE' AND OLD.status <> 'draft' THEN
    old_content := to_jsonb(OLD) - ARRAY['status','row_version','reviewed_by','reviewed_time','review_opinion','published_by','published_time','update_by','update_time'];
    new_content := to_jsonb(NEW) - ARRAY['status','row_version','reviewed_by','reviewed_time','review_opinion','published_by','published_time','update_by','update_time'];
    IF old_content IS DISTINCT FROM new_content THEN
      RAISE EXCEPTION 'non-draft question revision content is immutable';
    END IF;
  END IF;

  RETURN COALESCE(NEW,OLD);
END $$;

DROP TRIGGER IF EXISTS trg_cm_question_revision_status ON cm_question_revision;
CREATE TRIGGER trg_cm_question_revision_status
    BEFORE UPDATE OF status ON cm_question_revision
    FOR EACH ROW
    EXECUTE FUNCTION cm_guard_status_transition(
        'draft>pending_review,pending_review>approved,pending_review>rejected,rejected>draft,approved>published,published>superseded,published>offline'
    );

COMMIT;
