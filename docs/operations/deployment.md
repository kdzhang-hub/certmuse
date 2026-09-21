# CertMuse 生产部署流程

本文是 CertMuse 生产环境的唯一常规发布流程。生产应用由 RouteFlow Deployer 发布，`srv-ops` 仅用于受控的服务器和数据库只读核验、资料同步或已授权的紧急维护；不要用 SSH、SCP、SFTP、服务器上的 `git pull` 或直连数据库替代本流程。

发布入口：

```powershell
.\tools\ops\deploy-production.ps1
```

该脚本会停止在首个失败阶段，并输出失败阶段、原因和中文处理建议。完成条件不是“文件上传完成”，而是数据库迁移、服务、两个前端入口和最终一致性检查全部通过。

## 发布范围和职责

| 内容 | 责任工具 | 说明 |
| --- | --- | --- |
| 后端 JAR、管理端和学习端静态文件、Nginx/systemd 配置 | RouteFlow Deployer | 从本机构建产物同步到服务器并重启应用。 |
| PostgreSQL 应用迁移 | RouteFlow Deployer | 在服务重启前执行受管迁移并记录 SHA-256。 |
| 生产库只读查询、服务状态复核、资料同步 | `srvctl` / `srv-ops` | 不是正常发布通道。 |
| 本地 Compose 数据库迁移 | `tools/ops/migrate-local-database.ps1` | 仅本地环境，不能用于生产。 |

生产架构是 `certmuse.service`（Java 8080）+ Nginx + PostgreSQL、Redis、MinIO。应用受管目录为 `/app/certmuse`；数据库、日志、临时文件、MinIO 对象和 `/app/certmuse-references` 均不应被发布覆盖。

关键受管路径：

- 后端 JAR：`/app/certmuse/backend/certmuse-admin.jar`
- 迁移目录：`/app/certmuse/backend/migrations/`
- 管理端与学习端静态文件：`/app/certmuse/frontend/`
- Nginx 配置：`/etc/nginx/conf.d/certmuse.conf`
- systemd 单元：`/etc/systemd/system/certmuse.service`

私有 Deployer profile、部署私钥、服务器配置和运行时密码位于 Git 忽略的 `tools/ops/local/`。不得把 `runtime.env`、密码、私钥或构建产物提交到 Git。

## 发布前准备

1. 进入仓库根目录，并确认 PowerShell 7 可用：

   ```powershell
   pwsh --version
   ```

2. 发布脚本只发布当前本地的 `main` 提交，不会自动拉取或比较远程分支。先安全更新到需要发布的远程版本：

   ```powershell
   git fetch origin main --prune
   git status --short
   git pull --ff-only origin main
   ```

   工作区有未提交内容时，先提交，或明确保存为 stash；不要用 `-AllowWorkingTreeChanges` 将未知本地修改发布到生产。

3. 检查本地发布工具和私有 profile：

   ```powershell
   .\tools\ops\certmusectl.ps1 doctor
   ```

   必须看到 RouteFlow runtime、`srvctl.exe`、CertMuse Deployer 私有配置和主机 profile 均可用。缺失时先导入受控的 `srv-ops` / RouteFlow overlay，不要临时替换私钥或手改服务器配置。

4. 任何代码、运行、Git 或部署操作涉及数据库时，先确认数据库变更已经写成迁移。生产没有本地 Compose 使用的 `certmuse_meta.schema_migration` 表；生产发布迁移的状态由服务器上的 Deployer 标记目录维护。因此生产库核验使用受控 `srvctl` 查询表、字段、约束或索引，不能把本地迁移表缺失当成数据库故障。

## 数据库迁移规则

生产迁移目录是：

```text
源码：backend/script/sql/postgres/certmuse/
发布 rootfs：tools/ops/local/routeflow/platform/linux/rootfs/app/certmuse/backend/migrations/
Deployer 清单：tools/ops/local/routeflow/tools/deployer/certmuse.yml 的 data_files
生产状态标记：/var/lib/certmuse-deployer/migrations/<migration>.sha256
```

每个发布迁移必须同时出现在前三处。`deploy-production.ps1` 会检查已有 rootfs 副本的 SHA-256；只会补充此前未发布的新文件和清单条目，绝不会按修改时间覆盖已有副本。

规则如下：

- 一旦同名迁移已在生产执行，它就是冻结文件。不得修改、重命名、删除、重新记录 SHA-256，也不得手动重放。
- 后续数据库或数据修复必须新增一个按日期命名的 SQL，例如 `20260820_add-xxx.sql`；使用事务、幂等 DDL/DML 和明确的失败条件。
- 远程迁移器对每个 SQL 先计算 SHA-256：有匹配标记时输出 `[SKIP]`，无标记时输出 `[APPLY]`，成功后输出 `[OK]` 并写入标记。已执行脚本的哈希不同会输出 `Applied migration checksum changed` 并停止发布。
- 历史上明确批准的源码/rootfs 差异只能保留为冻结副本；新需求不能沿用这种例外。
- 不要同步整库、删除生产特有的 `sj_*` 表，或用本地 Compose 的数据覆盖生产数据。

