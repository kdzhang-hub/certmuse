\set ON_ERROR_STOP on
BEGIN;

-- A qualification/version deletion is an explicit administrative purge. Convert every
-- current foreign-key edge below the qualification, syllabus-version, and subject roots
-- to database cascades so the operation cannot leave relational orphan records.
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
              AND constraint_item.confrelid IN (
                  'public.cm_exam_certification'::regclass,
                  'public.cm_syllabus_version'::regclass,
                  'public.cm_exam_subject'::regclass
              )
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
    LOOP
        IF constraint_row.definition ~* ' ON DELETE CASCADE' THEN
            rewritten_definition := constraint_row.definition;
        ELSE
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
        END IF;

        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', constraint_row.table_name, constraint_row.conname);
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', constraint_row.table_name, constraint_row.conname, rewritten_definition);
    END LOOP;
END $$;

COMMIT;
