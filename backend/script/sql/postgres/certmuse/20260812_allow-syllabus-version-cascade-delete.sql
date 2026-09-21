\set ON_ERROR_STOP on
BEGIN;

-- A syllabus-version delete is an explicitly destructive aggregate operation.
-- Reconcile descendants added after the earlier qualification finalizers so its
-- content graph (knowledge, textbooks, imports and relations) is removable.
DO $$
DECLARE
    constraint_row record;
    rewritten_definition text;
BEGIN
    FOR constraint_row IN
        WITH RECURSIVE descendant_constraints AS (
            SELECT constraint_item.oid, constraint_item.conrelid
            FROM pg_constraint constraint_item
            WHERE constraint_item.contype = 'f'
              AND constraint_item.confrelid = 'public.cm_syllabus_version'::regclass
            UNION
            SELECT child_constraint.oid, child_constraint.conrelid
            FROM pg_constraint child_constraint
            JOIN descendant_constraints parent_constraint
              ON child_constraint.confrelid = parent_constraint.conrelid
            WHERE child_constraint.contype = 'f'
        )
        SELECT constraint_item.conname,
               constraint_item.conrelid::regclass AS table_name,
               pg_get_constraintdef(constraint_item.oid) AS definition
        FROM descendant_constraints
        JOIN pg_constraint constraint_item ON constraint_item.oid = descendant_constraints.oid
        WHERE constraint_item.confdeltype <> 'c'
    LOOP
        rewritten_definition := regexp_replace(
            constraint_row.definition,
            ' ON DELETE (NO ACTION|RESTRICT|SET NULL|SET DEFAULT)',
            ' ON DELETE CASCADE',
            1,
            0,
            'i'
        );
        IF rewritten_definition = constraint_row.definition THEN
            rewritten_definition := constraint_row.definition || ' ON DELETE CASCADE';
        END IF;

        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', constraint_row.table_name, constraint_row.conname);
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', constraint_row.table_name, constraint_row.conname, rewritten_definition);
    END LOOP;
END $$;

-- Keep revisions and audit rows immutable outside an explicit aggregate delete.
CREATE OR REPLACE FUNCTION cm_guard_question_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE old_content jsonb; new_content jsonb;
BEGIN
  IF TG_OP='DELETE'
     AND OLD.status <> 'draft'
     AND current_setting('certmuse.qualification_cascade_delete', true) IS DISTINCT FROM 'on' THEN
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

CREATE OR REPLACE FUNCTION cm_deny_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE'
     AND current_setting('certmuse.qualification_cascade_delete', true) = 'on' THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END $$;

COMMIT;
