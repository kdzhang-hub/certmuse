-- CertMuse 一期数据库：基础资格与知识域
-- 前置：RuoYi PostgreSQL 基础脚本
-- 执行账号：本地数据库对象所有者（不得使用应用账号）
-- 事务：本文件整体事务；psql必须启用ON_ERROR_STOP
-- 失败处理：事务回滚，修正后从本文件重新执行
-- 验证：文件末尾检查本批表数量
\set ON_ERROR_STOP on
BEGIN;

CREATE TABLE cm_exam_certification (
    id bigint NOT NULL,
    certification_code varchar(50) NOT NULL,
    certification_name varchar(200) NOT NULL,
    qualification_level varchar(20) NOT NULL,
    status char(1) DEFAULT '0' NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    create_dept bigint,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_exam_certification PRIMARY KEY (id),
    CONSTRAINT uk_cm_exam_certification_certification_code UNIQUE (certification_code),
    CONSTRAINT ck_cm_exam_certification_status CHECK (status IN ('0','1')),
    CONSTRAINT ck_cm_exam_certification_sort_order CHECK (sort_order >= 0)
);

CREATE TABLE cm_exam_subject (
    id bigint NOT NULL,
    certification_id bigint NOT NULL,
    subject_code varchar(50) NOT NULL,
    subject_name varchar(200) NOT NULL,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_exam_subject PRIMARY KEY (id)
);

CREATE TABLE cm_syllabus_version (
    id bigint NOT NULL,
    certification_id bigint NOT NULL,
    version_name varchar(200) NOT NULL,
    published_date date,
    source_file_path text,
    create_dept bigint,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_syllabus_version PRIMARY KEY (id)
);

CREATE TABLE cm_knowledge_point (
    id bigint NOT NULL,
    syllabus_version_id bigint NOT NULL,
    exam_subject_id bigint NOT NULL,
    parent_id bigint,
    syllabus_number varchar(100) NOT NULL,
    syllabus_title varchar(500) NOT NULL,
    tree_depth smallint NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    description text,
    importance smallint,
    diagnostic_enabled boolean DEFAULT false NOT NULL,
    recommendation_enabled boolean DEFAULT false NOT NULL,
    status char(1) DEFAULT '0' NOT NULL,
    del_flag char(1) DEFAULT '0' NOT NULL,
    create_dept bigint,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_knowledge_point PRIMARY KEY (id),
    CONSTRAINT ck_cm_knowledge_point_importance CHECK (importance IN (1,2,3)),
    CONSTRAINT ck_cm_knowledge_point_status CHECK (status IN ('0','1')),
    CONSTRAINT ck_cm_knowledge_point_del_flag CHECK (del_flag IN ('0','1'))
);

CREATE TABLE cm_knowledge_prerequisite (
    id bigint NOT NULL,
    knowledge_point_id bigint NOT NULL,
    prerequisite_knowledge_point_id bigint NOT NULL,
    syllabus_version_id bigint NOT NULL,
    exam_subject_id bigint NOT NULL,
    relation_type varchar(30) NOT NULL,
    weight numeric(10,4),
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_knowledge_prerequisite PRIMARY KEY (id)
);

CREATE TABLE cm_misconception (
    id bigint NOT NULL,
    misconception_code varchar(100) NOT NULL,
    name varchar(200) NOT NULL,
    description text,
    status char(1) DEFAULT '0' NOT NULL,
    create_dept bigint,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_misconception PRIMARY KEY (id),
    CONSTRAINT uk_cm_misconception_misconception_code UNIQUE (misconception_code),
    CONSTRAINT ck_cm_misconception_status CHECK (status IN ('0','1'))
);

CREATE TABLE cm_knowledge_misconception (
    id bigint NOT NULL,
    knowledge_point_id bigint NOT NULL,
    misconception_id bigint NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    status char(1) DEFAULT '0' NOT NULL,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_knowledge_misconception PRIMARY KEY (id),
    CONSTRAINT ck_cm_knowledge_misconception_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_cm_knowledge_misconception_status CHECK (status IN ('0','1'))
);

DO $$ BEGIN IF (SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name = ANY (ARRAY['cm_exam_certification','cm_exam_subject','cm_syllabus_version','cm_knowledge_point','cm_knowledge_prerequisite','cm_misconception','cm_knowledge_misconception'])) <> 7 THEN RAISE EXCEPTION 'CertMuse 001 table count mismatch'; END IF; END $$;
COMMIT;
