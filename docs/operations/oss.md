# OSS：本机 MinIO

CertMuse 已在 `certmuse-app`（77）启用本机 MinIO，作为应用上传文件的私有对象存储。日常服务状态、权限、对象存储维护和数据库查询均通过项目本地 `srvctl` 执行；应用发布仍只由 RouteFlow Deployer 执行。

## 运行布局

```text
/app/certmuse-oss/data          # MinIO 对象持久数据
/etc/minio/minio.env            # MinIO 服务根凭据，root:minio 0640
/etc/systemd/system/minio.service
/usr/local/bin/minio
/usr/local/bin/mc
/etc/certmuse/runtime.env       # CertMuse 应用 MinIO 访问凭据，root:certmuse 0640
```

- MinIO API 只监听 `127.0.0.1:9000`；Console 只监听 `127.0.0.1:9001`。
- bucket 为 `certmuse`，匿名访问为 private。应用使用独立的 `certmuse-app` MinIO 服务账号，按读写所需权限访问该 bucket。
- Console 不经 Nginx 发布，也不配置公开域名；需要管理时只能经受控运维路径访问。
- 数据库 `sys_oss_config[minio]` 仅保存 endpoint、bucket、private 策略与启用状态。访问密钥由 `/etc/certmuse/runtime.env` 的 `CERTMUSE_OSS_MINIO_ACCESS_KEY` 和 `CERTMUSE_OSS_MINIO_SECRET_KEY` 注入，不写入数据库、迁移 SQL、Deployer 声明或 Git。

## 与原始资料库的边界

MinIO 仅保存应用 OSS 对象。原始教材、试卷 PDF、答案原件及 OCR 中间文件仍按 [部署目录与流程](deployment.md#原始资料库) 放在 `/app/certmuse-references/`，通过 srv-ops 的受控文件操作同步，既不放入 bucket，也不由 Deployer 发布。

## 运维与验证

操作前先查看本地 srv-ops profile 和 policy，随后使用最小范围命令确认 `minio.service` 与 `certmuse.service` 均为 active；数据库查询只回读非敏感 OSS 字段和菜单可见性。不得以 SSH、SCP、SFTP、直连 `psql`、`mc` 明文凭据或查看 env 文件的方式绕过控制面。

应用层完整上传回归需要使用已认证的 CertMuse OSS 页面或 API 上传一份非敏感测试文件，并在验证后删除。系统服务、bucket 私有策略和应用服务账号权限已完成受控验证；此文档不记录凭据内容。
