# `cm.jklin.me` TLS 证书

CertMuse 使用 Let’s Encrypt 的 DNS-01 校验申请 `cm.jklin.me` 证书。该方式沿用 `s2a.jklin.me` 的 `acme.sh + dns_cf` 基准，不依赖域名公网 A/AAAA 解析到 77，因此可与当前内网 CoreDNS 记录并存。

## 77 上的固定约定

- ACME 客户端：Debian `acme.sh`，以 root 运行，使证书续期可直接安全写入 Nginx 的 root 管理目录。
- DNS 校验：Cloudflare `dns_cf`；凭据仅保存在 77 的 root 私有 acme 配置，不写入仓库、文档或聊天。
- 私钥：`/etc/nginx/cert/cm.jklin.me.key`。
- 完整证书链：`/etc/nginx/cert/cm.jklin.me.pem`。
- Nginx 站点：`/etc/nginx/conf.d/certmuse.conf`，模板为 [certmuse.conf.example](../../infra/deploy/nginx/certmuse.conf.example)。
- 续期后的动作：执行受管脚本 `/usr/local/sbin/certmuse-reload-nginx`，其唯一动作是 `systemctl reload nginx`。
- 续期调度：`/etc/cron.d/certmuse-acme` 每日 03:17 以 root 执行 `acme.sh --cron --home /root/.acme.sh`。

## 执行边界

- 证书申请、安装、续期验证与 Nginx 的基础 TLS 配置属于主机运维，由项目本地 `srvctl` 完成；必须先检查命令和文件策略。
- 前端、后端与应用服务发布仍由 RouteFlow Deployer 负责，不因证书操作改走 `srvctl`。
- 申请前须确认 77 有经授权的 Cloudflare DNS 凭据；不得读取、展示、提交或复制其明文内容。
- 每次申请或续期后，先运行 `nginx -t`，再 reload Nginx，并用 HTTPS 连接验证域名与证书链。
- 前端尚未发布时，Nginx 的静态根目录可能返回应用层 `500`；使用 `--resolve cm.jklin.me:443:127.0.0.1` 的 HTTPS 请求成功建立连接即可验证本机 TLS，应用可用性由 Deployer 发布后另行验证。
