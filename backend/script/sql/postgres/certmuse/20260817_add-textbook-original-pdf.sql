BEGIN;

CREATE TABLE cm_textbook_original_pdf (
    id bigint NOT NULL,
    document_id bigint NOT NULL,
    object_key varchar(1000) NOT NULL,
    file_name varchar(500) NOT NULL,
    file_size bigint NOT NULL,
    file_hash varchar(64) NOT NULL,
    uploaded_by bigint NOT NULL,
    uploaded_time timestamptz DEFAULT now() NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_textbook_original_pdf PRIMARY KEY (id),
    CONSTRAINT uk_cm_textbook_original_pdf_document UNIQUE (document_id),
    CONSTRAINT ck_cm_textbook_original_pdf_size CHECK (file_size > 0),
    CONSTRAINT ck_cm_textbook_original_pdf_hash CHECK (file_hash ~ '^[0-9a-f]{64}$')
);

ALTER TABLE cm_textbook_original_pdf ADD CONSTRAINT fk_cm_textbook_original_pdf_document
    FOREIGN KEY (document_id) REFERENCES cm_document(id) ON DELETE CASCADE;

CREATE INDEX idx_cm_textbook_original_pdf_document ON cm_textbook_original_pdf(document_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM sys_menu
         WHERE menu_id = 1761400000000099014
           AND perms IS DISTINCT FROM 'certmuse:learning:textbook:query'
    ) OR EXISTS (
        SELECT 1
          FROM sys_menu
         WHERE perms = 'certmuse:learning:textbook:query'
           AND menu_id <> 1761400000000099014
    ) THEN
        RAISE EXCEPTION 'textbook original PDF permission id conflict';
    END IF;
END $$;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, active_menu, ext, create_by, create_time, update_by, update_time, remark)
SELECT 1761400000000099014, '学员教材原文查询', 0, 999, '', NULL, NULL, 'N', 'N', 'F', '1', '0',
       'certmuse:learning:textbook:query', '#', '', '', 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden learner permission'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'certmuse:learning:textbook:query');

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 1761400000000099014 FROM sys_role
WHERE role_key = 'student' AND del_flag = '0'
ON CONFLICT DO NOTHING;

COMMIT;
