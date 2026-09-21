-- CertMuse 一期数据库：内容生产与发布域
-- 前置：001_*.sql
-- 执行账号：本地数据库对象所有者（不得使用应用账号）
-- 事务：本文件整体事务；psql必须启用ON_ERROR_STOP
-- 失败处理：事务回滚，修正后从本文件重新执行
-- 验证：文件末尾检查本批表数量
\set ON_ERROR_STOP on
BEGIN;

CREATE TABLE cm_question (
    id bigint NOT NULL,
    question_code varchar(20) NOT NULL,
    exam_subject_id bigint NOT NULL,
    source_identity_hash varchar(64),
    copied_from_question_id bigint,
    evidence_group_key varchar(100),
    del_flag char(1) DEFAULT '0' NOT NULL,
    create_dept bigint,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_question PRIMARY KEY (id),
    CONSTRAINT uk_cm_question_question_code UNIQUE (question_code),
    CONSTRAINT ck_cm_question_del_flag CHECK (del_flag IN ('0','1'))
);

CREATE TABLE cm_question_revision (
    id bigint NOT NULL,
    question_id bigint NOT NULL,
    revision_no integer NOT NULL,
    status varchar(20) DEFAULT 'draft' NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    question_type varchar(30) NOT NULL,
    difficulty varchar(20),
    ability_type varchar(30),
    estimated_seconds integer,
    stem text NOT NULL,
    answer jsonb,
    analysis text,
    common_mistakes text,
    source_type varchar(30),
    source_name varchar(500),
    source_external_id varchar(200),
    source_locator text,
    authorization_status varchar(20),
    authorization_note text,
    content_hash varchar(64) NOT NULL,
    knowledge_mapping_status varchar(20),
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_question_revision PRIMARY KEY (id),
    CONSTRAINT ck_cm_question_revision_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_cm_question_revision_difficulty CHECK (difficulty IN ('easy','medium','hard')),
    CONSTRAINT ck_cm_question_revision_estimated_seconds CHECK (estimated_seconds > 0)
);

CREATE TABLE cm_question_revision_event (
    id bigint NOT NULL,
    question_id bigint NOT NULL,
    question_revision_id bigint NOT NULL,
    event_type varchar(30) NOT NULL,
    from_status varchar(20),
    to_status varchar(20) NOT NULL,
    opinion text,
    rejected_fields jsonb,
    operator_id bigint,
    request_id varchar(100) NOT NULL,
    trace_id varchar(100),
    event_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_question_revision_event PRIMARY KEY (id)
);

CREATE TABLE cm_question_option (
    id bigint NOT NULL,
    question_revision_id bigint NOT NULL,
    option_label varchar(10) NOT NULL,
    option_text text NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_question_option PRIMARY KEY (id)
);

CREATE TABLE cm_question_image (
    id bigint NOT NULL,
    question_revision_id bigint NOT NULL,
    image_order integer NOT NULL,
    source_url text,
    alt_text text,
    storage_path text,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_question_image PRIMARY KEY (id)
);

CREATE TABLE cm_question_knowledge (
    id bigint NOT NULL,
    question_revision_id bigint NOT NULL,
    question_id bigint NOT NULL,
    knowledge_point_id bigint NOT NULL,
    exam_subject_id bigint NOT NULL,
    relation_role varchar(20),
    sort_order integer DEFAULT 0 NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_question_knowledge PRIMARY KEY (id),
    CONSTRAINT ck_cm_question_knowledge_sort_order CHECK (sort_order >= 0)
);

CREATE TABLE cm_document (
    id bigint NOT NULL,
    syllabus_version_id bigint NOT NULL,
    title varchar(500) NOT NULL,
    document_type varchar(30) NOT NULL,
    edition varchar(100),
    source_file_path text,
    file_hash varchar(64),
    status varchar(20) DEFAULT 'draft' NOT NULL,
    submitted_by bigint,
    submitted_time timestamptz,
    reviewed_by bigint,
    reviewed_time timestamptz,
    review_comment text,
    published_by bigint,
    published_time timestamptz,
    del_flag char(1) DEFAULT '0' NOT NULL,
    create_dept bigint,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_document PRIMARY KEY (id),
    CONSTRAINT ck_cm_document_del_flag CHECK (del_flag IN ('0','1'))
);

CREATE TABLE cm_document_chunk (
    id bigint NOT NULL,
    document_id bigint NOT NULL,
    chunk_order integer NOT NULL,
    heading varchar(500),
    heading_path jsonb DEFAULT '{"schema_version":"1.0","headings":[]}'::jsonb NOT NULL,
    content text NOT NULL,
    source_locator jsonb DEFAULT '{"schema_version":"1.0","source_type":"unknown"}'::jsonb NOT NULL,
    content_hash varchar(64) NOT NULL,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_document_chunk PRIMARY KEY (id)
);