## 一键发布的执行阶段

运行：

```powershell
.\tools\ops\deploy-production.ps1
```

脚本按以下顺序执行，任何阶段失败均不会继续下一阶段：

1. 检查当前分支必须为 `main`、工作区必须干净，并记录当前本地提交 SHA。
2. 运行 `doctor.ps1`，检查 `srvctl`、RouteFlow Deployer 和私有 profile。
3. 管理端质量门禁：类型检查、lint、单元测试和生产构建。
4. 学习端质量门禁：lint、单元测试和生产构建。
5. 后端完整测试：`backend\mvnw.cmd -Dmaven.test.skip=false test`。
6. 后端正式构建：`backend\mvnw.cmd clean package`。
7. 校验并同步迁移到 RouteFlow rootfs 和私有 Deployer 清单。
8. 同步 JAR、两个前端 `dist` 和生产 Nginx 模板到 RouteFlow rootfs。学习端内容位于 `/student-assets/`，服务器入口由 Nginx 指向 `student-index.html`。
9. 执行一次 Deployer `-dry-run`；确认将上传的文件和新增迁移符合预期。
10. 正式部署。服务器先投放受管文件，再运行迁移器；全部迁移成功后才执行 `systemctl daemon-reload`、enable/restart `certmuse.service`、`nginx -t` 和 Nginx reload。
11. 部署后验收：根首页、`/learning/home`、`/prod-api/auth/code` 返回 200；学习端 HTML 必须引用 `/student-assets/`；`certmuse.service` 必须为 `active`。
12. 再执行一次 dry-run，必须出现：

    ```text
    No changes detected, everything is up to date
    ```

只有出现脚本末尾的“部署成功：代码、数据库迁移、服务和页面均已通过检查。”才可宣布生产部署成功。

## 单独预演

仅查看变更计划、不会写服务器时，可运行：

```powershell
.\tools\ops\certmusectl.ps1 deployer `
  -config certmuse.yml `
  -platform linux `
  -host certmuse-app `
  -components certmuse `
  -dry-run
```

预演通过不是发布成功；它之后仍须完成完整质量门禁、实际迁移和部署后验收。正常发布应始终使用一键脚本，以免遗漏构建产物、迁移登记或验收步骤。

## 常见失败与处理

| 失败现象 | 正确处理 |
| --- | --- |
| 当前不是 `main`、工作区不干净 | 切换或 fast-forward 到目标 `main`；提交或 stash 本地内容后重试。不要发布未知工作区。 |
| `doctor` 缺少 runtime、profile 或密钥 | 导入已授权的本地 RouteFlow / srv-ops 配置，再重试；不要手工 SSH 发布。 |
| 前端类型检查、lint、测试或构建失败 | 修复第一个真实错误并重新运行完整脚本；不要跳过质量门禁。 |
| Maven 测试失败 | 以 Surefire 的 failures/errors 为准，修复后重跑完整后端测试；测试日志中的预期异常 `ERROR` 本身不等于失败。 |
| `Applied migration checksum changed` | 立即停止。恢复与服务器冻结副本一致的已发布 SQL，把真正的变化写成新的增量迁移。绝不改远程标记、覆盖历史文件或直接执行 SQL。 |
| 新迁移未进入 rootfs 或 `certmuse.yml` | 检查源码文件名、rootfs 副本和 `data_files` 三者；由脚本同步后重新预演。 |
| 重启后短暂 HTTP 502 | 脚本会每 5 秒重试，最多 12 次。若仍未恢复，先用 `srvctl` 检查 `certmuse.service` 与日志，再修复原因后重新发布。 |
| 学习端返回管理端页面 | 检查 Nginx 是否将学习端入口指向 `student-index.html`，以及 HTML 是否引用 `/student-assets/`；重新构建并走完整发布。 |
| 最终 dry-run 仍有变更 | 视为发布未收敛，检查未同步的文件或配置；不能跳过该检查。 |

## 回退和紧急处置边界

当前是直接部署模型，没有 `releases/current` 软链接和自动回退。若应用发布后必须回退，只能重新投放一个已验证的历史 Git 提交所构建出的完整发布包，并再次完成本流程。

数据库迁移不能通过修改或删除已执行 SQL 回退。需要补偿时，新增一个经过评审的前向修复迁移；涉及数据删除、整库恢复或生产数据重置时，必须另行明确授权、先做备份并使用受控 `srv-ops` 流程。

## 禁止事项

- 不在服务器上 `git pull`、构建 JAR 或构建前端。
- 不用 SSH/SCP/SFTP 直接上传 JAR、`dist`、Nginx 配置或 SQL。
- 不跳过测试、dry-run、迁移校验、服务健康检查或最终一致性检查。
- 不把本地 Compose 数据库、密码、`runtime.env`、私钥、日志、MinIO 对象或资料目录纳入应用发布。
- 不修改、覆盖或伪造已执行迁移的 SHA-256 标记。

部署相关的服务器目录和 Nginx/systemd 模板边界，参见 [部署声明](../../infra/deploy/README.md)；受控数据库查询和 profile 管理，参见 [srv-ops](srv-ops.md)。
