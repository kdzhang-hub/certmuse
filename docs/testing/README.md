# CertMuse 测试与覆盖率

## 一键运行

在仓库根目录执行：

```powershell
pnpm test:coverage
```

该命令运行管理端 Vitest 和 `ruoyi-certmuse` 的 JUnit 测试。任意一组失败都会返回非零退出码。

学生端位于 `web/student`，当前以独立 RuoYi-Vue-Plus 模板和 Mock 数据运行。它有独立的类型检查、Lint、单测和构建门禁（`pnpm check:student`），但尚未接入生产后端或纳入管理端覆盖率统计。

## 分开运行

```powershell
# 管理端
pnpm --dir web/admin test:coverage

# 后端（属性在 PowerShell 中必须加引号）
cd backend
.\mvnw.cmd -pl ruoyi-modules/ruoyi-certmuse -am '-Dmaven.test.skip=false' test
```

需要同时检查前端类型、Lint、单测和生产构建时，分别运行根目录的 `pnpm check:admin` 与 `pnpm check:student`。

## 查看覆盖率

运行完成后用浏览器打开：

- 管理端：`web/admin/coverage/index.html`
- 后端：`backend/ruoyi-modules/ruoyi-certmuse/target/site/jacoco/index.html`

终端也会输出 statements、branches、functions 和 lines 汇总。分支覆盖率通常最能暴露 invalid 与 edge case 的遗漏，但覆盖率只能说明代码是否执行，不能证明断言正确。

## 统计边界

- 后端只对 `ruoyi-modules/ruoyi-certmuse` 生成 JaCoCo 报告；RuoYi 自带模块不进入 CertMuse 覆盖率分母。
- 前端只统计 `src` 下的 CertMuse 自研目录；RuoYi 页面不进入分母。
- `mock.ts`、`mock.spec.ts`、测试文件和类型声明不进入正式产品覆盖率或正式测试数量。Mock 可以用于人工演示，但不能用其覆盖率代表真实 API 与业务逻辑覆盖率。
- 学生端不参与当前统计；即使目录中存在测试或历史覆盖率报告，也不能纳入管理端质量结论。

## valid、invalid 与 edge case

新增或修改一条业务规则时，至少检查：

1. **Valid**：典型合法输入、成功返回、持久化结果和正常状态转换。
2. **Invalid**：缺失字段、格式错误、非法状态、权限不足、资源不存在、冲突，以及准确的错误码/响应结构。
3. **Edge**：空集合、最小/最大值、超长输入、分页溢出、重复/幂等、并发竞争、文件边界、编码与压缩包安全边界。

测试名应描述行为和预期结果，不必机械添加分类前缀；评审时必须阅读输入与断言，不能只按测试名称计数。