CREATE TABLE cm_document_image (
    id bigint NOT NULL,
    chunk_id bigint NOT NULL,
    image_order integer NOT NULL,
    source_reference text NOT NULL,
    storage_path text NOT NULL,
    alt_text text,
    mime_type varchar(100),
    file_hash varchar(64),
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_document_image PRIMARY KEY (id)
);

CREATE TABLE cm_chunk_knowledge (
    id bigint NOT NULL,
    chunk_id bigint NOT NULL,
    knowledge_point_id bigint NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_chunk_knowledge PRIMARY KEY (id)
);

CREATE TABLE cm_import_batch (
    id bigint NOT NULL,
    request_id varchar(100) NOT NULL,
    retry_from_batch_id bigint,
    document_id bigint,
    syllabus_version_id bigint,
    exam_subject_id bigint,
    source_file_path text NOT NULL,
    source_file_hash varchar(64) NOT NULL,
    source_file_name varchar(255) NOT NULL,
    source_file_size bigint NOT NULL,
    source_file_mime varchar(100),
    import_type varchar(50) NOT NULL,
    template_version varchar(50),
    parser_version varchar(50),
    parse_config jsonb,
    status varchar(20) DEFAULT 'uploaded' NOT NULL,
    current_stage varchar(50),
    progress_percent numeric(5,2) DEFAULT 0 NOT NULL,
    valid_count integer DEFAULT 0 NOT NULL,
    warning_count integer DEFAULT 0 NOT NULL,
    failed_count integer DEFAULT 0 NOT NULL,
    generated_count integer DEFAULT 0 NOT NULL,
    trace_id varchar(100),
    confirmed_by bigint,
    confirmed_time timestamptz,
    started_time timestamptz,
    finished_time timestamptz,
    error_summary text,
    create_dept bigint,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_import_batch PRIMARY KEY (id),
    CONSTRAINT uk_cm_import_batch_request_id UNIQUE (request_id),
    CONSTRAINT ck_cm_import_batch_progress_percent CHECK (progress_percent >= 0 AND progress_percent <= 100),
    CONSTRAINT ck_cm_import_batch_valid_count CHECK (valid_count >= 0),
    CONSTRAINT ck_cm_import_batch_warning_count CHECK (warning_count >= 0),
    CONSTRAINT ck_cm_import_batch_failed_count CHECK (failed_count >= 0),
    CONSTRAINT ck_cm_import_batch_generated_count CHECK (generated_count >= 0)
);

CREATE TABLE cm_import_record (
    id bigint NOT NULL,
    batch_id bigint NOT NULL,
    retry_of_record_id bigint,
    source_line_no integer NOT NULL,
    source_key varchar(200),
    raw_record jsonb NOT NULL,
    raw_hash varchar(64) NOT NULL,
    subject_no smallint,
    exam_subject_id bigint,
    status varchar(20) DEFAULT 'pending' NOT NULL,
    result_data jsonb,
    error_message text,
    processed_time timestamptz,
    CONSTRAINT pk_cm_import_record PRIMARY KEY (id)
);

CREATE TABLE cm_question_scoring_point (
    id bigint NOT NULL,
    question_revision_id bigint NOT NULL,
    scoring_code varchar(50) NOT NULL,
    description text NOT NULL,
    max_score numeric(10,2) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_question_scoring_point PRIMARY KEY (id),
    CONSTRAINT ck_cm_question_scoring_point_max_score CHECK (max_score > 0),
    CONSTRAINT ck_cm_question_scoring_point_sort_order CHECK (sort_order >= 0)
);

CREATE TABLE cm_scoring_point_knowledge (
    id bigint NOT NULL,
    scoring_point_id bigint NOT NULL,
    knowledge_point_id bigint NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_scoring_point_knowledge PRIMARY KEY (id)
);

CREATE TABLE cm_profile_rule_version (
    id bigint NOT NULL,
    version_code varchar(50) NOT NULL,
    status varchar(20) DEFAULT 'draft' NOT NULL,
    config_schema_version varchar(30) NOT NULL,
    config_data jsonb NOT NULL,
    content_hash varchar(64) NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    published_by bigint,
    published_time timestamptz,
    create_dept bigint,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_profile_rule_version PRIMARY KEY (id),
    CONSTRAINT uk_cm_profile_rule_version_version_code UNIQUE (version_code),
    CONSTRAINT ck_cm_profile_rule_version_row_version CHECK (row_version >= 0)
);

