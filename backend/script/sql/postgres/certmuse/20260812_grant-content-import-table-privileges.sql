\set ON_ERROR_STOP on
BEGIN;

-- The baseline grants DML access to CertMuse business tables and keeps
-- append-only event/audit tables explicitly restricted. These two content
-- import tables were introduced later, so they need the same business-table
-- access without widening any append-only table permissions.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE cm_knowledge_import_diff TO certmuse_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE cm_paper_import TO certmuse_app;

COMMIT;
