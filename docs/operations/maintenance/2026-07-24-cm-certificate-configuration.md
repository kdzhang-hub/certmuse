# `cm.jklin.me` 证书配置完成记录

- 日期：2026-07-24（Asia/Taipei）
- 目标：`certmuse-app`（192.168.6.77）
- 执行路径：CertMuse 项目本地 `srvctl`

## 配置结果

- CA：Let’s Encrypt；校验方式：Cloudflare DNS-01（`dns_cf`）。
- 证书：ECC P-256，签发时间 `2026-07-24T04:29:03Z`，acme.sh 下次续期时间 `2026-09-21T04:29:03Z`。
- 私钥：`/etc/nginx/cert/cm.jklin.me.key`（root、`0600`）。
- 完整证书链：`/etc/nginx/cert/cm.jklin.me.pem`（root、`0644`）。
- Nginx 站点：`/etc/nginx/conf.d/certmuse.conf`，来自 `infra/deploy/nginx/certmuse.conf.example`。
- 续期 hook：`/usr/local/sbin/certmuse-reload-nginx`，仅 reload Nginx。
- 续期调度：`/etc/cron.d/certmuse-acme`，每日 03:17 执行。

## 验证结果

- `nginx -t` 成功，Nginx reload 后保持 `active`。
- 使用 `--resolve cm.jklin.me:443:127.0.0.1` 的 HTTPS 请求完成 TLS 握手。
- `acme.sh --cron --home /root/.acme.sh` 试运行正常；证书未到续期窗口，因此按预期跳过。
- 前端尚未发布到 `/app/certmuse/frontend`，当前 HTTPS 请求返回应用层 `500`；这不影响 TLS，待 Deployer 发布前端后再做应用健康检查。