CREATE TABLE cm_exam_schedule (
    id bigint NOT NULL,
    certification_id bigint NOT NULL,
    revision_no integer NOT NULL,
    exam_date date NOT NULL,
    registration_start timestamptz,
    registration_end timestamptz,
    timezone varchar(50) DEFAULT 'Asia/Shanghai' NOT NULL,
    note text,
    status varchar(20) DEFAULT 'draft' NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    published_by bigint,
    published_time timestamptz,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_exam_schedule PRIMARY KEY (id),
    CONSTRAINT ck_cm_exam_schedule_row_version CHECK (row_version >= 0)
);

CREATE TABLE cm_exam_schedule_session (
    id bigint NOT NULL,
    exam_schedule_id bigint NOT NULL,
    exam_subject_id bigint NOT NULL,
    session_code varchar(50) NOT NULL,
    start_time timestamptz NOT NULL,
    end_time timestamptz NOT NULL,
    sort_order integer NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_exam_schedule_session PRIMARY KEY (id),
    CONSTRAINT ck_cm_exam_schedule_session_sort_order CHECK (sort_order >= 0)
);

CREATE TABLE cm_collection (
    id bigint NOT NULL,
    certification_id bigint NOT NULL,
    syllabus_version_id bigint NOT NULL,
    collection_code varchar(50) NOT NULL,
    collection_name varchar(200) NOT NULL,
    collection_type varchar(30) NOT NULL,
    status char(1) DEFAULT '0' NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_collection PRIMARY KEY (id),
    CONSTRAINT ck_cm_collection_status CHECK (status IN ('0','1'))
);

CREATE TABLE cm_collection_revision (
    id bigint NOT NULL,
    collection_id bigint NOT NULL,
    revision_no integer NOT NULL,
    status varchar(20) DEFAULT 'draft' NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    question_count integer DEFAULT 0 NOT NULL,
    total_report_score numeric(10,2) DEFAULT 0 NOT NULL,
    duration_minutes integer,
    pause_allowed boolean DEFAULT false NOT NULL,
    submitted_by bigint,
    submitted_time timestamptz,
    reviewed_by bigint,
    reviewed_time timestamptz,
    review_opinion text,
    published_by bigint,
    published_time timestamptz,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_collection_revision PRIMARY KEY (id),
    CONSTRAINT ck_cm_collection_revision_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_cm_collection_revision_question_count CHECK (question_count >= 0),
    CONSTRAINT ck_cm_collection_revision_total_report_score CHECK (total_report_score >= 0),
    CONSTRAINT ck_cm_collection_revision_duration_minutes CHECK (duration_minutes > 0)
);

CREATE TABLE cm_collection_item (
    id bigint NOT NULL,
    collection_revision_id bigint NOT NULL,
    question_revision_id bigint NOT NULL,
    item_order integer NOT NULL,
    report_score numeric(10,2) NOT NULL,
    answer_schema jsonb NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_collection_item PRIMARY KEY (id),
    CONSTRAINT ck_cm_collection_item_report_score CHECK (report_score > 0)
);

CREATE TABLE cm_collection_current (
    collection_id bigint NOT NULL,
    collection_revision_id bigint NOT NULL,
    purpose_code varchar(30) NOT NULL,
    effective_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_collection_current PRIMARY KEY (collection_id),
    CONSTRAINT uk_cm_collection_current_collection_revision_id UNIQUE (collection_revision_id)
);

CREATE TABLE cm_import_issue (
    id bigint NOT NULL,
    import_batch_id bigint NOT NULL,
    import_record_id bigint,
    issue_code varchar(50) NOT NULL,
    field_path varchar(300),
    severity varchar(20) NOT NULL,
    message text NOT NULL,
    status varchar(20) DEFAULT 'open' NOT NULL,
    resolution text,
    resolved_by bigint,
    resolved_time timestamptz,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_import_issue PRIMARY KEY (id)
);

DO $$ BEGIN IF (SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name = ANY (ARRAY['cm_question','cm_question_revision','cm_question_revision_event','cm_question_option','cm_question_image','cm_question_knowledge','cm_document','cm_document_chunk','cm_document_image','cm_chunk_knowledge','cm_import_batch','cm_import_record','cm_question_scoring_point','cm_scoring_point_knowledge','cm_profile_rule_version','cm_exam_schedule','cm_exam_schedule_session','cm_collection','cm_collection_revision','cm_collection_item','cm_collection_current','cm_import_issue'])) <> 22 THEN RAISE EXCEPTION 'CertMuse 002 table count mismatch'; END IF; END $$;
COMMIT;
