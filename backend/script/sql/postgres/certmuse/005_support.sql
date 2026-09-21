-- CertMuse 一期数据库：平台支撑域
-- 前置：004_*.sql
-- 执行账号：本地数据库对象所有者（不得使用应用账号）
-- 事务：本文件整体事务；psql必须启用ON_ERROR_STOP
-- 失败处理：事务回滚，修正后从本文件重新执行
-- 验证：文件末尾检查本批表数量
\set ON_ERROR_STOP on
BEGIN;

CREATE TABLE cm_idempotency_record (
    id bigint NOT NULL,
    action_code varchar(100) NOT NULL,
    request_id varchar(100) NOT NULL,
    payload_hash varchar(64) NOT NULL,
    status varchar(20) DEFAULT 'processing' NOT NULL,
    resource_type varchar(100),
    resource_id bigint,
    response_status integer,
    response_body jsonb,
    expires_time timestamptz NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_idempotency_record PRIMARY KEY (id),
    CONSTRAINT ck_cm_idempotency_record_response_status CHECK (response_status >= 100 AND response_status <= 599)
);

CREATE TABLE cm_async_job (
    id bigint NOT NULL,
    job_type varchar(50) NOT NULL,
    business_key varchar(200) NOT NULL,
    payload jsonb NOT NULL,
    status varchar(20) DEFAULT 'queued' NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 3 NOT NULL,
    next_run_time timestamptz DEFAULT now() NOT NULL,
    started_time timestamptz,
    finished_time timestamptz,
    last_error text,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_async_job PRIMARY KEY (id),
    CONSTRAINT ck_cm_async_job_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_cm_async_job_max_attempts CHECK (max_attempts > 0)
);

CREATE TABLE cm_learning_event (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    goal_id bigint NOT NULL,
    event_type varchar(50) NOT NULL,
    source_type varchar(50) NOT NULL,
    source_id bigint,
    event_data jsonb NOT NULL,
    request_id varchar(100) NOT NULL,
    occurred_time timestamptz NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_learning_event PRIMARY KEY (id),
    CONSTRAINT uk_cm_learning_event_request_id UNIQUE (request_id)
);

CREATE TABLE cm_business_audit_log (
    id bigint NOT NULL,
    operator_id bigint,
    domain_code varchar(50) NOT NULL,
    action_code varchar(50) NOT NULL,
    target_type varchar(100) NOT NULL,
    target_id bigint NOT NULL,
    before_data jsonb,
    after_data jsonb,
    ip_address inet,
    trace_id varchar(100) NOT NULL,
    occurred_time timestamptz DEFAULT now() NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_business_audit_log PRIMARY KEY (id)
);

CREATE TABLE cm_rule_hit (
    id bigint NOT NULL,
    rule_version_id bigint NOT NULL,
    rule_code varchar(100) NOT NULL,
    user_id bigint,
    goal_id bigint,
    session_id bigint,
    target_type varchar(100) NOT NULL,
    target_id bigint NOT NULL,
    input_snapshot jsonb NOT NULL,
    output_snapshot jsonb NOT NULL,
    outcome varchar(30) NOT NULL,
    request_id varchar(100) NOT NULL,
    occurred_time timestamptz DEFAULT now() NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_rule_hit PRIMARY KEY (id)
);

CREATE TABLE cm_exception_event (
    id bigint NOT NULL,
    exception_code varchar(100) NOT NULL,
    source_type varchar(100) NOT NULL,
    source_id bigint NOT NULL,
    severity varchar(20) NOT NULL,
    detail_data jsonb NOT NULL,
    status varchar(20) DEFAULT 'open' NOT NULL,
    first_occurred_time timestamptz NOT NULL,
    last_occurred_time timestamptz NOT NULL,
    occurrence_count integer DEFAULT 1 NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_exception_event PRIMARY KEY (id)
);

CREATE TABLE cm_exception_action (
    id bigint NOT NULL,
    exception_event_id bigint NOT NULL,
    action_type varchar(50) NOT NULL,
    result_status varchar(20) NOT NULL,
    result_data jsonb NOT NULL,
    operator_id bigint,
    request_id varchar(100) NOT NULL,
    action_time timestamptz DEFAULT now() NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_exception_action PRIMARY KEY (id),
    CONSTRAINT uk_cm_exception_action_request_id UNIQUE (request_id)
);

CREATE TABLE cm_export_job (
    id bigint NOT NULL,
    operator_id bigint NOT NULL,
    export_type varchar(50) NOT NULL,
    filter_data jsonb NOT NULL,
    desensitisation_schema jsonb NOT NULL,
    status varchar(20) DEFAULT 'queued' NOT NULL,
    row_count integer DEFAULT 0 NOT NULL,
    file_path text,
    expires_time timestamptz,
    request_id varchar(100) NOT NULL,
    failure_reason text,
    started_time timestamptz,
    finished_time timestamptz,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_export_job PRIMARY KEY (id),
    CONSTRAINT ck_cm_export_job_row_count CHECK (row_count >= 0),
    CONSTRAINT uk_cm_export_job_request_id UNIQUE (request_id)
);

DO $$ BEGIN IF (SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name = ANY (ARRAY['cm_idempotency_record','cm_async_job','cm_learning_event','cm_business_audit_log','cm_rule_hit','cm_exception_event','cm_exception_action','cm_export_job'])) <> 8 THEN RAISE EXCEPTION 'CertMuse 005 table count mismatch'; END IF; END $$;
COMMIT;
