BEGIN;

ALTER TABLE cm_import_batch
    ADD COLUMN baseline_hash varchar(64),
    ADD COLUMN resolution_hash varchar(64);

CREATE TABLE cm_knowledge_import_diff (
    id bigint NOT NULL,
    import_batch_id bigint NOT NULL,
    import_record_id bigint,
    old_knowledge_point_id bigint,
    suggested_knowledge_point_id bigint,
    confirmed_knowledge_point_id bigint,
    exam_subject_id bigint NOT NULL,
    action varchar(20) NOT NULL,
    resolution_status varchar(20) NOT NULL,
    match_score numeric(5,2),
    match_evidence jsonb NOT NULL,
    changed_fields jsonb NOT NULL,
    create_by bigint,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_by bigint,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_knowledge_import_diff PRIMARY KEY (id),
    CONSTRAINT fk_cm_knowledge_import_diff_batch FOREIGN KEY (import_batch_id)
        REFERENCES cm_import_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_knowledge_import_diff_record FOREIGN KEY (import_record_id)
        REFERENCES cm_import_record(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_knowledge_import_diff_old_knowledge FOREIGN KEY (old_knowledge_point_id)
        REFERENCES cm_knowledge_point(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_knowledge_import_diff_suggested_knowledge FOREIGN KEY (suggested_knowledge_point_id)
        REFERENCES cm_knowledge_point(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_knowledge_import_diff_confirmed_knowledge FOREIGN KEY (confirmed_knowledge_point_id)
        REFERENCES cm_knowledge_point(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_knowledge_import_diff_subject FOREIGN KEY (exam_subject_id)
        REFERENCES cm_exam_subject(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cm_knowledge_import_diff_action CHECK (
        action IN ('unchanged', 'update', 'move', 'add', 'delete')
    ),
    CONSTRAINT ck_cm_knowledge_import_diff_resolution CHECK (
        resolution_status IN ('not_required', 'pending', 'manual_confirmed')
    ),
    CONSTRAINT ck_cm_knowledge_import_diff_score CHECK (
        match_score IS NULL OR match_score BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_cm_knowledge_import_diff_evidence_schema CHECK (
        jsonb_typeof(match_evidence) = 'object' AND match_evidence ? 'schema_version'
    ),
    CONSTRAINT ck_cm_knowledge_import_diff_changed_schema CHECK (
        jsonb_typeof(changed_fields) = 'object' AND changed_fields ? 'schema_version'
    ),
    CONSTRAINT ck_cm_knowledge_import_diff_record_or_old CHECK (
        import_record_id IS NOT NULL OR old_knowledge_point_id IS NOT NULL
    )
);

CREATE UNIQUE INDEX uk_cm_knowledge_import_diff_record
    ON cm_knowledge_import_diff(import_batch_id, import_record_id)
    WHERE import_record_id IS NOT NULL;

CREATE UNIQUE INDEX uk_cm_knowledge_import_diff_confirmed
    ON cm_knowledge_import_diff(import_batch_id, confirmed_knowledge_point_id)
    WHERE confirmed_knowledge_point_id IS NOT NULL;

CREATE INDEX idx_cm_knowledge_import_diff_filter
    ON cm_knowledge_import_diff(import_batch_id, resolution_status, action, exam_subject_id, id);

COMMIT;
