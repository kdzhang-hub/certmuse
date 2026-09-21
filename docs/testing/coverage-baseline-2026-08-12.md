# CertMuse 测试覆盖率基线（2026-08-12）

## 1. 快照与环境

- 分支：`develop`
- 提交：`a8f83373d2200d8ab11fc8991931d58e845b6642`
- 远端差异：`HEAD...origin/develop = 0 0`
- 测试前工作区：无 staged、unstaged 或 untracked 文件
- 本地 PostgreSQL：当前 `develop` 源码清单中的 62 条迁移均已登记且 SHA-256 一致；数据库另有 1 条仅存在于本地 `main` 提交的 `20260812_grant-content-import-table-privileges.sql`。本轮未回滚、重放或修改数据库。

覆盖率只用于说明代码是否被执行，不替代断言质量、接口契约、权限、状态转换和持久化结果检查。

## 2. 统计边界

### 后端

统计 `backend/ruoyi-modules/ruoyi-certmuse` 的全部已编译生产类。RuoYi 原生 Maven 模块不进入 JaCoCo 分母。当前生产源码未发现应排除的 mock 或空实现；首次诊断、学习目标和 onboarding 已有 Controller/Service/Mapper 实现，必须纳入。

### 管理端

统计 `src/api/certmuse`、`src/views/certmuse`、`src/components/certmuse` 的已实施代码。排除测试、声明、`mock.ts`，以及只渲染 `EmptyBusinessPage` 的明确占位目录：

- `catalog/exam-schedule/**`
- `insight/**`
- `learning/rule/**`

基线仍计入 5 个同样只包含占位壳的文件，最终口径将统一排除并在最终记录中列出：

- `catalog/resource/edit.vue`
- `catalog/resource/preview.vue`
- `question/question/edit.vue`
- `question/question/preview.vue`
- `question/review/detail.vue`

### 学员端

RuoYi 模板源码、`src/api/student/mock.ts`、mock 测试，以及只调用 `src/api/student` mock 适配器的未接入功能不进入正式分母。基线纳入已实现的真实前端契约范围：

- `src/api/certmuse/learning/{onboarding,goals,diagnostics}.ts`
- `src/utils/{auth-entry,learning-goal,onboarding-routing}.ts`
- onboarding 两个页面
- diagnostic 四个页面

`account/tasks/practice/mistakes/progress/history/resources/settings/session/result` 当前只通过 mock 适配器运行，明确排除。`dashboard` 同时混合真实 onboarding 和未接入 mock 功能，需先拆分真实逻辑后再决定分母。

## 3. 基线结果

| 端 | 正式测试结果 | Statements / Instructions | Branches | Functions / Methods | Lines |
|---|---:|---:|---:|---:|---:|
| 后端 | 标准命令 CertMuse 372/372 通过 | 79.68% (28,499/35,765) | 61.06% (2,238/3,665) | 83.98% (912/1,086) | 82.77% (4,555/5,503) |
| 管理端 | 177/177 通过 | 83.92% (3,169/3,776) | 78.94% (2,014/2,551) | 80.01% (1,237/1,546) | 87.81% (2,428/2,765) |
| 学员端 | 非 mock 11/11 通过 | 3.02% (20/662) | 4.73% (17/359) | 3.43% (8/233) | 3.19% (15/470) |

说明：后端全 reactor 标准命令为 384/384 通过；学员端现有总数为 16/16，其中 5 个是明确排除的 mock 测试。

## 4. 基线发现的测试门禁问题

后端 Surefire 默认按 `dev` 标签收集测试，但以下三个首次诊断测试没有 `@Tag("dev")`，所以标准命令没有执行它们：

- `DiagnosticMigrationTest`
- `DiagnosticExceptionHandlerTest`
- `DiagnosticServiceImplTest`

定向强制执行得到 8 个用例：7 个通过，`DiagnosticMigrationTest` 因依赖错误的工作目录相对路径而出现 `NoSuchFileException`。因此基线的准确结论是“标准 dev 测试集通过，但漏跑三类已实施诊断测试”，不能宣称所有后端测试均已通过。

学员端基线没有项目级 `vitest.config.ts`、`test:coverage` 脚本和本地 V8 coverage provider；基线使用明确白名单临时测量，后续必须补成可复现的正式配置。

## 5. 原始命令与报告

```powershell
# 后端
cd backend
.\mvnw.cmd "-Dmaven.test.skip=false" test

# 管理端
cd web/admin
pnpm test:unit
pnpm test:coverage

# 学员端现有门禁
cd web/student
pnpm test:unit
```

- 后端 HTML：`backend/ruoyi-modules/ruoyi-certmuse/target/site/jacoco/index.html`
- 后端 CSV：`backend/ruoyi-modules/ruoyi-certmuse/target/site/jacoco/jacoco.csv`
- 管理端 HTML：`web/admin/coverage/index.html`
- 管理端 JSON：`web/admin/coverage/coverage-summary.json`
- 学员端临时基线 JSON：`%LOCALAPPDATA%/Temp/certmuse-student-baseline-a8f83373-20260812T1753/coverage-summary.json`
