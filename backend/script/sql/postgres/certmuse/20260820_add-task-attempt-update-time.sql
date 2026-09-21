-- Restore the audit timestamp expected by the U11 daily-task completion updates.
\set ON_ERROR_STOP on
BEGIN;

ALTER TABLE cm_task_attempt
    ADD COLUMN update_time timestamptz NOT NULL DEFAULT now();

COMMIT;
