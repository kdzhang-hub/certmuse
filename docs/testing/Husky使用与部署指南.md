# Husky 使用与部署指南

## 1. Husky是什么

Husky是Git Hooks管理工具，用于在本地执行 `git commit`、`git push` 等操作时，自动调用项目已有的检查和测试命令。

```text
开发者执行Git操作
        ↓
Git触发对应Hook
        ↓
Husky执行配置的命令
        ↓
全部命令返回0：Git操作继续
任一命令返回非0：Git操作终止
```

Husky不是测试框架，不会自行理解业务、生成测试或判断测试结果。实际检查工作由项目采用的工具完成，例如：

- 前端代码规范：Oxlint、ESLint；
- 前端类型检查：TypeScript、`vue-tsc`；
- 前端单元测试：Vitest、Jest；
- 前端构建：Vite、Webpack；
- 后端单元测试：JUnit、Mockito；
- 后端构建：Maven、Gradle；
- 容器配置：Docker Compose。

Husky的职责只是确定“何时自动执行这些命令”。

## 2. 常见Git Hooks

### pre-commit

在Git创建提交之前执行。适合快速检查：

- 修改文件的lint；
- 格式检查；
- 少量快速单元测试；
- 提交内容检查。

`pre-commit`应尽量保持快速，否则频繁提交会明显影响开发体验。

### commit-msg

在提交信息写入后执行。适合检查：

- Conventional Commits格式；
- Issue编号；
- 提交信息长度；
- 禁止无意义提交信息。

### pre-push

在Git向远端发送提交之前执行。适合较完整的检查：

- 全部前端单元测试；
- 前端类型检查；
- 前端生产构建；
- 后端单元测试；
- 后端编译；
- 配置文件校验。

`pre-push`通常比 `pre-commit`耗时更长，但仍不适合执行长时间性能测试或需要复杂环境的完整端到端测试。

## 3. 项目环境要求

部署前检查：

```powershell
git --version
node --version
pnpm --version
java --version
docker --version
docker compose version
```

CertMuse前端要求：

```text
Node.js >= 20.19.0
pnpm >= 10.0.0
```

CertMuse后端使用JDK 21和Maven Wrapper。

### 启用pnpm

Node.js通常包含Corepack：

```powershell
corepack --version
corepack enable
corepack prepare pnpm@10 --activate
pnpm --version
```

Windows如果提示无法写入 `C:\Program Files\nodejs`，可以用管理员PowerShell执行一次：

```powershell
corepack enable
```

完成后，日常使用pnpm不需要管理员权限。

## 4. 在项目中安装Husky

以下命令从Git仓库根目录执行。

### 4.1 初始化根package.json

如果仓库根目录没有 `package.json`：

```powershell
pnpm init
```

对于同时包含Vue前端和Java后端的仓库，建议在根目录单独管理Husky，由根Hook统一调度两个子项目。

### 4.2 安装Husky

```powershell
pnpm add -D husky
```

Husky只用于开发阶段，因此应放在 `devDependencies`。

### 4.3 初始化Git Hooks

```powershell
pnpm exec husky init
```

初始化后通常生成：

```text
.husky/
└── pre-commit
```

根 `package.json` 应包含：

```json
{
  "scripts": {
    "prepare": "husky"
  }
}
```

`prepare`的作用是在其他开发者安装根依赖时自动激活Husky。

### 4.4 验证Hooks路径

```powershell
git config --get core.hooksPath
```

Husky 9通常返回：

```text
.husky/_
```

## 5. 配置检查命令

### 5.1 根package.json

可以在根 `package.json` 定义可重复使用的检查命令：

```json
{
  "scripts": {
    "prepare": "husky",
    "check:frontend": "pnpm --dir web/admin typecheck && pnpm --dir web/admin lint && pnpm --dir web/admin test:unit && pnpm --dir web/admin build"
  }
}
```

这样既可以由Husky调用，也可以手工执行：

```powershell
pnpm run check:frontend
```

### 5.2 pre-commit示例

`.husky/pre-commit`：

```sh
pnpm --dir web/admin lint
```

如果后续引入 `lint-staged`，可改为只检查已经暂存的文件，进一步缩短执行时间。

### 5.3 pre-push示例

`.husky/pre-push`：

```sh
pnpm run check:frontend
./backend/mvnw -f ./backend/pom.xml -Dmaven.test.skip=false test
docker compose -f infra/docker/compose.yml config --quiet
```

该示例依次检查：

1. Vue/TypeScript类型；
2. 前端代码规范；
3. 前端单元测试；
4. 前端生产构建；
5. Java后端编译和单元测试；
6. Docker Compose语法与变量解析。

任何命令失败都会停止后续命令并阻止push。

## 6. CertMuse可用的测试命令

### 前端

```powershell
pnpm --dir web/admin typecheck
pnpm --dir web/admin lint
pnpm --dir web/admin test:unit
pnpm --dir web/admin test:coverage
pnpm --dir web/admin build
```

用途分别是：

| 命令 | 作用 |
|---|---|
| `typecheck` | 检查TypeScript和Vue模板类型 |
| `lint` | 检查前端代码规范和常见错误 |
| `test:unit` | 执行Vitest单元测试 |
| `test:coverage` | 执行测试并生成覆盖率报告 |
| `build` | 验证生产构建是否成功 |

### 后端

