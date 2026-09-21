\set ON_ERROR_STOP on
BEGIN;

-- Rejected revisions remain content-immutable, but may be resubmitted without
-- an intermediate draft transition after review feedback has been addressed.
CREATE OR REPLACE FUNCTION cm_guard_question_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE old_content jsonb; new_content jsonb;
BEGIN
  IF TG_OP='DELETE' AND OLD.status <> 'draft' THEN
    RAISE EXCEPTION 'non-draft question revision is immutable';
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

COMMIT;
