-- Reconcile the known production gaps without replaying historical migrations.
-- This migration is safe to run repeatedly against a partially updated schema.
\set ON_ERROR_STOP on
BEGIN;

-- Paper-import persistence always writes these attributes, including NULL values
-- for simulation papers. All four columns must therefore exist before imports run.
ALTER TABLE cm_paper_import
    ADD COLUMN IF NOT EXISTS exam_year integer,
    ADD COLUMN IF NOT EXISTS exam_month smallint,
    ADD COLUMN IF NOT EXISTS paper_type_code varchar(50),
    ADD COLUMN IF NOT EXISTS paper_type_name varchar(100);

-- Restore the referential integrity that was omitted when the AI-grading tables
-- reached production. Existing rows were audited for orphans before this release.
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

-- Grant only the operations used by the corresponding MyBatis mappers. UPDATE
-- on the two lease/conversation tables is also required by SELECT ... FOR UPDATE.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'certmuse_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE
            cm_ai_conversation,
            cm_ai_message,
            cm_ai_grading_task,
            cm_diagnostic_timer_lease,
            cm_past_paper_draft,
            cm_task_replenishment_batch
            TO certmuse_app;

        GRANT DELETE ON TABLE cm_diagnostic_timer_lease TO certmuse_app;

        GRANT SELECT, INSERT ON TABLE
            cm_question_ai_rubric,
            cm_past_paper_answer_disclosure,
            cm_task_question
            TO certmuse_app;
    END IF;
END $$;

COMMIT;
