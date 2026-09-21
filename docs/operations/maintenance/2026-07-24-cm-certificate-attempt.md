# `cm.jklin.me` 证书申请记录

- 日期：2026-07-24（Asia/Taipei）
- 目标：`certmuse-app`（192.168.6.77）
- 执行路径：CertMuse 项目本地 `srvctl`

## 已完成

1. 以 `s2a.jklin.me` 的实现为基准，确定使用 Let’s Encrypt、`acme.sh`、Cloudflare `dns_cf` 与 ECC P-256。
2. 在 77 安装 Debian `acme.sh 3.1.1`。
3. 建立 root 私有 acme 配置和受管 Nginx reload 脚本；凭据的本机与远端临时副本均已删除。

## 未完成与原因

`acme.sh` 无法向 Cloudflare 写入 `_acme-challenge.cm.jklin.me` TXT 记录，返回 `Add txt record error`。因此未签发有效证书，不能部署 HTTPS Nginx 站点。

申请失败后生成的私钥和空证书链已从 `/etc/nginx/cert/` 删除；Nginx 站点配置未写入，现有服务未受影响。

## 初始失败后的处理

后续无密核对确认：76 与 77 的 acme 凭据摘要一致，公网出口 IP 一致；Cloudflare Token 验证成功。受控 debug 重试后，Cloudflare 成功创建 TXT 记录并签发证书。最终配置与验证见 [证书配置完成记录](2026-07-24-cm-certificate-configuration.md)。
