# CertMuse 首期隐藏未启用菜单

- 日期：2026-07-24（Asia/Taipei）
- 目标：`certmuse-pg`（77）
- 执行路径：项目本地 `tools/ops/local/srv-ops/bin/srvctl db exec`

## 变更

将下列 `sys_menu` 记录的 `visible` 标记设为隐藏（`1`），未删除菜单、角色关联或权限：

- 系统工具、代码生成、修改生成配置。
- 测试菜单、测试单表、测试树表。
- AI 会话、AI 控制台。
- Admin 监控、任务调度中心。
- PLUS 官网外链。

系统监控中的在线用户和缓存监控，以及系统与权限相关菜单保留。

## 依据与验证

- 首期生产包未包含演示、代码生成、工作流、AI、SnailAI 与 MCP；SnailJob 客户端保持关闭。当时 OSS 尚未配置。
- 幂等 SQL 已保存在 `backend/script/sql/postgres/certmuse/20260724_hide-disabled-menus.sql`。
- 受控 SQL 更新影响 11 行；“修改生成配置”原本已隐藏。
- 回读 12 个目标菜单均为 `visible = '1'`。

菜单路由在用户登录时从数据库构建，不依赖服务端菜单缓存；已有登录会话刷新页面或重新登录后生效。2026-07-24 随本机 MinIO 启用，OSS 文件管理已由 `20260724_enable-minio-oss.sql` 恢复为可见；其他入口仍需先启用其模块、配置和必要外部服务后再恢复。
