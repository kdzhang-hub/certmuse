# 知识点JSONL测试集

本目录依据 `docs/imports/知识点树JSONL导入规范.md` 构造，用于单元测试、API集成测试和PostgreSQL导入断言。

## 文件

- `fixtures/knowledge-point-v7-valid.jsonl`：1001行正常基线数据。
- `fixtures/knowledge-point-v7-with-edge-cases.jsonl`：1001行正常数据后追加字段、结构、跨行和物理行极端案例。
- `fixtures/knowledge-point-subject-mapping-unknown.jsonl`：配合只提交科目1映射，验证科目2未显式映射。
- `fixtures/knowledge-point-v7-valid-crlf.jsonl`：CRLF行尾正常数据。
- `fixtures/knowledge-point-v7-valid-no-final-newline.jsonl`：末行无换行符的正常数据。
- `fixtures/knowledge-point-v7-valid-with-bom.jsonl`：带UTF-8 BOM，预期文件级拒绝。
- `fixtures/knowledge-point-invalid-utf8.jsonl`：包含非法UTF-8字节，预期文件级拒绝。
- `expected-cases.json`：每个追加案例的物理行号、主要预期问题码和说明。
- `generate-fixtures.mjs`：从 `target/knowledge_point_v7.jsonl` 可重复生成测试集。
- `compose.test.yml`：隔离集成测试覆盖配置，仅关闭验证码，不改变正式配置。
- `run-integration.mjs`：真实认证、上传、幂等、预检、进度和问题分页测试。
- `run-database-test.ps1`：PostgreSQL、幂等记录和MinIO对象一致性断言。
- `results/`：本次覆盖率、集成测试和数据库测试结果。
- `TEST-REPORT.md`：本次测试结论和缺陷修复记录。

综合文件中的案例可能因全文件校验产生合理的附加问题码。自动断言应至少包含清单列出的主要问题码，不应假设每行只能产生一个问题。

重新生成：

```powershell
node docs/testing/knowledge-point-jsonl/generate-fixtures.mjs
```

生成器要求原始基线恰好为1001行；不满足时立即失败，避免基线变化后静默生成错误测试集。

## 运行

集成测试默认访问 `http://localhost:18080`，使用隔离测试栈的默认管理员账号。先用
`infra/docker/compose.yml` 和本目录的 `compose.test.yml` 启动环境，再执行：

```powershell
node docs/testing/knowledge-point-jsonl/run-integration.mjs
.\docs\testing\knowledge-point-jsonl\run-database-test.ps1
```

当前接口属于D1“上传与预检”阶段，没有“确认并正式导入”端点。因此数据库测试验证的是
`cm_import_batch`、`cm_import_record`、`cm_import_issue`、幂等记录和MinIO原文件持久化，
并明确断言预检不会写入 `cm_knowledge_point`。这不能被表述为正式知识点入库成功。
