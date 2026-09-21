-- Durable, revision-scoped AI grading support for CASE and ESSAY questions.

CREATE TABLE cm_question_ai_rubric (
    id bigint NOT NULL,
    question_revision_id bigint NOT NULL,
    context_hash varchar(64) NOT NULL,
    rubric_data jsonb NOT NULL,
    generator_model varchar(200) NOT NULL,
    generator_prompt_version varchar(100) NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_question_ai_rubric PRIMARY KEY (id),
    CONSTRAINT uk_cm_question_ai_rubric_revision UNIQUE (question_revision_id)
);

CREATE TABLE cm_ai_grading_task (
    id bigint NOT NULL,
    task_key varchar(200) NOT NULL,
    task_type varchar(40) NOT NULL,
    status varchar(30) DEFAULT 'pending' NOT NULL,
    question_revision_id bigint,
    session_question_id bigint,
    attempt_id bigint,
    payload jsonb NOT NULL,
    result jsonb,
    error_code varchar(100),
    retry_count integer DEFAULT 0 NOT NULL,
    next_retry_time timestamptz DEFAULT now() NOT NULL,
    claimed_time timestamptz,
    completed_time timestamptz,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_ai_grading_task PRIMARY KEY (id),
    CONSTRAINT uk_cm_ai_grading_task_key UNIQUE (task_key),
    CONSTRAINT ck_cm_ai_grading_task_type CHECK (task_type IN ('RUBRIC_GENERATION', 'ANSWER_GRADING')),
    CONSTRAINT ck_cm_ai_grading_task_status CHECK (status IN ('pending', 'processing', 'succeeded', 'failed')),
    CONSTRAINT ck_cm_ai_grading_task_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_cm_ai_grading_task_dispatch
    ON cm_ai_grading_task(status, next_retry_time, create_time)
    WHERE status IN ('pending', 'processing');

ALTER TABLE cm_session_question
    ADD COLUMN IF NOT EXISTS ai_rubric_snapshot jsonb;

ALTER TABLE cm_attempt_answer
    ADD COLUMN IF NOT EXISTS grading_result jsonb;

COMMENT ON COLUMN cm_session_question.ai_rubric_snapshot IS
    'Immutable AI rubric copied from the question revision when a subjective answer is first graded';
COMMENT ON COLUMN cm_attempt_answer.grading_result IS
    'Versioned structured grading result; never contains provider credentials or request headers';
