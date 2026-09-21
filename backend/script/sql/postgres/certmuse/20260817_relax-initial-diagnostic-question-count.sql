\set ON_ERROR_STOP on
BEGIN;

ALTER TABLE cm_learning_session DROP CONSTRAINT IF EXISTS ck_cm_learning_session_diagnostic_position;
ALTER TABLE cm_learning_session ADD CONSTRAINT ck_cm_learning_session_diagnostic_position
    CHECK (session_type <> 'initial_diagnosis' OR last_question_order >= 1) NOT VALID;
ALTER TABLE cm_learning_session VALIDATE CONSTRAINT ck_cm_learning_session_diagnostic_position;

ALTER TABLE cm_diagnostic_timer_lease DROP CONSTRAINT IF EXISTS ck_cm_diagnostic_timer_lease_question_order;
ALTER TABLE cm_diagnostic_timer_lease ADD CONSTRAINT ck_cm_diagnostic_timer_lease_question_order
    CHECK (question_order >= 1);

COMMIT;
