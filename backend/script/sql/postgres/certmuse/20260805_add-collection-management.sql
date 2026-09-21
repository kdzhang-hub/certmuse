-- Collection revision snapshots, linear workflow and permissions.
\set ON_ERROR_STOP on
BEGIN;

ALTER TABLE cm_collection_revision
    ADD COLUMN IF NOT EXISTS collection_name varchar(200),
    ADD COLUMN IF NOT EXISTS collection_type varchar(30),
    ADD COLUMN IF NOT EXISTS certification_id bigint,
    ADD COLUMN IF NOT EXISTS syllabus_version_id bigint;

UPDATE cm_collection_revision r
SET collection_name = c.collection_name,
    collection_type = c.collection_type,
    certification_id = c.certification_id,
    syllabus_version_id = c.syllabus_version_id
FROM cm_collection c
WHERE c.id = r.collection_id
  AND (r.collection_name IS NULL OR r.collection_type IS NULL
       OR r.certification_id IS NULL OR r.syllabus_version_id IS NULL);

ALTER TABLE cm_collection_revision
    ALTER COLUMN collection_name SET NOT NULL,
    ALTER COLUMN collection_type SET NOT NULL,
    ALTER COLUMN certification_id SET NOT NULL,
    ALTER COLUMN syllabus_version_id SET NOT NULL,
    ALTER COLUMN pause_allowed SET DEFAULT true;

ALTER TABLE cm_collection_revision DROP CONSTRAINT IF EXISTS ck_cm_collection_revision_collection_type;
ALTER TABLE cm_collection_revision ADD CONSTRAINT ck_cm_collection_revision_collection_type
    CHECK (collection_type IN ('FIRST_DIAGNOSTIC', 'PRACTICE', 'SIMULATION'));
ALTER TABLE cm_collection_revision DROP CONSTRAINT IF EXISTS fk_cm_collection_revision_certification_id;
ALTER TABLE cm_collection_revision ADD CONSTRAINT fk_cm_collection_revision_certification_id
    FOREIGN KEY (certification_id) REFERENCES cm_exam_certification(id) ON DELETE RESTRICT;
ALTER TABLE cm_collection_revision DROP CONSTRAINT IF EXISTS fk_cm_collection_revision_syllabus_version_id;
ALTER TABLE cm_collection_revision ADD CONSTRAINT fk_cm_collection_revision_syllabus_version_id
    FOREIGN KEY (syllabus_version_id) REFERENCES cm_syllabus_version(id) ON DELETE RESTRICT;

DROP TRIGGER IF EXISTS trg_cm_collection_revision_status ON cm_collection_revision;
CREATE TRIGGER trg_cm_collection_revision_status
BEFORE UPDATE OF status ON cm_collection_revision
FOR EACH ROW EXECUTE FUNCTION cm_guard_status_transition(
    'draft>pending_review,pending_review>draft,pending_review>published,published>superseded'
);

-- Only draft and pending_review are working revisions for the collection workflow.
DROP INDEX IF EXISTS uk_cm_collection_revision_working;
CREATE UNIQUE INDEX uk_cm_collection_revision_working
    ON cm_collection_revision(collection_id)
    WHERE status IN ('draft', 'pending_review');

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu,
    ext, create_dept, create_by, create_time, update_by, update_time, remark
) VALUES
    (1761400000000090511, '新增题集', 1761400000000090150, 1, '', NULL, NULL, 'N', 'N', 'F', '0', '0', 'certmuse:question:collection:add', '', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse collection permission'),
    (1761400000000090512, '编辑题集草稿', 1761400000000090150, 2, '', NULL, NULL, 'N', 'N', 'F', '0', '0', 'certmuse:question:collection:edit', '', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse collection permission'),
    (1761400000000090513, '题集发布检查', 1761400000000090150, 3, '', NULL, NULL, 'N', 'N', 'F', '0', '0', 'certmuse:question:collection:check', '', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse collection permission'),
    (1761400000000090514, '提交题集审核', 1761400000000090150, 4, '', NULL, NULL, 'N', 'N', 'F', '0', '0', 'certmuse:question:collection:submit-review', '', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse collection permission'),
    (1761400000000090515, '审核题集', 1761400000000090150, 5, '', NULL, NULL, 'N', 'N', 'F', '0', '0', 'certmuse:question:collection:review', '', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse collection permission')
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    perms = EXCLUDED.perms,
    update_time = CURRENT_TIMESTAMP,
    remark = EXCLUDED.remark;

UPDATE sys_menu
SET status = '1', visible = '1', update_time = CURRENT_TIMESTAMP,
    remark = 'Deprecated: collection enable/disable workflow removed'
WHERE perms = 'certmuse:question:collection:change-status';

COMMIT;
