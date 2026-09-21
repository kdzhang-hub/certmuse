-- Pausable formal-exam timing and replaceable grading provenance.
\set ON_ERROR_STOP on
BEGIN;

ALTER TABLE cm_attempt_answer
    ADD COLUMN IF NOT EXISTS grading_source varchar(30),
    ADD COLUMN IF NOT EXISTS grading_revision_no integer NOT NULL DEFAULT 0;

ALTER TABLE cm_attempt_answer DROP CONSTRAINT IF EXISTS ck_cm_attempt_answer_grading_revision_no;
ALTER TABLE cm_attempt_answer ADD CONSTRAINT ck_cm_attempt_answer_grading_revision_no
    CHECK (grading_revision_no >= 0) NOT VALID;

CREATE TABLE IF NOT EXISTS cm_formal_exam_timer_lease (
    session_id bigint NOT NULL,
    question_order integer NOT NULL,
    attempt_id bigint NOT NULL,
    lease_id varchar(36) NOT NULL,
    started_at timestamptz NOT NULL DEFAULT now(),
    last_heartbeat_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_cm_formal_exam_timer_lease PRIMARY KEY (session_id),
    CONSTRAINT fk_cm_formal_exam_timer_lease_session FOREIGN KEY (session_id)
        REFERENCES cm_learning_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_cm_formal_exam_timer_lease_attempt FOREIGN KEY (attempt_id)
        REFERENCES cm_question_attempt(id) ON DELETE CASCADE,
    CONSTRAINT ck_cm_formal_exam_timer_lease_question_order CHECK (question_order > 0)
);
CREATE INDEX IF NOT EXISTS idx_cm_formal_exam_timer_lease_attempt
    ON cm_formal_exam_timer_lease(attempt_id);

ALTER TABLE cm_learning_session DROP CONSTRAINT IF EXISTS ck_cm_learning_session_simulation_fields;
ALTER TABLE cm_learning_session ADD CONSTRAINT ck_cm_learning_session_simulation_fields CHECK (
    session_type <> 'simulation' OR (
        collection_revision_id IS NOT NULL
        AND timing_mode IN ('standard', 'pausable')
        AND duration_seconds_snapshot > 0
        AND formal_attempt_no >= 1
        AND started_time IS NOT NULL
        AND deadline_time > started_time
    )
) NOT VALID;

ALTER TABLE cm_learning_session DROP CONSTRAINT IF EXISTS ck_cm_learning_session_past_paper_exam_timing;
ALTER TABLE cm_learning_session ADD CONSTRAINT ck_cm_learning_session_past_paper_exam_timing CHECK (
    session_type <> 'past_paper_exam' OR (
        timing_mode IN ('standard', 'pausable')
        AND duration_seconds_snapshot > 0
        AND started_time IS NOT NULL
        AND deadline_time > started_time
        AND formal_attempt_no > 0
    )
) NOT VALID;

ALTER TABLE cm_attempt_answer VALIDATE CONSTRAINT ck_cm_attempt_answer_grading_revision_no;
ALTER TABLE cm_learning_session VALIDATE CONSTRAINT ck_cm_learning_session_simulation_fields;
ALTER TABLE cm_learning_session VALIDATE CONSTRAINT ck_cm_learning_session_past_paper_exam_timing;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='certmuse_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON cm_formal_exam_timer_lease TO certmuse_app;
    END IF;
END $$;

COMMIT;
