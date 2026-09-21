# CertMuse Redis 配置记录

- 日期：2026-07-24（Asia/Taipei）
- 目标：`certmuse-app`（77）的 Redis
- 执行路径：项目本地 `tools/ops/local/srv-ops/bin/srvctl`

## 初始状态

- Redis `8.0.2`，standalone，监听本机 IPv4/IPv6 回环地址。
- 数据库 `0` 无 key，未启用认证。

## 执行内容

1. 在 Git 忽略的 srv-ops profile 中增加 `certmuse-redis`，通过应用主机的受控 SSH 隧道连接本机 Redis。
2. 保留 Debian 默认 Redis 配置，仅增加随机 `requirepass` 与 `appendonly yes`；AOF fsync 保持系统默认的 `everysec`。
3. 将后端生产配置切换为 `CERTMUSE_REDIS_*` 环境变量，并使用 `certmuse:` Redisson key 前缀。
4. 回装配置后重启 Redis，删除本机及 77 上含认证信息的临时副本。

## 验证结果

- 经 `certmuse-redis` 认证 profile 返回 `PONG`。
- `aof_enabled:1`，`aof_last_write_status:ok`。
- `redis-server.service` 为 `active (running)`。

首次 Deployer 发布创建 `certmuse` 系统账号后，Redis 密钥与数据库密钥一并通过受控 srv-ops 安装到 `/etc/certmuse/runtime.env`；不得提交或写入 Deployer 公开声明。
