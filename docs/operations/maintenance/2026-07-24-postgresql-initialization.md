# CertMuse PostgreSQL 初始化记录

- 日期：2026-07-24（Asia/Taipei）
- 目标：`certmuse-app`（77）的 PostgreSQL `16-main`
- 执行路径：项目本地 `tools/ops/local/srv-ops/bin/srvctl`

## 执行内容

1. 在 Git 忽略的本地 srv-ops profile 中新增 `certmuse-pg`；通过应用主机的受控 SSH 隧道访问本机 PostgreSQL。
2. 创建 `certmuse` 数据库、非登录所有者 `certmuse_owner` 与最小权限应用账号 `certmuse_app`；数据库仅供本机后端访问。
3. 导入 RuoYi-Vue-Plus PostgreSQL 主库和 SnailJob 脚本：`postgres_ry_vue.sql`、`postgres_ry_job.sql`。
4. 首次导入发现上游主库脚本的隐式 CAST 需要超级用户；删除仅包含首次初始化数据的半成品库与专用角色，修复角色切换后完成干净重导入。
5. 删除 77 与本机的临时初始化脚本；未输出或提交数据库密码。

## 验证结果

- `certmuse-pg` 以 `certmuse_app` 成功连接 `certmuse`。
- `public` schema 已有 47 张表。
- 初始 `sys_user` 数据为 3 条。
- 工作流与 AI 脚本未导入，待对应功能启用时由 Deployer 发布期迁移执行。

## 后续前置条件

77 还没有 `certmuse` 系统服务账号，因此 `/etc/certmuse/runtime.env` 尚未安装。首次应用发布由 RouteFlow Deployer 的 preflight 创建服务账号与目录后，再通过受控 srv-ops 安装该私有运行时文件。
