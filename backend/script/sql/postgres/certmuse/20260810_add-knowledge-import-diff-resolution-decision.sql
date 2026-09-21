BEGIN;

ALTER TABLE cm_knowledge_import_diff
    ADD COLUMN resolution_decision varchar(20);

ALTER TABLE cm_knowledge_import_diff
    ADD CONSTRAINT ck_cm_knowledge_import_diff_resolution_decision CHECK (
        resolution_decision IS NULL OR resolution_decision IN ('approve', 'reject')
    );

COMMIT;
