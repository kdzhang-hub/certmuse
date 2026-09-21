\set ON_ERROR_STOP on
BEGIN;

CREATE TABLE IF NOT EXISTS cm_diagnostic_timer_lease (
    session_id bigint NOT NULL,
    question_order integer NOT NULL,
    attempt_id bigint NOT NULL,
    lease_id varchar(36) NOT NULL,
    started_at timestamptz NOT NULL DEFAULT now(),
    last_heartbeat_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_cm_diagnostic_timer_lease PRIMARY KEY (session_id),
    CONSTRAINT ck_cm_diagnostic_timer_lease_question_order CHECK (question_order BETWEEN 1 AND 50)
);

CREATE INDEX IF NOT EXISTS idx_cm_diagnostic_timer_lease_attempt ON cm_diagnostic_timer_lease(attempt_id);
ALTER TABLE cm_learning_evidence DROP CONSTRAINT IF EXISTS ck_cm_learning_evidence_combined_coefficient;
ALTER TABLE cm_learning_evidence ADD CONSTRAINT ck_cm_learning_evidence_combined_coefficient
    CHECK (combined_coefficient >= 0 AND combined_coefficient <= 1.60);
ALTER TABLE cm_diagnostic_timer_lease DROP CONSTRAINT IF EXISTS fk_cm_diagnostic_timer_lease_session;
ALTER TABLE cm_diagnostic_timer_lease ADD CONSTRAINT fk_cm_diagnostic_timer_lease_session
    FOREIGN KEY (session_id) REFERENCES cm_learning_session(id) ON DELETE CASCADE;
ALTER TABLE cm_diagnostic_timer_lease DROP CONSTRAINT IF EXISTS fk_cm_diagnostic_timer_lease_attempt;
ALTER TABLE cm_diagnostic_timer_lease ADD CONSTRAINT fk_cm_diagnostic_timer_lease_attempt
    FOREIGN KEY (attempt_id) REFERENCES cm_question_attempt(id) ON DELETE CASCADE;

COMMIT;
