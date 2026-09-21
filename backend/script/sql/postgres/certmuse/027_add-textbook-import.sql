BEGIN;

ALTER TABLE cm_import_batch
    DROP CONSTRAINT IF EXISTS ck_cm_import_batch_document_chunk_parse_config;

ALTER TABLE cm_import_batch
    ADD CONSTRAINT ck_cm_import_batch_document_chunk_parse_config CHECK (
        import_type <> 'document_chunk'
        OR CASE
        WHEN (
            parse_config IS NOT NULL
            AND jsonb_typeof(parse_config) = 'object'
            AND parse_config ?& ARRAY[
                'schema_version', 'import_type', 'mode', 'title', 'edition', 'subject_mappings'
            ]
        ) THEN (
            parse_config ->> 'schema_version' = 'import_parse_config/1.0'
            AND parse_config ->> 'import_type' = 'document_chunk'
            AND parse_config ->> 'mode' IN ('create', 'replace_draft')
            AND jsonb_typeof(parse_config -> 'title') = 'string'
            AND char_length(parse_config ->> 'title') BETWEEN 1 AND 500
            AND (
                parse_config -> 'edition' = 'null'::jsonb
                OR (
                    jsonb_typeof(parse_config -> 'edition') = 'string'
                    AND char_length(parse_config ->> 'edition') BETWEEN 1 AND 100
                )
            )
            AND jsonb_typeof(parse_config -> 'subject_mappings') = 'array'
            AND jsonb_array_length(parse_config -> 'subject_mappings') BETWEEN 1 AND 3
        )
        ELSE false
        END
    );

CREATE INDEX IF NOT EXISTS idx_cm_import_batch_document_started
    ON cm_import_batch(document_id, started_time DESC)
    WHERE document_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cm_document_chunk_document_hash
    ON cm_document_chunk(document_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_cm_chunk_knowledge_knowledge_chunk
    ON cm_chunk_knowledge(knowledge_point_id, chunk_id);

COMMIT;
