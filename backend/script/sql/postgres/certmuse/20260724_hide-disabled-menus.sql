-- CertMuse first-phase navigation: hide upstream entries whose module or external service is not enabled.
-- This is reversible: restore `visible` to '0' only after enabling the corresponding backend/module configuration.
UPDATE sys_menu
SET visible = '1'
WHERE menu_id IN (
    1761400000000000003, -- 系统工具（首期只有已移除的代码生成）
    1761400000000000004, -- PLUS 官网
    1761400000000000005, -- 测试菜单
    1761400000000000006, -- AI 会话
    1761400000000000115, -- 代码生成
    1761400000000000116, -- 修改生成配置
    1761400000000000117, -- Admin 监控
    1761400000000000118, -- 文件管理（OSS）
    1761400000000000120, -- SnailJob 控制台
    1761400000000000121, -- SnailAI 控制台
    1761400000000001500, -- 测试单表
    1761400000000001506  -- 测试树表
)
  AND visible <> '1';
