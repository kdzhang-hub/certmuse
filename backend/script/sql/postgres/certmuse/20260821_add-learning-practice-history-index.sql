\set ON_ERROR_STOP on
BEGIN;

CREATE INDEX IF NOT EXISTS idx_cm_learning_session_completed_practice_history
    ON cm_learning_session(user_id, goal_id, settled_time DESC, id DESC)
    WHERE session_type IN ('self_practice', 'past_paper_practice')
      AND status = 'completed'
      AND settled_time IS NOT NULL;

COMMIT;
