-- CertMuse 一期数据库：学习闭环域
-- 前置：002_*.sql
-- 执行账号：本地数据库对象所有者（不得使用应用账号）
-- 事务：本文件整体事务；psql必须启用ON_ERROR_STOP
-- 失败处理：事务回滚，修正后从本文件重新执行
-- 验证：文件末尾检查本批表数量
\set ON_ERROR_STOP on
BEGIN;

CREATE TABLE cm_user_goal (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    certification_id bigint NOT NULL,
    syllabus_version_id bigint NOT NULL,
    daily_minutes integer NOT NULL,
    status varchar(20) DEFAULT 'active' NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_user_goal PRIMARY KEY (id),
    CONSTRAINT ck_cm_user_goal_daily_minutes CHECK (daily_minutes > 0),
    CONSTRAINT ck_cm_user_goal_row_version CHECK (row_version >= 0)
);

CREATE TABLE cm_user_goal_change (
    id bigint NOT NULL,
    goal_id bigint NOT NULL,
    user_id bigint NOT NULL,
    change_type varchar(30) NOT NULL,
    before_data jsonb,
    after_data jsonb NOT NULL,
    reason text,
    request_id varchar(100) NOT NULL,
    operator_id bigint NOT NULL,
    change_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_user_goal_change PRIMARY KEY (id),
    CONSTRAINT uk_cm_user_goal_change_request_id UNIQUE (request_id)
);

CREATE TABLE cm_learning_session (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    goal_id bigint NOT NULL,
    exam_subject_id bigint,
    session_type varchar(30) NOT NULL,
    status varchar(20) DEFAULT 'created' NOT NULL,
    rule_version_id bigint NOT NULL,
    request_id varchar(100) NOT NULL,
    trace_id varchar(100),
    started_time timestamptz,
    submitted_time timestamptz,
    settled_time timestamptz,
    invalid_reason text,
    status_changed_by bigint,
    status_changed_time timestamptz DEFAULT now() NOT NULL,
    status_reason text,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_learning_session PRIMARY KEY (id),
    CONSTRAINT uk_cm_learning_session_request_id UNIQUE (request_id)
);

CREATE TABLE cm_session_question (
    id bigint NOT NULL,
    session_id bigint NOT NULL,
    question_id bigint NOT NULL,
    question_revision_id bigint NOT NULL,
    exam_subject_id bigint NOT NULL,
    question_order integer NOT NULL,
    evidence_group_key varchar(100) NOT NULL,
    difficulty_snapshot varchar(20) NOT NULL,
    estimated_seconds_snapshot integer,
    presentation_snapshot jsonb NOT NULL,
    grading_snapshot jsonb NOT NULL,
    knowledge_snapshot jsonb DEFAULT '{"schema_version":"1.0","items":[]}'::jsonb NOT NULL,
    scoring_point_snapshot jsonb DEFAULT '{"schema_version":"2.0","snapshot_type":"session","items":[]}'::jsonb NOT NULL,
    is_first_encounter boolean DEFAULT false NOT NULL,
    hint_allowed boolean DEFAULT true NOT NULL,
    is_predeclared_verification boolean DEFAULT false NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_session_question PRIMARY KEY (id),
    CONSTRAINT ck_cm_session_question_difficulty_snapshot CHECK (difficulty_snapshot IN ('easy','medium','hard')),
    CONSTRAINT ck_cm_session_question_estimated_seconds_snapshot CHECK (estimated_seconds_snapshot > 0)
);

CREATE TABLE cm_question_attempt (
    id bigint NOT NULL,
    session_question_id bigint NOT NULL,
    user_id bigint NOT NULL,
    attempt_no integer DEFAULT 1 NOT NULL,
    status varchar(20) DEFAULT 'in_progress' NOT NULL,
    create_request_id varchar(100) NOT NULL,
    submit_request_id varchar(100),
    started_time timestamptz DEFAULT now() NOT NULL,
    presented_at timestamptz,
    submitted_time timestamptz,
    approved_pause_seconds integer DEFAULT 0 NOT NULL,
    client_elapsed_seconds integer,
    elapsed_seconds integer,
    timer_status varchar(20) DEFAULT 'valid' NOT NULL,
    is_blank boolean DEFAULT false NOT NULL,
    is_skipped boolean DEFAULT false NOT NULL,
    is_first_attempt boolean DEFAULT true NOT NULL,
    is_repeat_attempt boolean DEFAULT false NOT NULL,
    invalid_reason text,
    status_changed_by bigint,
    status_changed_time timestamptz DEFAULT now() NOT NULL,
    status_reason text,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_question_attempt PRIMARY KEY (id),
    CONSTRAINT uk_cm_question_attempt_create_request_id UNIQUE (create_request_id),
    CONSTRAINT uk_cm_question_attempt_submit_request_id UNIQUE (submit_request_id),
    CONSTRAINT ck_cm_question_attempt_approved_pause_seconds CHECK (approved_pause_seconds >= 0),
    CONSTRAINT ck_cm_question_attempt_client_elapsed_seconds CHECK (client_elapsed_seconds >= 0),
    CONSTRAINT ck_cm_question_attempt_elapsed_seconds CHECK (elapsed_seconds >= 0)
);

