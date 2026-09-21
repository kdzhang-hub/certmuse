BEGIN;

UPDATE sys_menu
SET menu_name = '考纲管理',
    update_by = 1,
    update_time = CURRENT_TIMESTAMP,
    remark = 'CertMuse syllabus management navigation'
WHERE menu_id = 1761400000000090120
  AND path = 'knowledge'
  AND perms = 'certmuse:catalog:knowledge:list';

COMMIT;
