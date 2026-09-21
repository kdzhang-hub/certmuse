\set ON_ERROR_STOP on
BEGIN;

ALTER TABLE cm_collection
    ADD COLUMN IF NOT EXISTS exam_year integer,
    ADD COLUMN IF NOT EXISTS exam_month smallint,
    ADD COLUMN IF NOT EXISTS paper_type_code varchar(50),
    ADD COLUMN IF NOT EXISTS paper_type_name varchar(100);

ALTER TABLE cm_collection DROP CONSTRAINT IF EXISTS ck_cm_collection_collection_type;
ALTER TABLE cm_collection ADD CONSTRAINT ck_cm_collection_collection_type
    CHECK (collection_type IN ('FIRST_DIAGNOSTIC', 'PRACTICE', 'SIMULATION', 'PAST_PAPER')) NOT VALID;
ALTER TABLE cm_collection ADD CONSTRAINT ck_cm_collection_past_paper_metadata
    CHECK (collection_type <> 'PAST_PAPER' OR (
        exam_year between 1990 and 2100
        AND exam_month in (5, 11)
        AND btrim(coalesce(paper_type_code, '')) <> ''
        AND btrim(coalesce(paper_type_name, '')) <> ''
    )) NOT VALID;

ALTER TABLE cm_collection_revision DROP CONSTRAINT IF EXISTS ck_cm_collection_revision_collection_type;
ALTER TABLE cm_collection_revision ADD CONSTRAINT ck_cm_collection_revision_collection_type
    CHECK (collection_type IN ('FIRST_DIAGNOSTIC', 'PRACTICE', 'SIMULATION', 'PAST_PAPER')) NOT VALID;

ALTER TABLE cm_paper_import DROP CONSTRAINT IF EXISTS ck_cm_paper_import_collection_type;
ALTER TABLE cm_paper_import ADD CONSTRAINT ck_cm_paper_import_collection_type
    CHECK (collection_type IN ('FIRST_DIAGNOSTIC', 'PRACTICE', 'SIMULATION', 'PAST_PAPER')) NOT VALID;

ALTER TABLE cm_learning_session
    ADD COLUMN IF NOT EXISTS timing_mode varchar(20),
    ADD COLUMN IF NOT EXISTS duration_seconds_snapshot integer,
    ADD COLUMN IF NOT EXISTS deadline_time timestamptz,
    ADD COLUMN IF NOT EXISTS formal_attempt_no integer;
ALTER TABLE cm_question_attempt ADD COLUMN IF NOT EXISTS profile_applied boolean NOT NULL DEFAULT true;

ALTER TABLE cm_learning_session DROP CONSTRAINT IF EXISTS ck_cm_learning_session_session_type;
ALTER TABLE cm_learning_session ADD CONSTRAINT ck_cm_learning_session_session_type
    CHECK (session_type IN ('initial_diagnosis', 'daily_task', 'task_test', 'self_practice', 'simulation',
        'correction', 'independent_verification', 'simulation_verification', 'past_paper_practice',
        'past_paper_exam')) NOT VALID;
ALTER TABLE cm_learning_session ADD CONSTRAINT ck_cm_learning_session_past_paper_snapshot
    CHECK (session_type NOT IN ('past_paper_practice', 'past_paper_exam') OR collection_revision_id IS NOT NULL) NOT VALID;
ALTER TABLE cm_learning_session ADD CONSTRAINT ck_cm_learning_session_past_paper_exam_timing
    CHECK (session_type <> 'past_paper_exam' OR (
        timing_mode = 'standard' AND duration_seconds_snapshot > 0 AND started_time IS NOT NULL
        AND deadline_time = started_time + make_interval(secs => duration_seconds_snapshot)
        AND formal_attempt_no > 0
    )) NOT VALID;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_learning_session_active_past_paper_practice
    ON cm_learning_session(user_id, goal_id, collection_revision_id)
    WHERE session_type = 'past_paper_practice' AND status IN ('created', 'in_progress');
CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_learning_session_active_past_paper_exam
    ON cm_learning_session(user_id, goal_id, collection_revision_id)
    WHERE session_type = 'past_paper_exam' AND status IN ('created', 'in_progress', 'submitted', 'settling');
CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_learning_session_past_paper_exam_attempt
    ON cm_learning_session(user_id, goal_id, collection_revision_id, formal_attempt_no)
    WHERE session_type = 'past_paper_exam';
CREATE INDEX IF NOT EXISTS idx_cm_learning_session_past_paper_deadline
    ON cm_learning_session(deadline_time, id)
    WHERE session_type = 'past_paper_exam' AND status IN ('created', 'in_progress');

CREATE TABLE IF NOT EXISTS cm_past_paper_draft (
    session_question_id bigint NOT NULL,
    user_id bigint NOT NULL,
    answer_data jsonb NOT NULL,
    row_version bigint NOT NULL DEFAULT 0,
    saved_time timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_cm_past_paper_draft PRIMARY KEY (session_question_id),
    CONSTRAINT fk_cm_past_paper_draft_session_question FOREIGN KEY (session_question_id)
        REFERENCES cm_session_question(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cm_past_paper_draft_answer CHECK (jsonb_typeof(answer_data) = 'object')
);

CREATE TABLE IF NOT EXISTS cm_past_paper_answer_disclosure (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    question_id bigint NOT NULL,
    question_revision_id bigint NOT NULL,
    collection_revision_id bigint NOT NULL,
    request_id varchar(100) NOT NULL,
    disclosed_time timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_cm_past_paper_answer_disclosure PRIMARY KEY (id),
    CONSTRAINT uk_cm_past_paper_answer_disclosure_request UNIQUE (request_id),
    CONSTRAINT fk_cm_past_paper_answer_disclosure_question FOREIGN KEY (question_id) REFERENCES cm_question(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_past_paper_answer_disclosure_revision FOREIGN KEY (question_revision_id) REFERENCES cm_question_revision(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_past_paper_answer_disclosure_collection_revision FOREIGN KEY (collection_revision_id) REFERENCES cm_collection_revision(id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_cm_past_paper_answer_disclosure_recent
    ON cm_past_paper_answer_disclosure(user_id, question_id, disclosed_time DESC);

ALTER TABLE cm_collection VALIDATE CONSTRAINT ck_cm_collection_collection_type;
ALTER TABLE cm_collection VALIDATE CONSTRAINT ck_cm_collection_past_paper_metadata;
ALTER TABLE cm_collection_revision VALIDATE CONSTRAINT ck_cm_collection_revision_collection_type;
ALTER TABLE cm_paper_import VALIDATE CONSTRAINT ck_cm_paper_import_collection_type;
ALTER TABLE cm_learning_session VALIDATE CONSTRAINT ck_cm_learning_session_session_type;
ALTER TABLE cm_learning_session VALIDATE CONSTRAINT ck_cm_learning_session_past_paper_snapshot;
ALTER TABLE cm_learning_session VALIDATE CONSTRAINT ck_cm_learning_session_past_paper_exam_timing;
COMMIT;
