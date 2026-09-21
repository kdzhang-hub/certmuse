-- Remove author-configured scoring points and obsolete scoring-point snapshots.
\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
    snapshot_has_items boolean;
BEGIN
    IF EXISTS (SELECT 1 FROM cm_question_scoring_point) THEN
        RAISE EXCEPTION 'Cannot remove question scoring points: cm_question_scoring_point is not empty';
    END IF;
    IF EXISTS (SELECT 1 FROM cm_scoring_point_knowledge) THEN
        RAISE EXCEPTION 'Cannot remove question scoring points: cm_scoring_point_knowledge is not empty';
    END IF;
    IF EXISTS (SELECT 1 FROM cm_attempt_scoring_point) THEN
        RAISE EXCEPTION 'Cannot remove question scoring points: cm_attempt_scoring_point is not empty';
    END IF;
    IF EXISTS (SELECT 1 FROM cm_scoring_review) THEN
        RAISE EXCEPTION 'Cannot remove question scoring points: cm_scoring_review is not empty';
    END IF;
    IF EXISTS (
        SELECT 1 FROM cm_session_question
        WHERE jsonb_typeof(scoring_point_snapshot->'items') = 'array'
          AND jsonb_array_length(scoring_point_snapshot->'items') > 0
    ) THEN
        RAISE EXCEPTION 'Cannot remove scoring-point snapshots: cm_session_question contains non-empty items';
    END IF;
    IF EXISTS (
        SELECT 1 FROM cm_learning_evidence
        WHERE jsonb_typeof(scoring_point_snapshot->'items') = 'array'
          AND jsonb_array_length(scoring_point_snapshot->'items') > 0
    ) THEN
        RAISE EXCEPTION 'Cannot remove scoring-point snapshots: cm_learning_evidence contains non-empty items';
    END IF;
    IF to_regclass('public.cm_task_question') IS NOT NULL THEN
        EXECUTE 'SELECT EXISTS (
            SELECT 1 FROM cm_task_question
            WHERE jsonb_typeof(scoring_point_snapshot->''items'') = ''array''
              AND jsonb_array_length(scoring_point_snapshot->''items'') > 0
        )' INTO snapshot_has_items;
        IF snapshot_has_items THEN
            RAISE EXCEPTION 'Cannot remove scoring-point snapshots: cm_task_question contains non-empty items';
        END IF;
    END IF;
END $$;

ALTER TABLE cm_session_question DROP CONSTRAINT IF EXISTS ck_cm_session_question_scoring_point_snapshot_schema;
ALTER TABLE cm_learning_evidence DROP CONSTRAINT IF EXISTS ck_cm_learning_evidence_scoring_point_snapshot_schema;
ALTER TABLE IF EXISTS cm_task_question DROP CONSTRAINT IF EXISTS ck_cm_task_question_scoring_schema;

ALTER TABLE cm_session_question DROP COLUMN scoring_point_snapshot;
ALTER TABLE cm_learning_evidence DROP COLUMN scoring_point_snapshot;
ALTER TABLE IF EXISTS cm_task_question DROP COLUMN scoring_point_snapshot;

DROP TABLE cm_scoring_review;
DROP TABLE cm_attempt_scoring_point;
DROP TABLE cm_scoring_point_knowledge;
DROP TABLE cm_question_scoring_point;

COMMIT;
