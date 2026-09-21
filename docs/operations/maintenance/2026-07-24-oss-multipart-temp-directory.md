# OSS 上传临时目录修复

- 日期：2026-07-24（Asia/Taipei）
- 目标：`certmuse-app`（77）
- 发布路径：RouteFlow Deployer；服务与目录回读：项目本地 `srvctl`。

## 原因

生产 profile 沿用了上游容器路径 `/ruoyi/server/temp`。当前 CertMuse 以非容器 `certmuse` 服务账号运行，该账号无权在 `/ruoyi` 下创建 multipart 临时文件，因此 OSS 上传在请求解析阶段即以 `400 bad multipart` 失败，尚未进入 MinIO。

## 修复

- 将 `spring.servlet.multipart.location` 改为 `/app/certmuse/tmp`。
- 在 CertMuse 私有 Deployer profile 的 bootstrap 目录中声明 `/app/certmuse/tmp`，并随 `/app/certmuse` 统一设为 `certmuse:certmuse`。
- 完成后端生产构建，执行 Deployer dry-run 后发布新 JAR，并重启 `certmuse.service`。

## 验证

- Deployer 发布成功，Nginx 配置测试和 reload 成功。
- `certmuse.service`、`minio.service` 均为 `active`。
- `/app/certmuse/tmp` 为 `certmuse:certmuse`，权限 `0755`。

完整应用层回归需以拥有 `system:oss:upload` 权限的已登录用户上传一份非敏感小文件，并在确认后删除该对象。
