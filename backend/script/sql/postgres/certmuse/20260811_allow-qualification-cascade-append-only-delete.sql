\set ON_ERROR_STOP on
BEGIN;

-- Append-only rows cannot be mutated independently. They may only be removed
-- as descendants of an explicitly marked qualification aggregate deletion.
CREATE OR REPLACE FUNCTION cm_deny_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE'
     AND current_setting('certmuse.qualification_cascade_delete', true) = 'on' THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END $$;

COMMIT;