CREATE TABLE cm_attempt_answer (
    id bigint NOT NULL,
    attempt_id bigint NOT NULL,
    answer_data jsonb NOT NULL,
    score numeric(10,2),
    max_score numeric(10,2),
    score_rate numeric(12,8),
    grading_status varchar(20) DEFAULT 'pending' NOT NULL,
    graded_by bigint,
    graded_time timestamptz,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_attempt_answer PRIMARY KEY (id),
    CONSTRAINT uk_cm_attempt_answer_attempt_id UNIQUE (attempt_id),
    CONSTRAINT ck_cm_attempt_answer_score CHECK (score >= 0),
    CONSTRAINT ck_cm_attempt_answer_max_score CHECK (max_score > 0),
    CONSTRAINT ck_cm_attempt_answer_score_rate CHECK (score_rate >= 0 AND score_rate <= 1)
);

CREATE TABLE cm_attempt_scoring_point (
    id bigint NOT NULL,
    attempt_id bigint NOT NULL,
    scoring_point_id bigint NOT NULL,
    knowledge_point_id bigint NOT NULL,
    scoring_code_snapshot varchar(50) NOT NULL,
    description_snapshot text NOT NULL,
    score numeric(10,2) NOT NULL,
    max_score numeric(10,2) NOT NULL,
    score_rate numeric(12,8) NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_attempt_scoring_point PRIMARY KEY (id),
    CONSTRAINT ck_cm_attempt_scoring_point_score CHECK (score >= 0),
    CONSTRAINT ck_cm_attempt_scoring_point_max_score CHECK (max_score > 0),
    CONSTRAINT ck_cm_attempt_scoring_point_score_rate CHECK (score_rate >= 0 AND score_rate <= 1)
);

CREATE TABLE cm_hint_usage (
    id bigint NOT NULL,
    attempt_id bigint NOT NULL,
    hint_order integer NOT NULL,
    hint_type varchar(20) NOT NULL,
    content_snapshot text,
    used_time timestamptz DEFAULT now() NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_hint_usage PRIMARY KEY (id)
);

CREATE TABLE cm_error_record (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    goal_id bigint NOT NULL,
    attempt_id bigint NOT NULL,
    knowledge_point_id bigint NOT NULL,
    evidence_group_key varchar(100) NOT NULL,
    status varchar(30) DEFAULT 'unresolved' NOT NULL,
    detected_time timestamptz DEFAULT now() NOT NULL,
    resolved_time timestamptz,
    status_changed_by bigint,
    status_changed_time timestamptz DEFAULT now() NOT NULL,
    status_reason text,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_error_record PRIMARY KEY (id)
);

CREATE TABLE cm_error_reason (
    id bigint NOT NULL,
    error_record_id bigint NOT NULL,
    misconception_id bigint,
    reason_type varchar(20) NOT NULL,
    custom_reason text,
    recorded_by bigint NOT NULL,
    recorded_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_error_reason PRIMARY KEY (id)
);

CREATE TABLE cm_correction_record (
    id bigint NOT NULL,
    error_record_id bigint NOT NULL,
    correction_attempt_id bigint NOT NULL,
    status varchar(20) DEFAULT 'submitted' NOT NULL,
    score_rate numeric(12,8),
    profile_settlement_id bigint,
    request_id varchar(100) NOT NULL,
    submitted_time timestamptz DEFAULT now() NOT NULL,
    settled_time timestamptz,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_correction_record PRIMARY KEY (id),
    CONSTRAINT uk_cm_correction_record_error_record_id UNIQUE (error_record_id),
    CONSTRAINT ck_cm_correction_record_score_rate CHECK (score_rate >= 0 AND score_rate <= 1),
    CONSTRAINT uk_cm_correction_record_profile_settlement_id UNIQUE (profile_settlement_id),
    CONSTRAINT uk_cm_correction_record_request_id UNIQUE (request_id)
);

CREATE TABLE cm_verification_record (
    id bigint NOT NULL,
    correction_record_id bigint NOT NULL,
    verification_attempt_id bigint NOT NULL,
    verification_evidence_group_key varchar(100) NOT NULL,
    status varchar(20) DEFAULT 'pending' NOT NULL,
    is_predeclared boolean NOT NULL,
    is_hint_free boolean NOT NULL,
    profile_settlement_id bigint,
    failure_reason text,
    request_id varchar(100) NOT NULL,
    verified_time timestamptz,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_verification_record PRIMARY KEY (id),
    CONSTRAINT uk_cm_verification_record_profile_settlement_id UNIQUE (profile_settlement_id),
    CONSTRAINT uk_cm_verification_record_request_id UNIQUE (request_id)
);

