\set ON_ERROR_STOP on
BEGIN;

-- A paper import is validated asynchronously, so past-paper metadata must survive
-- from the upload form until the collection draft is created after confirmation.
ALTER TABLE cm_paper_import
    ADD COLUMN IF NOT EXISTS exam_year integer,
    ADD COLUMN IF NOT EXISTS exam_month smallint,
    ADD COLUMN IF NOT EXISTS paper_type_code varchar(50),
    ADD COLUMN IF NOT EXISTS paper_type_name varchar(100);

COMMIT;
