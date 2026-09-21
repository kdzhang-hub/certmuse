-- Restore AI-grading foreign-key relationships without rewriting an applied migration.
\set ON_ERROR_STOP on

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'cm_question_ai_rubric'::regclass
          AND conname = 'fk_cm_question_ai_rubric_revision'
    ) THEN
        ALTER TABLE cm_question_ai_rubric
            ADD CONSTRAINT fk_cm_question_ai_rubric_revision
            FOREIGN KEY (question_revision_id)
            REFERENCES cm_question_revision(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'cm_ai_grading_task'::regclass
          AND conname = 'fk_cm_ai_grading_task_revision'
    ) THEN
        ALTER TABLE cm_ai_grading_task
            ADD CONSTRAINT fk_cm_ai_grading_task_revision
            FOREIGN KEY (question_revision_id)
            REFERENCES cm_question_revision(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'cm_ai_grading_task'::regclass
          AND conname = 'fk_cm_ai_grading_task_session_question'
    ) THEN
        ALTER TABLE cm_ai_grading_task
            ADD CONSTRAINT fk_cm_ai_grading_task_session_question
            FOREIGN KEY (session_question_id)
            REFERENCES cm_session_question(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'cm_ai_grading_task'::regclass
          AND conname = 'fk_cm_ai_grading_task_attempt'
    ) THEN
        ALTER TABLE cm_ai_grading_task
            ADD CONSTRAINT fk_cm_ai_grading_task_attempt
            FOREIGN KEY (attempt_id)
            REFERENCES cm_question_attempt(id) ON DELETE CASCADE;
    END IF;
END $$;