Windows PowerShell：

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml "-Dmaven.test.skip=false" test
```

Git Bash、macOS或Linux：

```sh
./backend/mvnw -f ./backend/pom.xml -Dmaven.test.skip=false test
```

必须显式设置：

```text
-Dmaven.test.skip=false
```

如果项目POM默认跳过测试，遗漏该参数可能出现 `BUILD SUCCESS`，但没有执行任何测试。

### Docker Compose

```powershell
docker compose -f infra/docker/compose.yml config --quiet
```

此命令只验证Compose配置能否解析，不启动容器，也不验证数据库或服务能够正常运行。

## 7. 新成员如何启用Husky

克隆或拉取包含Husky配置的仓库后，在仓库根目录执行：

```powershell
pnpm install --frozen-lockfile
pnpm --dir web/admin install --frozen-lockfile
```

第一条命令安装根Husky依赖并通过 `prepare`激活Git Hooks；第二条命令安装Vue前端依赖。

验证：

```powershell
git config --get core.hooksPath
git hook run pre-commit
git hook run pre-push
```

`git hook run`只执行Hook，不创建提交，也不会向远端推送。

## 8. 日常工作流程

```powershell
git add <files>
git commit -m "test: add validation coverage"
git push
```

执行过程：

```text
git commit
→ pre-commit
→ 快速检查通过
→ 创建提交

git push
→ pre-push
→ 完整检查通过
→ 推送
```

如果Hook失败：

1. Git终止本次操作；
2. 找到终端输出中的第一处实际错误；
3. 单独运行对应命令复现；
4. 修复代码、配置或测试；
5. 重新执行Hook；
6. 通过后再提交或推送。

不要通过删除测试、弱化断言或跳过编译来掩盖真实问题。

## 9. 测试文件和生成物

应提交到Git：

```text
.husky/
package.json
pnpm-lock.yaml
web/admin/package.json
web/admin/pnpm-lock.yaml
web/admin/vitest.config.ts
前端 *.spec.ts
后端 *Test.java
测试夹具
docs/testing/
```

不应提交：

```text
node_modules/
web/admin/dist/
web/admin/coverage/
backend/**/target/
本地日志
本地数据库数据
.env和密钥
```

HTML覆盖率报告属于可重新生成的产物。长期记录可在 `docs/testing` 中保存测试日期、命令、环境、统计结果和结论。

## 10. 性能与分层建议

### pre-commit

目标是快速反馈，通常控制在数秒至一分钟：

```text
lint
格式检查
暂存文件检查
少量快速测试
```

### pre-push

目标是防止明显不可构建或测试失败的代码离开本地：

```text
类型检查
完整单元测试
生产构建
后端编译
配置解析
```

不建议在每次commit或push时运行：

- 长时间性能测试；
- 完整安全扫描；
- 启动完整生产环境；
- 大规模浏览器矩阵；
- 生产部署。

## 11. 覆盖率门槛

覆盖率门槛应在已有合理测试基础后逐步引入。

不建议在测试基础薄弱时直接设置高阈值，因为这会导致所有push失败，并诱发无意义测试或绕过Hook。

合理流程：

```text
先生成覆盖率基线
→ 找出高风险未覆盖模块
→ 补充有效断言
→ 设置略低于当前稳定值的门槛
→ 随测试增加逐步提高
```

覆盖率表示代码是否在测试中执行，不证明：

- 断言正确；
- 业务需求正确；
- API可以联通；
- 数据库事务正确；
- 页面完整流程正常。

## 12. 常见故障

### `pnpm`无法识别

```powershell
corepack enable
corepack prepare pnpm@10 --activate
```

### Hook没有执行

```powershell
git config --get core.hooksPath
Get-ChildItem .husky
pnpm exec husky
```

### `pnpm install`后仍未激活

确认根 `package.json` 包含：

```json
"prepare": "husky"
```

然后重新运行：

```powershell
pnpm install
```

### Maven提示没有POM

如果从仓库根目录调用Maven Wrapper，需要指定后端POM：

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml "-Dmaven.test.skip=false" test
```

或者先进入后端目录：

```powershell
Set-Location backend
.\mvnw.cmd "-Dmaven.test.skip=false" test
Set-Location ..
```

### Maven显示成功但没有测试

检查输出是否包含：

```text
Tests run:
```

并确认使用了：

```text
-Dmaven.test.skip=false
```

### Surefire提示缺少JUnit引擎

典型错误：

```text
groups/excludedGroups require TestNG, JUnit48+ or JUnit 5
```

通常表示Surefire标签筛选被应用到没有JUnit测试引擎的模块。应检查POM中的Surefire配置范围，而不是盲目给所有模块增加测试依赖。

### Docker Compose检查失败

去掉 `--quiet` 查看详细错误：

```powershell
docker compose -f infra/docker/compose.yml config
```

### Windows构建输出乱码

Git Hook使用的Shell和PowerShell编码可能不同。若只是符号乱码、命令退出码为0且明确显示构建成功，通常属于终端显示问题。

## 13. Husky的限制

Husky运行在每个开发者的本地仓库中，因此依赖本地Node.js、Java、pnpm、Docker等环境。

开发者也可以使用：

```powershell
git commit --no-verify
git push --no-verify
```

绕过本地Hook。因此，Husky能提供快速反馈和本地拦截，但不能证明代码绝对没有缺陷，也不能保证所有人的本地环境完全一致。

## 14. 给Codex或维护者的操作清单

处理Husky相关任务时：

1. 先阅读仓库根 `AGENTS.md`；
2. 执行 `git status --short --branch`，保护用户已有修改；
3. 检查根和前端的 `package.json`、锁文件；
4. 检查 `.husky/pre-commit` 与 `.husky/pre-push`；
5. 确认每条命令可以脱离Hook独立执行；
6. 核实JUnit实际执行数量，不能只看 `BUILD SUCCESS`；
7. 修改Hook后执行 `git hook run pre-commit` 和 `git hook run pre-push`；
8. 不使用个人绝对路径；
9. 不擅自提高覆盖率门槛；
10. 不擅自commit、push或使用 `--no-verify`。

