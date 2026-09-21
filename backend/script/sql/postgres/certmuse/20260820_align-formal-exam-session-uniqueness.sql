-- Keep one active session per formal-exam type while allowing diagnostic, past-paper and simulation concurrently.
\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM cm_learning_session
         WHERE session_type = 'past_paper_exam'
           AND status IN ('created', 'in_progress', 'submitted', 'settling')
         GROUP BY user_id, goal_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'multiple active past-paper exams must be resolved before applying this migration';
    END IF;
END $$;

DROP INDEX IF EXISTS uk_cm_learning_session_active_past_paper_exam;
CREATE UNIQUE INDEX uk_cm_learning_session_active_past_paper_exam
    ON cm_learning_session(user_id, goal_id)
    WHERE session_type = 'past_paper_exam'
      AND status IN ('created', 'in_progress', 'submitted', 'settling');

COMMIT;
