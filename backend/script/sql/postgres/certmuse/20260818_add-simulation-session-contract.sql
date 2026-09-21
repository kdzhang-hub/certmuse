-- U13 simulation display and formal-session persistence prerequisites.
\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
    simulation_count bigint;
BEGIN
    SELECT count(*) INTO simulation_count
      FROM cm_learning_session
     WHERE session_type = 'simulation';
    IF simulation_count > 0 THEN
        RAISE EXCEPTION
            'U13 migration requires explicit remediation of % existing simulation sessions',
            simulation_count;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'cm_learning_session'::regclass
           AND conname IN (
               'fk_cm_learning_session_collection_revision',
               'ck_cm_learning_session_simulation_fields'
           )
    ) OR EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname = 'public'
           AND indexname IN (
               'uk_cm_learning_session_active_simulation_goal',
               'uk_cm_learning_session_simulation_attempt'
           )
    ) THEN
        RAISE EXCEPTION 'U13 simulation session constraints or indexes already exist';
    END IF;
END $$;

ALTER TABLE cm_learning_session
    ADD COLUMN IF NOT EXISTS timing_mode varchar(20),
    ADD COLUMN IF NOT EXISTS duration_seconds_snapshot integer,
    ADD COLUMN IF NOT EXISTS deadline_time timestamptz,
    ADD COLUMN IF NOT EXISTS formal_attempt_no integer;

ALTER TABLE cm_learning_session
    ADD CONSTRAINT fk_cm_learning_session_collection_revision
    FOREIGN KEY (collection_revision_id)
    REFERENCES cm_collection_revision(id) ON DELETE RESTRICT;

ALTER TABLE cm_learning_session
    ADD CONSTRAINT ck_cm_learning_session_simulation_fields CHECK (
        session_type <> 'simulation' OR (
            collection_revision_id IS NOT NULL
            AND timing_mode = 'standard'
            AND duration_seconds_snapshot > 0
            AND formal_attempt_no >= 1
            AND started_time IS NOT NULL
            AND deadline_time > started_time
        )
    ) NOT VALID;

ALTER TABLE cm_learning_session
    VALIDATE CONSTRAINT ck_cm_learning_session_simulation_fields;

CREATE UNIQUE INDEX uk_cm_learning_session_active_simulation_goal
    ON cm_learning_session(user_id, goal_id)
    WHERE session_type = 'simulation'
      AND status IN ('created', 'in_progress', 'submitted', 'settling');

CREATE UNIQUE INDEX uk_cm_learning_session_simulation_attempt
    ON cm_learning_session(user_id, goal_id, collection_revision_id, formal_attempt_no)
    WHERE session_type = 'simulation';

COMMIT;