CREATE TABLE cm_learning_task (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    goal_id bigint NOT NULL,
    exam_subject_id bigint,
    task_date date NOT NULL,
    task_type varchar(30) NOT NULL,
    estimated_minutes integer NOT NULL,
    completion_rule jsonb NOT NULL,
    profile_snapshot jsonb NOT NULL,
    rule_version_id bigint NOT NULL,
    status varchar(20) DEFAULT 'pending' NOT NULL,
    request_id varchar(100) NOT NULL,
    status_changed_by bigint,
    status_changed_time timestamptz DEFAULT now() NOT NULL,
    status_reason text,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_learning_task PRIMARY KEY (id),
    CONSTRAINT ck_cm_learning_task_estimated_minutes CHECK (estimated_minutes > 0),
    CONSTRAINT uk_cm_learning_task_request_id UNIQUE (request_id)
);

CREATE TABLE cm_task_item (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    item_order integer NOT NULL,
    item_type varchar(20) NOT NULL,
    exam_subject_id bigint,
    knowledge_point_id bigint,
    target_data jsonb NOT NULL,
    estimated_minutes integer NOT NULL,
    status varchar(20) DEFAULT 'pending' NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_task_item PRIMARY KEY (id),
    CONSTRAINT ck_cm_task_item_estimated_minutes CHECK (estimated_minutes > 0)
);

CREATE TABLE cm_task_reason (
    id bigint NOT NULL,
    task_item_id bigint NOT NULL,
    reason_code varchar(50) NOT NULL,
    reason_text text NOT NULL,
    evidence_data jsonb,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_task_reason PRIMARY KEY (id)
);

CREATE TABLE cm_task_attempt (
    id bigint NOT NULL,
    task_item_id bigint NOT NULL,
    session_id bigint,
    attempt_id bigint,
    attempt_no integer DEFAULT 1 NOT NULL,
    status varchar(20) DEFAULT 'started' NOT NULL,
    started_time timestamptz DEFAULT now() NOT NULL,
    completed_time timestamptz,
    CONSTRAINT pk_cm_task_attempt PRIMARY KEY (id)
);

CREATE TABLE cm_assessment_report (
    id bigint NOT NULL,
    session_id bigint NOT NULL,
    user_id bigint NOT NULL,
    goal_id bigint NOT NULL,
    report_type varchar(30) NOT NULL,
    total_score numeric(10,2),
    max_score numeric(10,2),
    subject_scores jsonb NOT NULL,
    profile_summary jsonb NOT NULL,
    status varchar(20) DEFAULT 'generating' NOT NULL,
    rule_version_id bigint NOT NULL,
    generated_time timestamptz,
    failure_reason text,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_assessment_report PRIMARY KEY (id),
    CONSTRAINT ck_cm_assessment_report_total_score CHECK (total_score >= 0),
    CONSTRAINT ck_cm_assessment_report_max_score CHECK (max_score > 0)
);

CREATE TABLE cm_ai_scoring_job (
    id bigint NOT NULL,
    attempt_id bigint NOT NULL,
    question_revision_id bigint NOT NULL,
    model_code varchar(100) NOT NULL,
    model_config jsonb NOT NULL,
    input_snapshot jsonb NOT NULL,
    output_snapshot jsonb,
    status varchar(20) DEFAULT 'pending' NOT NULL,
    attempt_no integer DEFAULT 1 NOT NULL,
    request_id varchar(100) NOT NULL,
    failure_reason text,
    started_time timestamptz,
    finished_time timestamptz,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_ai_scoring_job PRIMARY KEY (id),
    CONSTRAINT uk_cm_ai_scoring_job_request_id UNIQUE (request_id)
);

CREATE TABLE cm_scoring_review (
    id bigint NOT NULL,
    attempt_scoring_point_id bigint NOT NULL,
    ai_scoring_job_id bigint,
    review_no integer NOT NULL,
    reviewer_id bigint NOT NULL,
    score numeric(10,2) NOT NULL,
    opinion text NOT NULL,
    status varchar(20) DEFAULT 'draft' NOT NULL,
    submitted_time timestamptz,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_scoring_review PRIMARY KEY (id),
    CONSTRAINT ck_cm_scoring_review_score CHECK (score >= 0)
);

CREATE TABLE cm_daily_task_batch (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    goal_id bigint NOT NULL,
    task_date date NOT NULL,
    generation_key varchar(100) NOT NULL,
    status varchar(20) DEFAULT 'generating' NOT NULL,
    task_count integer DEFAULT 0 NOT NULL,
    generation_summary jsonb NOT NULL,
    failure_reason text,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_daily_task_batch PRIMARY KEY (id),
    CONSTRAINT uk_cm_daily_task_batch_generation_key UNIQUE (generation_key),
    CONSTRAINT ck_cm_daily_task_batch_task_count CHECK (task_count >= 0)
);

DO $$ BEGIN IF (SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name = ANY (ARRAY['cm_user_goal','cm_user_goal_change','cm_learning_session','cm_session_question','cm_question_attempt','cm_attempt_answer','cm_attempt_scoring_point','cm_hint_usage','cm_error_record','cm_error_reason','cm_correction_record','cm_verification_record','cm_learning_task','cm_task_item','cm_task_reason','cm_task_attempt','cm_assessment_report','cm_ai_scoring_job','cm_scoring_review','cm_daily_task_batch'])) <> 20 THEN RAISE EXCEPTION 'CertMuse 003 table count mismatch'; END IF; END $$;
COMMIT;
