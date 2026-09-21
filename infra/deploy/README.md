# CertMuse 部署声明

本目录保存 CertMuse 的**可提交部署声明与模板**，由 RouteFlow Deployer 直接使用私有 profile 发布应用。

- `deployer/`：发布步骤、部署目录和健康检查约定。
- `nginx/`：Nginx 站点模板。
- `systemd/`：后端服务模板。
- `scripts/certmuse-migrate-postgres.sh`：按 SHA-256 记录已执行脚本的发布期 PostgreSQL 迁移器；已执行脚本内容发生变化时拒绝继续发布。

真实 `host.yml`、认证材料和由 Deployer 暂存的构建产物必须保留在 Git 忽略的 `tools/ops/local/routeflow/`。日常服务器与数据库操作不在此目录定义，使用 `infra/ops/srv-ops/` 与 `srvctl`。
