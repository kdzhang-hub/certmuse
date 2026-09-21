\set ON_ERROR_STOP on
BEGIN;

-- The original-PDF table was added after the baseline business-table grants.
-- The runtime account needs only the DML operations used by the attachment flow.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE cm_textbook_original_pdf TO certmuse_app;

COMMIT;
