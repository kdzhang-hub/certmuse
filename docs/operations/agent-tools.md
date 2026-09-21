# 本地 Agent 工具：macOS、Windows 与 Linux

CertMuse 在开发机上使用两套本地工具：RouteFlow Deployer 直接发布应用；`srvctl` 用于日常 VPS、PostgreSQL、Redis 与资料库操作。公共二进制、私有 profile 和认证材料均安装在本仓库的 Git 忽略目录 `tools/ops/local/`，每台机器各自导入，绝不复制到 Git。

## 支持范围

| 平台 | 架构 | 安装入口 | 状态 |
| --- | --- | --- | --- |
| macOS | Apple Silicon (`arm64`) | `tools/ops/certmusectl install` | 支持 |
| Linux | `amd64` | `tools/ops/certmusectl install` | 支持 |
| Windows | `amd64` | `./tools/ops/certmusectl.ps1 install` | 支持 |
| macOS | Intel (`x86_64`) | — | 当前公开发布未提供包，不可自行替用其他架构 |

所有公共包均以 [agent-tools.lock](../../infra/ops/agent-tools.lock) 中固定的版本与 SHA-256 下载并校验。

## macOS 与 Linux

```bash
tools/ops/certmusectl install
tools/ops/certmusectl doctor
```

将授权的加密 srv-ops profile 导入本机后，在项目根目录直接调用：

```bash
tools/ops/local/srv-ops/bin/srvctl \
  -config tools/ops/local/srv-ops/.srv-ops-local/config.yaml \
  -env tools/ops/local/srv-ops/.srv-ops-local/env/passwords.env \
  server list
```

授权的 RouteFlow 私有 overlay 置入 `tools/ops/local/routeflow/`，至少包含 `tools/deployer/certmuse.yml` 与 `platform/linux/hosts/certmuse-app/host.yml`。随后先 dry-run：

```bash
tools/ops/certmusectl deployer -config certmuse.yml -platform linux -host certmuse-app -components certmuse -dry-run
```

## Windows PowerShell

在仓库根目录以 PowerShell 执行：

```powershell
.\tools\ops\certmusectl.ps1 install
.\tools\ops\certmusectl.ps1 doctor
```

导入授权的 srv-ops profile 后，直接调用本地二进制：

```powershell
.\tools\ops\local\srv-ops\bin\srvctl.exe `
  -config .\tools\ops\local\srv-ops\.srv-ops-local\config.yaml `
  -env .\tools\ops\local\srv-ops\.srv-ops-local\env\passwords.env `
  server list
```

将授权的 RouteFlow overlay 解压到 `tools\ops\local\routeflow\` 后，先 dry-run：

```powershell
.\tools\ops\certmusectl.ps1 deployer -config certmuse.yml -platform linux -host certmuse-app -components certmuse -dry-run
```

如执行策略限制本地脚本，可在当前进程临时放开：`Set-ExecutionPolicy -Scope Process Bypass`。不得降低系统级执行策略，不得将 profile、`host.yml`、密钥或密码复制到仓库。

## 职责边界

- Deployer：前端、后端、systemd、Nginx 与发布期迁移；必须先 `-dry-run`。不上传 `/etc/certmuse/runtime.env` 或其他生产密钥。
- `srvctl`：VPS、PostgreSQL、Redis、资料库的日常查询、维护与分析；变更前必须 `policy check`。
- 每台 macOS、Windows 或 Linux 开发机分别导入自己获授权的私有资料；同一私有 profile 不作为跨机器共享文件。
