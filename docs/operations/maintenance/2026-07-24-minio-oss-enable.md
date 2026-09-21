# CertMuse 本机 MinIO OSS 启用

- 日期：2026-07-24（Asia/Taipei）
- 目标：`certmuse-app`（77）
- 执行边界：VPS、MinIO 与数据库操作使用项目本地 `srvctl`；应用构建与发布使用 RouteFlow Deployer。

## 变更

- 安装并启用 MinIO 服务，持久对象数据目录为 `/app/certmuse-oss/data`。
- API 与 Console 分别仅绑定 `127.0.0.1:9000` 和 `127.0.0.1:9001`；Console 未由 Nginx 对外发布。
- 创建私有 bucket `certmuse`，并为 CertMuse 创建独立 MinIO 服务账号。
- 将应用访问密钥仅写入 `/etc/certmuse/runtime.env`，由 Spring 环境配置读取；数据库只保存非敏感 endpoint、bucket、private 策略和启用状态。
- 执行 `20260724_enable-minio-oss.sql`，启用 `minio` OSS 配置并恢复 OSS 文件管理菜单。
- 调整后端加载逻辑，禁止生产环境回退使用上游 SQL 中的示例 MinIO 凭据；完成构建并经 Deployer 发布。

## 验证

- `minio.service` 与 `certmuse.service` 均为 `active`。
- 私有 bucket 创建完成，匿名访问为 private；独立应用账号可按授权访问该 bucket。
- `sys_oss_config[minio]` 已回读为启用、私有、loopback endpoint；OSS 菜单已恢复可见。

未记录或输出 MinIO 根凭据、应用服务账号密钥、运行时 env 内容或其他私密信息。原始资料仍保留在 `/app/certmuse-references/`，不进入 MinIO。
