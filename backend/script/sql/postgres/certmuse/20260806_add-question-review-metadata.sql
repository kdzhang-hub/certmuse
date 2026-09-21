-- Add the review metadata used by the question review workflow.
ALTER TABLE cm_question_revision
    ADD COLUMN IF NOT EXISTS reviewed_by bigint,
    ADD COLUMN IF NOT EXISTS reviewed_time timestamptz,
    ADD COLUMN IF NOT EXISTS review_opinion text,
    ADD COLUMN IF NOT EXISTS published_by bigint,
    ADD COLUMN IF NOT EXISTS published_time timestamptz;
