# PostgreSQL 数据库

## 生产实例

- 主机：`certmuse-app`（77），PostgreSQL `16-main`；应用仅通过 `127.0.0.1:5432` 连接，不对局域网暴露数据库端口。
- 数据库：`certmuse`，编码 `UTF8`，排序与字符分类均为 `C.UTF-8`，时区固定为 `Asia/Taipei`。
- 所有者：`certmuse_owner`（`NOLOGIN`）；应用账号：`certmuse_app`（最小表、序列读写权限，不拥有 schema 的创建权限）。密码只存在于 Git 忽略的本地 `srv-ops` 环境文件，后续仅安装到 77 的 `/etc/certmuse/runtime.env`。
- 日常数据库入口：项目本地 `srvctl` 的 `certmuse-pg` profile。它通过 `certmuse-app` SSH profile 隧道连接 `127.0.0.1:5432`，不使用直连 `psql`。

## 已导入的基础模块

首期已导入以下 PostgreSQL 脚本：

- `backend/script/sql/postgres/postgres_ry_vue.sql`
- `backend/script/sql/postgres/postgres_ry_job.sql`

`postgres_ry_workflow.sql` 和 `postgres_ry_ai.sql` 暂未导入；仅在启用工作流或 AI 模块的发布期，由 RouteFlow Deployer 的受审核迁移步骤导入。

CertMuse 的本地补充迁移位于 `backend/script/sql/postgres/certmuse/`。已执行 `20260724_hide-disabled-menus.sql`，仅将未启用模块的菜单标记为隐藏；不会删除菜单、角色权限或基础数据。随后已执行 `20260724_enable-minio-oss.sql`，启用本机 MinIO 的非敏感元数据并恢复 OSS 文件管理菜单；凭据始终留在运行时文件中。

`postgres_ry_vue.sql` 末尾的隐式 `varchar -> timestamptz` CAST 要求 PostgreSQL 超级用户。脚本在该小段前暂时退出应用所有者角色、完成 CAST 后再恢复；其余业务对象由 `certmuse_owner` 持有。

### 教材原始 PDF 附件权限

`20260819_grant-textbook-original-pdf-privileges.sql` 为后基线创建的
`cm_textbook_original_pdf` 表授予运行账号 `certmuse_app` 所需的
`SELECT`、`INSERT`、`UPDATE`、`DELETE` 权限。该迁移必须由 RouteFlow Deployer
在 dry-run 后执行，不能用临时 SQL 直连生产数据库。只有同步回退教材 PDF
附件功能后，才允许通过受控迁移撤销这些权限；单独撤权会使查询和上传失败。

## 后端生产配置

`backend/ruoyi-admin` 已使用 PostgreSQL JDBC 驱动。生产 profile 读取以下环境变量：

```text
SPRING_PROFILES_ACTIVE=prod
CERTMUSE_DB_URL=jdbc:postgresql://127.0.0.1:5432/certmuse?ApplicationName=certmuse
CERTMUSE_DB_USERNAME=certmuse_app
CERTMUSE_DB_PASSWORD=<仅限私有运行时文件>
```

`CERTMUSE_DB_PASSWORD` 不得写入 `application-prod.yml`、Deployer 声明或 Git。首次正式发布前，RouteFlow Deployer 的 preflight 应创建 `certmuse` 系统账号和目录；随后以受控 `srvctl` 将运行时文件安装为 `/etc/certmuse/runtime.env`，属主 `root:certmuse`、权限 `0640`，再由 Deployer 安装并启动 systemd 服务。

## 日常验证

```bash
tools/ops/local/srv-ops/bin/srvctl \
  -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml \
  -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env \
  db query certmuse-pg "select current_database(), current_user"
```

查询、维护与分析走 `srvctl`；前后端发布、systemd、Nginx 和发布期迁移仅走 RouteFlow Deployer，并且必须先 dry-run。

## Redis 缓存

- 实例：77 的 Redis `8.0.2`，独立逻辑 profile 为 `certmuse-redis`，仅监听 `127.0.0.1` 和 IPv6 回环地址。
- 认证：已启用随机 `requirepass`；认证密钥仅存在于 Git 忽略的本地 srv-ops 环境文件，首次应用发布时与数据库密钥一并写入 `/etc/certmuse/runtime.env`。
- 持久化：AOF 已启用，策略为 `appendfsync everysec`。缓存 key 统一使用 `certmuse:` 前缀，逻辑数据库为 `0`。
- 生产后端通过 `CERTMUSE_REDIS_HOST`、`CERTMUSE_REDIS_PORT`、`CERTMUSE_REDIS_DATABASE` 与 `CERTMUSE_REDIS_PASSWORD` 配置；未设置时仅 host、port、database 使用本机默认值，密码必须由运行时环境提供。

```bash
tools/ops/local/srv-ops/bin/srvctl \
  -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml \
  -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env \
  db query certmuse-redis "ping"
```

## OSS 配置

本机 MinIO 已启用：数据库仅保留 `minio` 配置的 endpoint、bucket、私有访问策略和启用状态；访问密钥不写入 `sys_oss_config` 或迁移 SQL。应用通过 `/etc/certmuse/runtime.env` 注入 MinIO 密钥，再由 Spring 环境覆盖加载。完整运行边界见 [OSS](oss.md)。
