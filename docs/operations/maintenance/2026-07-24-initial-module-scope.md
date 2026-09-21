# CertMuse 首期模块范围

- 日期：2026-07-24（Asia/Taipei）
- 目标：收紧 CertMuse 首期 RuoYi-Vue-Plus 生产包与默认公开入口

## 执行内容

1. 保留系统、权限、PostgreSQL、Redis 与 `ruoyi-job` 基础能力；SnailJob 客户端继续关闭。
2. 从 `ruoyi-admin` 的默认生产依赖移除演示、代码生成、工作流、LiteFlow、AI、SnailAI 与 MCP。
3. 关闭统一消息推送与生产 OpenAPI 文档；清理工作流和 AI 遗留的安全排除路径及 API 分组。
4. 将生产 Actuator 缩至 `health,info`，且不公开健康详情。

## 验证

- `mvn -pl ruoyi-admin -am -Pprod -DskipTests -q compile` 成功。
- 现有 PostgreSQL 仅保留主库和任务脚本；工作流、AI 脚本均未导入。
- 未执行应用发布、服务重启或数据库变更。

后续模块的恢复入口、依赖和迁移要求见[首期模块清单](../../modules.md)。
