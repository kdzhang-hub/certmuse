# CertMuse 首次发布与 SSH 密钥切换准备

- 日期：2026-07-24（Asia/Taipei）
- 目标：`certmuse-app`（77，`cm.jklin.me`）
- 应用发布路径：项目本地 RouteFlow Deployer
- 主机维护路径：项目本地 `tools/ops/local/srv-ops/bin/srvctl`

## 发布

1. 已完成目标化 Deployer dry-run，随后发布 plus-ui 生产静态文件、Spring Boot JAR、`certmuse.service` 与 Nginx 站点配置。
2. 发布后 `certmuse.service` 为 `active`；`https://cm.jklin.me/` 经本机 TLS 解析返回 HTTP 200。
3. 后端匿名 Actuator health 返回 401，符合生产环境仅保留受保护 health/info 的配置。实际登录验证码端点为 `/prod-api/auth/code`，已返回 `code: 200`；旧的 `/captchaImage` 路径不存在。
4. 首次发布发现 Deployer 上传的运行时环境文件权限为 `root:root 0644`。已通过 srvctl 保留内容并回装为 `root:certmuse 0640`，重启后服务仍为 active。
5. 私有 Deployer component 已移除 `runtime.env` 上传项，并删除其本地旧副本；后续仅由 srvctl 安装或更新该私有文件，避免下一次发布回退权限。
6. 登录图片验证码的 IP 限流从每分钟 10 次提高至每分钟 30 次，仍保留防刷限制；已随后端 JAR 发布生效。

## SSH 密钥切换准备

1. 在 Git 忽略的 CertMuse srv-ops profile 下生成独立 `ed25519` 密钥，并以 `jucher:jucher`、目录 `0700`、文件 `0600` 安装其公钥到 77 的 `authorized_keys`。
2. srv-ops profile 已改用该密钥，并以 `srvctl exec certmuse-app uptime` 成功验证连接。
3. 当前 SSH 密码认证没有关闭；待明确授权后，先校验 sshd 配置、重载服务并再次验证密钥访问，才禁用密码认证。

## 边界

- 不记录密码、私钥、运行时环境变量、授权密钥内容或私有 profile。
- 应用产物、systemd 与 Nginx 继续只由 Deployer 发布；VPS、数据库、Redis、运行时环境与 SSH 维护继续只由 srvctl 执行。
