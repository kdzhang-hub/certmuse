-- CertMuse management navigation for the admin application.
-- The statements are idempotent and intentionally do not assign menus to non-super-admin roles.

DELETE FROM sys_menu
WHERE menu_id IN (1761400000000090001, 1761400000000090002, 1761400000000090003)
  AND remark = 'TEMP_LOCAL_PREVIEW_20260730';

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu,
    ext, create_dept, create_by, create_time, update_by, update_time, remark
)
VALUES
    (1761400000000090100, '内容中心', 0, 10, 'content', NULL, NULL, 'N', 'Y', 'M', '0', '0', NULL, 'documentation', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090110, '科目与版本', 1761400000000090100, 1, 'subject-version', 'certmuse/catalog/subject-version/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:catalog:subject:list', 'category', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090120, '考纲与知识点树', 1761400000000090100, 2, 'knowledge', 'certmuse/catalog/knowledge/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:catalog:knowledge:list', 'tree', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090130, '教材与资源', 1761400000000090100, 3, 'resources', 'certmuse/catalog/resource/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:catalog:resource:list', 'education', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090140, '题目管理', 1761400000000090100, 4, 'questions', 'certmuse/question/question/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:question:list', 'question', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090150, '题集与试卷', 1761400000000090100, 5, 'collections', 'certmuse/question/collection/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:question:collection:list', 'clipboard', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090160, '内容导入', 1761400000000090100, 6, 'imports', 'certmuse/catalog/import/batches/index', NULL, 'N', 'N', 'C', '0', '0', 'certmuse:catalog:import', 'upload', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),

    (1761400000000090200, '审核与规则', 0, 11, 'review-rules', NULL, NULL, 'N', 'Y', 'M', '0', '0', NULL, 'finish', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090210, '内容审核', 1761400000000090200, 1, 'content-review', 'certmuse/question/review/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:review:list', 'finish', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090220, '规则配置', 1761400000000090200, 2, 'rules', 'certmuse/learning/rule/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:rule:list', 'build', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090230, '规则命中日志', 1761400000000090200, 3, 'rule-hits', 'certmuse/insight/rule-hit/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:rule-hit:list', 'log', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),

    (1761400000000090300, '运营中心', 0, 12, 'operations', NULL, NULL, 'N', 'Y', 'M', '0', '0', NULL, 'dashboard', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090310, '题池健康', 1761400000000090300, 1, 'question-health', 'certmuse/insight/question-health/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:insight:question-health:list', 'chart', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090320, '运营看板', 1761400000000090300, 2, 'dashboard', 'certmuse/insight/dashboard/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:insight:dashboard:list', 'dashboard', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090330, '异常中心', 1761400000000090300, 3, 'exceptions', 'certmuse/insight/exception/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:insight:exception:list', 'bug', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),
    (1761400000000090340, '导出任务', 1761400000000090300, 4, 'exports', 'certmuse/insight/export/index', NULL, 'N', 'Y', 'C', '0', '0', 'certmuse:insight:export:list', 'download', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse admin navigation'),

    (1761400000000090401, '新建导入', 1761400000000090160, 1, 'new', 'certmuse/catalog/import/index', NULL, 'N', 'N', 'C', '1', '0', 'certmuse:catalog:import:create', 'upload', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden business route'),
    (1761400000000090402, '导入批次详情', 1761400000000090160, 2, 'detail/:batchId', 'certmuse/catalog/import/batches/detail', NULL, 'N', 'N', 'C', '1', '0', 'certmuse:catalog:import:detail', 'documentation', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden business route'),
    (1761400000000090403, '教材编辑', 1761400000000090130, 1, 'edit/:id', 'certmuse/catalog/resource/edit', NULL, 'N', 'N', 'C', '1', '0', 'certmuse:catalog:resource:edit', 'edit', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden business route'),
    (1761400000000090404, '教材预览', 1761400000000090130, 2, 'preview/:id', 'certmuse/catalog/resource/preview', NULL, 'N', 'N', 'C', '1', '0', 'certmuse:catalog:resource:preview', 'eye-open', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden business route'),
    (1761400000000090405, '官方考试安排', 1761400000000090110, 1, 'exam-schedules', 'certmuse/catalog/exam-schedule/index', NULL, 'N', 'N', 'C', '1', '0', 'certmuse:catalog:exam-schedule:list', 'date', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden business route'),
    (1761400000000090410, '题目编辑', 1761400000000090140, 1, 'edit/:id', 'certmuse/question/question/edit', NULL, 'N', 'N', 'C', '1', '0', 'certmuse:question:edit', 'edit', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden business route'),
    (1761400000000090411, '题目预览', 1761400000000090140, 2, 'preview/:id', 'certmuse/question/question/preview', NULL, 'N', 'N', 'C', '1', '0', 'certmuse:question:preview', 'eye-open', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden business route'),
    (1761400000000090420, '审核详情', 1761400000000090210, 1, 'detail/:id', 'certmuse/question/review/detail', NULL, 'N', 'N', 'C', '1', '0', 'certmuse:review:detail', 'documentation', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden business route'),
    (1761400000000090430, '规则编辑', 1761400000000090220, 1, 'edit/:id', 'certmuse/learning/rule/edit', NULL, 'N', 'N', 'C', '1', '0', 'certmuse:rule:edit', 'edit', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden business route'),
    (1761400000000090431, '规则模拟', 1761400000000090220, 2, 'simulate/:id', 'certmuse/learning/rule/simulate', NULL, 'N', 'N', 'C', '1', '0', 'certmuse:rule:simulate', 'build', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden business route'),
    (1761400000000090432, '规则发布', 1761400000000090220, 3, 'publish/:id', 'certmuse/learning/rule/publish', NULL, 'N', 'N', 'C', '1', '0', 'certmuse:rule:publish', 'upload', '', '', NULL, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'CertMuse hidden business route')
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    parent_id = EXCLUDED.parent_id,
    order_num = EXCLUDED.order_num,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    query_param = EXCLUDED.query_param,
    is_frame = EXCLUDED.is_frame,
    is_cache = EXCLUDED.is_cache,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    perms = EXCLUDED.perms,
    icon = EXCLUDED.icon,
    active_menu = EXCLUDED.active_menu,
    ext = EXCLUDED.ext,
    update_by = EXCLUDED.update_by,
    update_time = CURRENT_TIMESTAMP,
    remark = EXCLUDED.remark;
