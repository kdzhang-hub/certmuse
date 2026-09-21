# VPS 与数据库操作

CertMuse 的 VPS、PostgreSQL、Redis 与资料文件的日常查询、维护和分析，直接调用项目本地的 `srvctl`。应用前端、后端、systemd、Nginx 及发布期迁移由 RouteFlow Deployer 直接部署，不经 `srv-ops` 转发。

```bash
tools/ops/local/srv-ops/bin/srvctl \
  -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml \
  -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env \
  <srvctl 参数>
```

这是对项目本地 `srvctl` 的直接调用；私有配置和密码环境文件均保持 Git 忽略。不得改用 `../srv-ops` 的运行文件、系统 `ssh`、`scp`、`sftp`、直连数据库客户端或复制 `.srv-ops-local`。

macOS、Windows 与 Linux 的工具安装、私有 profile 导入及平台命令见[本地 Agent 工具说明](agent-tools.md)。

`cm.jklin.me` 的证书申请与 Nginx TLS 配置见[证书说明](certificates.md)。
PostgreSQL 的实例、角色、初始化范围与运行时配置见[数据库说明](database.md)。

## 当前应用入口

- 逻辑应用主机：`certmuse-app`。
- 应用域名：`cm.jklin.me`。
- 内网 DNS：由 RouteFlow 的 `ucg-max-work` CoreDNS 托管，解析至 `192.168.6.77`。

后续与 CertMuse 主机或应用入口有关的配置、健康检查和部署文档均使用 `certmuse-app` 与 `cm.jklin.me`；不得改用其它项目的域名。

## 准备与边界

1. 先执行 `tools/ops/certmusectl doctor`。只有公共工具已安装、且经授权导入私有 profile 后，才能继续。
2. 私有 profile、SSH key、密码、审计文件均保留在 `tools/ops/local/`，该目录必须保持 Git 忽略。
3. 仓库仅提交逻辑需求：[srv-ops requirements](../../infra/ops/srv-ops/requirements.yml)，不提交真实主机地址、数据库密码或策略文件。
4. 当前实际 profile 已提供 `certmuse-app`、`certmuse-pg` 与 `certmuse-redis`；不得以其它项目的 profile 替代。
5. `certmuse-app` 的日常连接使用 `jucher` 专用 SSH 私钥；私钥只保留在 Git 忽略的 profile 目录。密码认证在密钥验证完成后的过渡期内保留，只有明确授权后才可通过受控变更禁用。

## 标准流程

每次操作都先核对当前授权目标，避免使用陈旧的主机或数据库映射：

```bash
tools/ops/local/srv-ops/bin/srvctl -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env server list
tools/ops/local/srv-ops/bin/srvctl -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env db list
tools/ops/local/srv-ops/bin/srvctl -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env server info certmuse-app
tools/ops/local/srv-ops/bin/srvctl -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env db info certmuse-pg
```

读取 VPS 状态时，先执行最小只读检查：

```bash
tools/ops/local/srv-ops/bin/srvctl -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env policy check certmuse-app --cmd "uptime"
tools/ops/local/srv-ops/bin/srvctl -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env exec certmuse-app "uptime"
```

读取 PostgreSQL 时，先检查数据库 profile 和 SQL 策略：

```bash
tools/ops/local/srv-ops/bin/srvctl -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env db info certmuse-pg
tools/ops/local/srv-ops/bin/srvctl -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env db query certmuse-pg "select now()"
```

## 备份与恢复 srv-ops profile

备份归档先保存在 Git 忽略的项目临时目录
`tools/ops/local/srv-ops/.srv-ops-local/tmp/`，再上传至 `certmuse-app` 的
`/app/certmuse-backups/srv-ops/`；同时更新远端的 `latest.srvops.enc`。
公司内网通过 `https://cm.jklin.me/ops-backup/` 只读提供该目录中的具名加密
归档（目录列表关闭，不能上传或浏览）。macOS/Linux 工作站使用以下脚本，密码
提示始终由 `srvctl` 处理：

```bash
tools/ops/certmusectl srv-ops-backup
```

如需指定归档名：

```bash
tools/ops/certmusectl srv-ops-backup \
  --file certmuse-srv-ops-YYYYMMDD.tar.gz.enc
```

恢复时，脚本通过 HTTPS 从 `cm.jklin.me` 下载归档到 Git 忽略的项目临时目录
`tools/ops/local/srv-ops/.srv-ops-local/tmp/backups/`，再提示输入密码并离线
导入；首次工作站也可直接恢复最新备份：

```bash
tools/ops/certmusectl srv-ops-import
```

如需恢复指定归档：

```bash
tools/ops/certmusectl srv-ops-import \
  --file certmuse-srv-ops-YYYYMMDD.tar.gz.enc
```

远程下载后，脚本以 `local import --local-file` 恢复；归档会保留在项目临时
目录中，便于在密码错误或合并冲突时排查，且不进入 Git。

如 HTTPS 下载不可用，也可将经授权取得的加密归档放到本地后离线导入：

```bash
tools/ops/certmusectl srv-ops-import \
  --local-file ~/Downloads/certmuse-srv-ops-YYYYMMDD.tar.gz.enc
```

离线导入会创建本地 profile；之后再使用上面的 HTTPS 下载脚本同步更新。所有导入
都会校验归档 manifest；既有的不同内容不会被覆盖，而是放入
`.srv-ops-local/tmp/import-conflicts/<timestamp>/`。归档、私钥和密码环境文件均
必须保持 Git 忽略，不得通过公开链接或聊天工具传输。

## 变更规则

- 任何主机命令、文件上传、服务重启或数据库写入前，先运行匹配的 `policy check`；策略拒绝时停止，不尝试绕过。
- 数据库读取使用 `db query`；迁移或受控写入使用 `db exec`，并仅执行已审核的 PostgreSQL 脚本。
- 每次变更必须记录目标逻辑名称、执行内容、验证结果和时间；不要记录密码、令牌或 profile 内容。
- 已完成的主机维护记录置于 [maintenance/](maintenance/)。
- 应用发布（前端、后端、对应服务与发布期迁移）由 RouteFlow Deployer 直接执行；`srvctl` 不参与该链路。

## 禁止项

- 禁止人工直接调用 `ssh`、`scp`、`sftp`、`psql`、`redis-cli` 操作生产目标；仅可使用受控的 Deployer 发布路径或本项目 `srvctl` 运维路径。
- 禁止将 `tools/ops/local/`、`.srvops.enc`、私有 `host.yml`、密钥或密码提交到 Git。
- 禁止把其它项目的 srv-ops profile 复制为 CertMuse profile。
