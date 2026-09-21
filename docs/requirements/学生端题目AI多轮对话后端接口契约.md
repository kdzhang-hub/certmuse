# 学生端题目 AI 多轮对话后端接口契约

```yaml
document_id: learner-question-ai-chat
document_version: 1.0
status: handoff_ready
module: U09-AI
routes:
  - /api/assessment/knowledge-practices/{sessionId}/items/{questionOrder}/ai-conversations
  - /api/assessment/ai-conversations/{conversationId}
  - /api/assessment/ai-conversations/{conversationId}/messages
  - /api/assessment/ai-conversations/{conversationId}/messages/stream
  - /api/assessment/ai-conversations/{conversationId}/messages/{assistantMessageId}/cancel
scope: learner-question-ai-chat
updated_at: 2026-08-17
```

## 1. 目标与边界

本契约服务学生端知识点练习答题页 `/learning/session/practice?sessionId=...`，允许登录学员围绕当前练习题与 AI 助教进行持久化、多轮、流式对话。前端只提交练习会话、题序、用户消息和当前未提交选择；题干、选项、图片、冻结知识点、标准答案、解析、正式提交状态和正式作答均由服务端根据当前登录用户和练习快照取得。前端不得提交题干、标准答案、解析、知识点或用户 ID。

V1.0 包含：

- 每个“用户 × 练习会话 × 题序”最多一个 AI 对话；
- 创建或幂等取得题目对话；
- 分页读取多轮历史消息；
- 以 `POST + text/event-stream` 发送消息并流式接收 AI 回答；
- 取消当前正在生成的回答；
- 页面刷新后恢复历史，切题后切换到对应题目的独立对话；
- 提交前启发式辅导，提交后完整答案、错因和解析讲解；
- 题目文字、选项和图片上下文；
- 模型超时、限流、内容安全、断流、幂等和依赖故障处理。

V1.0 不包含：

- AI 代替用户提交答案、修改选择、结束练习、评分或写入学习画像；
- 跨题共享完整聊天记录、跨练习合并会话或通用开放聊天；
- 联网搜索、工具调用、教材向量检索或 RAG；
- 语音、文件上传、用户编辑消息、删除单条消息、分享或导出对话；
- 重新生成既有回答；用户需要再次提问；
- AI 主观题评分；其业务仍按全流程文档第 16 节另行实现。

AI 对话是辅助能力。AI 服务关闭、超时或故障不得影响读取题目、提交作答、查看冻结解析或结束练习。本文档是对现有 U09 的增量契约；除本文明确新增的 AI 能力外，`U08-U09-知识点练习后端接口契约.md` 继续有效。

### 1.1 提交前与提交后的回答边界

服务端在**每次生成开始时**读取该题正式提交状态，冻结为本次生成的 `answerDisclosureMode`：

| 模式 | 条件 | AI 可以做 | AI 不得做 |
| --- | --- | --- | --- |
| `GUIDANCE_ONLY` | 当前题尚未正式提交 | 解释概念、澄清题意、分析解题方法、给渐进提示、基于用户临时选择提出反问 | 直接或变相给出正确选项、标准答案、对错结论、冻结解析原文，或确认用户临时选择正确 |
| `FULL_EXPLANATION` | 当前题已经正式提交 | 说明正确答案，对比正式作答，解释选项、错因、知识点和冻结解析 | 编造不存在的评分事实，或修改正式成绩、错题和画像 |

用户通过提示注入、角色扮演、编码、翻译或要求输出系统提示，均不能改变披露模式。`GUIDANCE_ONLY` 下即使用户直接问“答案是什么”也只能拒绝直接泄露并继续提供提示。

临时选择只用于当前轮个性化辅导，不是正式作答，不持久化到练习作答表，不产生错题或画像证据。题目在生成期间被正式提交，不改变已经开始的本次生成模式；下一条消息按最新状态重新计算。

## 2. 共同约定

### 2.1 身份、权限与资源归属

- 所有接口必须登录，并同时具备 Sa-Token 权限 `certmuse:student` 和 `certmuse:assessment:knowledge-practice:ai-chat`，两者为 AND 关系。
- 用户身份只取当前登录会话。请求不得传递或覆盖 `userId`、`goalId`、`questionId`、`revisionId`、题目内容或答案。
- 创建对话时，服务端必须验证练习会话属于当前用户、题序存在，并从 `cm_session_question` 不可变快照取得上下文。
- 通过 `conversationId` 访问时仍须重新校验对话属于当前用户；不能只依赖 ID 不可猜测。
- 不属于当前用户的会话或对话与真实不存在统一返回 404，避免资源枚举。
- 已完成的练习允许读取已有 AI 对话、继续提问和创建该练习题目的对话；披露模式固定为 `FULL_EXPLANATION`。不存在或被业务删除的练习不可创建或继续对话。
- 401/403 沿用认证授权兼容格式，只承诺 `code`、`msg` 和空 `data`。

### 2.2 普通响应与错误模型

除 SSE 流接口外，成功和失败响应均使用 `R<T>`。真实 HTTP Status 必须等于 `R.code`。前端只根据 `data.errorCode` 分支，不匹配本地化 `msg`。

```ts
interface AiChatErrorVo {
  errorCode: string;
  retryable: boolean;
  traceId: string | null;
  fieldErrors: Array<{
    field: string;
    code: 'REQUIRED' | 'INVALID_FORMAT' | 'OUT_OF_RANGE';
    message: string;
  }>;
  details: null | {
    conversationId?: string;
    assistantMessageId?: string;
    retryAfterSeconds?: number;
    limitType?: 'CONCURRENT_GENERATION' | 'USER_MINUTE' | 'USER_DAY';
  };
}
```

- 400 字段错误统一使用 `REQUIRED`、`INVALID_FORMAT`、`OUT_OF_RANGE`。
- 4xx 默认 `traceId:null`；5xx 必须返回安全文案和非空 `traceId`。
- 普通接口的所有数组不得返回 `null`。
- 所有业务 ID 均为十进制正整数字符串，防止 JavaScript 精度丢失。
- 时间为带 `Z` 的 ISO-8601 UTC 字符串，前端按 `Asia/Shanghai` 展示。

### 2.3 对话和消息模型

```ts
type AiConversationStatus = 'ACTIVE' | 'DISABLED';
type AiMessageRole = 'USER' | 'ASSISTANT';
type AiMessageStatus = 'COMPLETED' | 'GENERATING' | 'FAILED' | 'CANCELLED';
type AnswerDisclosureMode = 'GUIDANCE_ONLY' | 'FULL_EXPLANATION';

interface AiChatMessageVo {
  id: string;
  sequence: number;
  role: AiMessageRole;
  content: string;
  status: AiMessageStatus;
  answerDisclosureMode: AnswerDisclosureMode | null;
  errorCode: string | null;
  createdAt: string;
  completedAt: string | null;
}

interface AiConversationVo {
  conversationId: string;
  practiceSessionId: string;
  questionOrder: number;
  status: AiConversationStatus;
  answerDisclosureMode: AnswerDisclosureMode;
  generatingAssistantMessageId: string | null;
  createdAt: string;
  updatedAt: string;
}
```

规则：

- `sequence` 在单个对话内从 1 严格递增，用户消息和助手消息各占一个序号。
- 用户消息一旦被接受即为 `COMPLETED`；助手消息经历 `GENERATING` 后进入 `COMPLETED`、`FAILED` 或 `CANCELLED` 终态。
- `answerDisclosureMode` 对用户消息恒为 `null`，对助手消息表示生成该消息时冻结的披露模式。
- `content` 为纯 Markdown 文本，不包含 HTML；前端必须禁用原始 HTML并执行 XSS 清洗。失败且没有产生文本时返回空字符串。
- 系统提示、题目上下文、模型内部推理、供应商响应体和 token 用量不通过消息接口返回。

### 2.4 服务开关与基础限额

以下值为 V1.0 冻结的对外行为，具体配置键由实现文档确定：

| 项目 | 限制 |
| --- | --- |
| 用户消息 | 去除首尾空白后 1–2000 个 Unicode 字符 |
| 单个对话保留消息 | 最多 200 条，即最多 100 个完整问答轮次 |
| 单用户并发生成 | 1 个 |
| 单用户频率 | 10 次生成/滚动 60 秒 |
| 单用户每日额度 | 100 次生成/Asia/Shanghai 自然日 |
| 单次生成首 token 超时 | 20 秒 |
| 单次生成总时长 | 90 秒 |
| 单次助手输出 | 最多 4000 个 Unicode 字符；达到上限正常结束，`finishReason='LENGTH'` |

服务端发送给模型的上下文不要求包含全部 200 条消息。必须保留当前题目上下文、最近 20 条完整消息；更早消息可由服务端摘要。摘要不得作为用户可见消息返回，也不得改变答案披露规则。

## 3. 创建或取得题目对话

```http
POST /api/assessment/knowledge-practices/{sessionId}/items/{questionOrder}/ai-conversations
X-Request-Id: 550e8400-e29b-41d4-a716-446655440000
```

无查询参数和请求体。

### 3.1 请求字段

| 位置 | 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- | --- |
| path | `sessionId` | string | 是 | 十进制正整数 |
| path | `questionOrder` | integer | 是 | `1..session.totalCount` |
| header | `X-Request-Id` | string | 是 | 小写 UUID；同一创建意图重试必须复用 |

### 3.2 成功响应

首次创建返回 HTTP 201；已经存在或相同请求幂等回放返回 HTTP 200。两者 `data.created` 不同，其余结构相同。

```json
{
  "code": 201,
  "msg": "创建成功",
  "data": {
    "created": true,
    "conversation": {
      "conversationId": "901",
      "practiceSessionId": "123",
      "questionOrder": 2,
      "status": "ACTIVE",
      "answerDisclosureMode": "GUIDANCE_ONLY",
      "generatingAssistantMessageId": null,
      "createdAt": "2026-08-17T08:00:00Z",
      "updatedAt": "2026-08-17T08:00:00Z"
    }
  }
}
```

唯一性由“用户 × `practice_session_id` × `question_order`”保证，并发创建只能产生一条对话。相同 `X-Request-Id` 对应不同规范化目标时返回 `AI_CHAT_IDEMPOTENCY_CONFLICT`。

## 4. 获取对话摘要

```http
GET /api/assessment/ai-conversations/{conversationId}
```

无查询参数、请求体和 `X-Request-Id`。

成功返回 HTTP 200：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "conversationId": "901",
    "practiceSessionId": "123",
    "questionOrder": 2,
    "status": "ACTIVE",
    "answerDisclosureMode": "FULL_EXPLANATION",
    "generatingAssistantMessageId": null,
    "createdAt": "2026-08-17T08:00:00Z",
    "updatedAt": "2026-08-17T08:04:30Z"
  }
}
```

`answerDisclosureMode` 按读取时最新正式提交状态计算；它可能与历史助手消息上的模式不同。

## 5. 分页获取历史消息

```http
GET /api/assessment/ai-conversations/{conversationId}/messages?beforeSequence=41&limit=20
```

### 5.1 查询参数

| 字段 | 类型 | 必填 | 默认值 | 规则 |
| --- | --- | --- | --- | --- |
| `beforeSequence` | integer | 否 | 无 | 大于 0；只返回 `sequence < beforeSequence` 的消息 |
| `limit` | integer | 否 | 20 | `1..50` |

查询采用向前游标分页。服务端先选取游标前最新的 `limit` 条，再按 `sequence` 升序返回，方便前端直接插入时间线。首次请求不传 `beforeSequence`，取得最新一页。

```ts
interface AiMessagePageVo {
  items: AiChatMessageVo[];
  hasMore: boolean;
  nextBeforeSequence: number | null;
}
```

成功响应 HTTP 200：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "items": [
      {
        "id": "1001",
        "sequence": 1,
        "role": "USER",
        "content": "这道题考查什么？",
        "status": "COMPLETED",
        "answerDisclosureMode": null,
        "errorCode": null,
        "createdAt": "2026-08-17T08:01:00Z",
        "completedAt": "2026-08-17T08:01:00Z"
      },
      {
        "id": "1002",
        "sequence": 2,
        "role": "ASSISTANT",
        "content": "这道题主要考查访问控制中的最小权限原则。可以先比较各选项授予的权限范围。",
        "status": "COMPLETED",
        "answerDisclosureMode": "GUIDANCE_ONLY",
        "errorCode": null,
        "createdAt": "2026-08-17T08:01:00Z",
        "completedAt": "2026-08-17T08:01:03Z"
      }
    ],
    "hasMore": false,
    "nextBeforeSequence": null
  }
}
```

如果最近一条助手消息仍为 `GENERATING`，历史接口返回当前已持久化的部分 `content`；前端随后可等待当前流、取消生成，或轮询对话摘要和历史。V1.0 不提供从断点重新订阅旧 SSE 流。

## 6. 发送消息并流式生成回答

```http
POST /api/assessment/ai-conversations/{conversationId}/messages/stream
Accept: text/event-stream
Content-Type: application/json
X-Request-Id: 7c9e6679-7425-40de-944b-e07fc1f90ae7
```

```json
{
  "message": "为什么 B 选项不合适？",
  "clientMessageId": "9f1c1e50-2b46-4c23-8b0f-c1dd5a65c871",
  "currentSelection": ["B"]
}
```

### 6.1 请求体

```ts
interface SendAiMessageBo {
  message: string;
  clientMessageId: string;
  currentSelection: string[];
}
```

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `message` | string | 是 | 去除首尾空白后 1–2000 个 Unicode 字符；持久化规范化后的文本 |
| `clientMessageId` | string | 是 | 小写 UUID；一次用户发送意图全局稳定，重试必须复用 |
| `currentSelection` | string[] | 是 | 未选择时传 `[]`；去重后最多等于冻结选项数；每项必须是当前题冻结选项标签 |

`X-Request-Id` 标识一次 HTTP 尝试，网络重试使用新的值；`clientMessageId` 标识用户发送意图，网络重试必须复用。相同用户和 `clientMessageId` 对应不同规范化消息、对话或选择时返回 `AI_CHAT_IDEMPOTENCY_CONFLICT`。

`currentSelection` 只用于当前轮提示：

- 未提交题允许传当前页面中的临时选择；
- 已提交题服务端忽略该字段，改用正式作答快照；
- 该字段不得写入正式作答、错题、画像或学习证据表；
- 非法或重复选项返回 422 `AI_CHAT_SELECTION_INVALID`。

### 6.2 建立流之前的响应

服务端必须在发送 HTTP 200 和 SSE 响应头之前完成认证、权限、字段、资源归属、服务开关、会话状态、并发、额度、消息上限和幂等检查。

检查失败时不建立 SSE，按普通 `application/json` 的 `R<AiChatErrorVo>` 返回真实 4xx/5xx。检查通过后返回：

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream;charset=UTF-8
Cache-Control: no-cache, no-transform
X-Accel-Buffering: no
X-Request-Id: 7c9e6679-7425-40de-944b-e07fc1f90ae7
```

前端必须使用 `fetch` 读取响应流；浏览器原生 `EventSource` 不支持本契约所需的 POST 请求体。前端必须先检查 HTTP Status 和 `Content-Type`，JSON 走普通错误分支，`text/event-stream` 才进入事件解析。

### 6.3 SSE 通用格式

每个事件固定包含 `id`、`event` 和单行 JSON `data`：

```text
id: 1
event: message.start
data: {"conversationId":"901","userMessageId":"1003","assistantMessageId":"1004","answerDisclosureMode":"GUIDANCE_ONLY"}

```

- 空行结束一个事件；`id` 是本次 HTTP 流内从 1 递增的事件序号，不是数据库消息 ID。
- 服务端每 15 秒无业务事件时发送注释心跳 `: heartbeat\n\n`；心跳不递增事件序号。
- 所有 `data` 使用 UTF-8 JSON；换行编码为 JSON 字符串中的 `\n`，不得产生多行 `data:`。
- V1.0 不承诺 `Last-Event-ID` 续传。断流后前端读取历史接口恢复最终已保存状态，不得自动创建新消息。
- 每个成功建立的流必须以一个终态事件结束：`message.completed`、`message.failed` 或 `message.cancelled`。

### 6.4 SSE 事件定义

#### `message.start`

消息记录已经创建，必须是第一个业务事件：

```json
{
  "conversationId": "901",
  "userMessageId": "1003",
  "assistantMessageId": "1004",
  "answerDisclosureMode": "GUIDANCE_ONLY"
}
```

#### `message.delta`

追加助手文本。前端按事件顺序直接拼接 `delta`，不得覆盖已有文本：

```json
{
  "assistantMessageId": "1004",
  "delta": "B 选项扩大了权限范围，"
}
```

`delta` 非空，单个事件不超过 1000 个 Unicode 字符。服务端可批量落库，不承诺每个 delta 已单独持久化。

#### `message.completed`

生成正常完成：

```json
{
  "assistantMessageId": "1004",
  "status": "COMPLETED",
  "finishReason": "STOP",
  "contentLength": 126,
  "completedAt": "2026-08-17T08:02:10Z"
}
```

`finishReason` 枚举为：

- `STOP`：模型正常结束；
- `LENGTH`：达到输出长度上限；
- `CONTENT_FILTER`：输出被安全策略截断；此时已通过审核、允许展示的前缀可以保留。

#### `message.failed`

HTTP 200 已建立后发生失败：

```json
{
  "assistantMessageId": "1004",
  "status": "FAILED",
  "errorCode": "AI_PROVIDER_TIMEOUT",
  "retryable": true,
  "traceId": "01J5KQ0C9W2M8E6Y7A4B3D1F0H",
  "partialContentDiscarded": true
}
```

流内 `message.failed` 不是 `R<T>`。`traceId` 必须非空。失败时服务端将助手消息标记为 `FAILED`；为避免把不完整结论当成正式回答，历史消息中的 `content` 固定保存为空字符串，`partialContentDiscarded:true`。

#### `message.cancelled`

用户取消或客户端断开且供应商生成已成功终止：

```json
{
  "assistantMessageId": "1004",
  "status": "CANCELLED",
  "completedAt": "2026-08-17T08:02:05Z"
}
```

取消时已经输出的部分内容不作为完整回答，历史消息中的 `content` 固定保存为空字符串。

### 6.5 上下文组装

模型上下文至少包含：

1. 版本化系统规则和当前 `answerDisclosureMode`；
2. 冻结题型、题干、选项和题目图片；
3. 冻结知识点映射；
4. 当前题正式提交状态；
5. `FULL_EXPLANATION` 下的正式作答、正确答案和冻结解析；
6. `GUIDANCE_ONLY` 下仅供服务端策略控制的正确答案和解析，不得在回答中泄露；
7. 当前临时选择或正式作答；
8. 最近 20 条完整历史消息和必要的内部摘要；
9. 当前用户消息。

题目正文、用户消息和历史消息均视为不可信内容，不能覆盖系统规则。服务端必须用结构化字段区分系统指令、可信题目事实和用户文本，不能把它们简单字符串拼接成同级指令。

题目图片必须由服务端通过现有 OSS 抽象按冻结对象标识读取，并按模型适配器要求传递；不得信任前端图片 URL，不得把 OSS 凭据或永久公开地址交给模型供应商。单张或整题图片超过供应商限制时返回/发送 `AI_QUESTION_CONTEXT_UNSUPPORTED`，不能静默忽略导致误答。

### 6.6 持久化与提交顺序

流建立前置检查通过后，服务端在短事务内原子完成：

1. 写入用户消息；
2. 写入 `GENERATING` 助手消息；
3. 占用用户并发生成槽；
4. 提交事务；
5. 发送 `message.start`；
6. 在事务外调用远程模型并流式生成；
7. 终态时用短事务更新助手消息并释放并发槽。

远程调用、等待 token 和长循环不得位于数据库事务中。应用异常退出后，后台清理任务必须将超过总时长加 30 秒仍处于 `GENERATING` 的消息转为 `FAILED/AI_GENERATION_INTERRUPTED` 并释放并发槽。

## 7. 取消正在生成的回答

```http
POST /api/assessment/ai-conversations/{conversationId}/messages/{assistantMessageId}/cancel
X-Request-Id: 2676c3c8-9c1a-4ae8-a1a8-08992bf77cc8
```

无请求体。`assistantMessageId` 必须属于路径中的对话且角色为 `ASSISTANT`。

成功取消或目标已经是 `CANCELLED` 时返回 HTTP 200：

```json
{
  "code": 200,
  "msg": "已取消生成",
  "data": {
    "assistantMessageId": "1004",
    "status": "CANCELLED"
  }
}
```

规则：

- 相同 `X-Request-Id` 幂等回放；不同请求重复取消已取消消息也返回成功。
- 已经 `COMPLETED` 或 `FAILED` 返回 409 `AI_MESSAGE_NOT_GENERATING`，不得修改终态。
- 取消请求将消息置为 `CANCELLED`、释放并发槽并尽力终止供应商调用。
- 前端点击“停止生成”时必须同时调用取消接口并中止本地 fetch；仅中止 fetch 不能视为服务端已取消。
- 客户端意外断开时，服务端应取消上游调用并转为 `CANCELLED`；如果无法确认取消，保持生成直到终态或由超时清理任务处理。

## 8. 失败响应

### 8.1 普通 JSON 错误示例

```json
{
  "code": 429,
  "msg": "AI 对话请求过于频繁，请稍后再试",
  "data": {
    "errorCode": "AI_CHAT_RATE_LIMITED",
    "retryable": true,
    "traceId": null,
    "fieldErrors": [],
    "details": {
      "retryAfterSeconds": 42,
      "limitType": "USER_MINUTE"
    }
  }
}
```

字段错误示例：

```json
{
  "code": 400,
  "msg": "请求参数不正确",
  "data": {
    "errorCode": "AI_CHAT_REQUEST_INVALID",
    "retryable": false,
    "traceId": null,
    "fieldErrors": [
      {
        "field": "message",
        "code": "OUT_OF_RANGE",
        "message": "消息长度必须为 1 至 2000 个字符"
      }
    ],
    "details": null
  }
}
```

### 8.2 错误表

| 接口 | HTTP | `errorCode` | 触发条件 | `retryable` | 前端行为 |
| --- | ---: | --- | --- | --- | --- |
| 全部 | 400 | `AI_CHAT_REQUEST_INVALID` | 路径、查询、请求头、JSON 类型或字段约束错误 | false | 按 `fieldErrors` 修正；不重试 |
| 创建、发送 | 422 | `AI_CHAT_SELECTION_INVALID` | `currentSelection` 含非法、重复或超量标签 | false | 保留输入，刷新题目状态后修正选择 |
| 全部 | 404 | `AI_CONVERSATION_NOT_FOUND` | 对话不存在或不属于当前用户 | false | 清理本地会话并返回题目页 |
| 创建 | 404 | `PRACTICE_SESSION_NOT_FOUND` | 练习会话不存在或不属于当前用户 | false | 返回知识点练习设置页 |
| 创建 | 400 | `AI_CHAT_QUESTION_ORDER_INVALID` | 题序超出该练习范围 | false | 刷新练习会话导航 |
| 创建、发送 | 409 | `AI_CHAT_IDEMPOTENCY_CONFLICT` | 幂等标识对应不同规范化载荷 | false | 停止自动重试并生成新标识 |
| 创建、发送 | 409 | `AI_CHAT_MESSAGE_LIMIT_REACHED` | 对话已有 200 条消息 | false | 禁用输入；提示当前题对话已达上限 |
| 发送 | 409 | `AI_CHAT_GENERATION_ALREADY_RECORDED` | 相同 `clientMessageId` 的助手消息已经进入终态 | false | 不重发；按详情中的消息 ID 读取历史恢复结果 |
| 发送 | 409 | `AI_CHAT_GENERATION_IN_PROGRESS` | 当前用户已有生成任务 | true | 展示已有生成状态，不创建新消息 |
| 取消 | 404 | `AI_MESSAGE_NOT_FOUND` | 助手消息不存在、不属于对话或资源越权 | false | 刷新历史消息 |
| 取消 | 409 | `AI_MESSAGE_NOT_GENERATING` | 消息已完成或失败 | false | 刷新历史，以服务端终态为准 |
| 创建、发送 | 403 | `AI_CHAT_CONTENT_REJECTED` | 用户输入命中内容安全拒绝策略 | false | 保留输入供用户修改，不创建消息 |
| 创建、发送 | 503 | `AI_CHAT_DISABLED` | 功能开关关闭或当前环境未启用 | true | 隐藏或禁用 AI 面板；正常做题不受影响 |
| 发送 | 429 | `AI_CHAT_RATE_LIMITED` | 命中分钟或每日额度 | true | 按 `retryAfterSeconds` 倒计时，不自动循环重试 |
| 发送 | 422 | `AI_QUESTION_CONTEXT_UNSUPPORTED` | 题目快照或图片无法安全转换为模型上下文 | false | 提示当前题暂不支持 AI，不影响做题 |
| 发送 | 503 | `AI_PROVIDER_UNAVAILABLE` | 模型供应商不可用或熔断 | true | 提示稍后重试；不得重复创建用户消息 |
| 发送 | 504 | `AI_PROVIDER_TIMEOUT` | 建流前供应商超时 | true | 提示稍后重试 |
| 全部 | 500 | `AI_CHAT_SYSTEM_FAILURE` | 未预期系统、持久化或上下文组装故障 | true | 展示安全提示和 `traceId` |

说明：

- 发送接口在 HTTP 200 建流后发生的供应商不可用、超时、内容安全、应用中断和系统故障改用 `message.failed`，其稳定错误码分别为 `AI_PROVIDER_UNAVAILABLE`、`AI_PROVIDER_TIMEOUT`、`AI_OUTPUT_REJECTED`、`AI_GENERATION_INTERRUPTED`、`AI_CHAT_SYSTEM_FAILURE`。
- 429 响应同时返回标准 `Retry-After` HTTP Header，其秒数与 `details.retryAfterSeconds` 相同。
- `AI_CHAT_CONTENT_REJECTED` 是已认证用户对自身资源的内容安全业务拒绝，不是权限 403；它必须返回完整 `AiChatErrorVo`。认证授权 403 仍只承诺 `code/msg/data:null`。

## 9. 行为规则

### 9.1 多轮与切题

1. 同一道题的连续提问复用同一 `conversationId`；前端不得为每一轮创建新对话。
2. 切换题目时，前端取消当前仍在生成的回答，再创建或取得新题对应对话并加载其历史。
3. 返回之前题目时恢复该题自己的历史，不能把其他题的消息合并进当前模型上下文。
4. 同一题在不同浏览器或设备打开时共享持久化历史，但单用户只能有一个并发生成任务。
5. 页面刷新不能自动重发最后一条消息；必须先取得会话摘要和历史。

### 9.2 幂等和并发

1. 创建对话和取消使用 `X-Request-Id` 实现动作幂等；发送消息以 `clientMessageId` 实现业务幂等，并记录每次 HTTP 的 `X-Request-Id` 用于追踪。
2. 相同 `clientMessageId` 已存在且载荷相同时：若助手消息已经终态，发送接口不重新调用模型，返回 HTTP 409 `AI_CHAT_GENERATION_ALREADY_RECORDED`，`details` 包含 `conversationId` 和 `assistantMessageId`，`retryable:false`；前端读取历史恢复结果。若仍在生成，返回 `AI_CHAT_GENERATION_IN_PROGRESS`。
3. 并发请求必须通过数据库唯一约束或分布式原子门禁保证不会创建重复用户消息、重复助手消息或重复供应商调用。
4. 额度只在成功创建一对用户/助手消息时扣减一次；参数拒绝、权限失败、幂等回放不扣减。模型失败或用户取消不返还次数，防止滥用。

### 9.3 AI 输出与学习事实隔离

- AI 回答、用户聊天消息、临时选择和对话次数均不形成正式学习画像证据。
- AI 不得调用提交、评分、错题、画像或练习完成写接口。
- AI 回答与题库冻结解析不一致时，前端仍以正式提交接口返回的正确答案和冻结解析为准。
- 前端必须在面板固定展示“AI 生成内容可能有误，请以题目正式解析为准”。
- 助手消息需要视觉标识“AI 生成”；不得冒充官方考试答案或人工教师。

### 9.4 对话生命周期

- 对话随所属学习会话保存；V1.0 不提供用户单独删除接口。
- 账号注销、数据匿名化和法定保留按平台统一数据生命周期执行。
- 对话内容用于提供当前 AI 服务，不得默认用于模型训练；启用训练前必须完成独立合规确认，明确用途、范围和退出方式。
- 向外部模型供应商发送的数据必须受供应商数据处理协议约束，并关闭供应商侧训练和非必要长期保留能力。

## 10. 安全与可观测性

### 10.1 安全要求

1. API Key、供应商凭据只来自受保护的运行时环境变量，不进入数据库、YAML、前端或日志。
2. 前端不得直连模型供应商；所有请求通过 CertMuse 后端认证、授权、限流和审计。
3. 题目上下文必须来自冻结快照，不接受前端覆盖；跨用户资源统一按不存在处理。
4. 系统提示和标准答案不得通过接口、错误、日志或调试字段返回。
5. 输出在展示前执行内容安全检查；Markdown 渲染禁用原始 HTML、脚本、事件属性和危险 URL 协议。
6. 不记录 Cookie、Authorization、API Key、完整题目、标准答案、解析、用户消息、模型输出、图片内容或供应商原始请求/响应。
7. 生产环境不得启用可返回系统提示、模型原始载荷或完整上下文的调试接口。

### 10.2 允许记录和指标

日志允许记录：

- `requestId`、`traceId`、脱敏/哈希后的用户标识；
- `conversationId`、`practiceSessionId`、`questionOrder`、消息 ID；
- 模型供应商和模型名称的内部配置标识；
- 披露模式、状态、错误码、耗时、首 token 耗时；
- 输入/输出 token 数、限流命中类型、取消结果；
- 不含内容的字符数、图片数量和上下文消息数量。

至少提供以下指标：生成请求数、成功率、失败率、取消率、首 token P50/P95、总耗时 P95、各错误码数量、当前生成数、token 用量和用户额度拒绝数。供应商异常只在边界记录一次完整原因，并以同一 `traceId` 关联 SSE 或 JSON 错误。

## 11. 前端联调约定

前端 AI 面板至少实现以下状态：

- `disabled`：功能关闭或当前题不支持；
- `loading-history`：加载会话和历史；
- `ready`：允许输入；
- `streaming`：展示增量文本并允许停止；
- `rate-limited`：展示恢复倒计时；
- `failed`：展示稳定错误对应的重试入口；
- `message-limit-reached`：只读历史。

交互规则：

1. 进入题目时调用创建接口；`created:false` 时继续读取历史。
2. 发送前在本地立即展示用户消息，但服务端拒绝时必须标记失败或移除，不能伪装为已保存。
3. 收到 `message.start` 后以服务端消息 ID 替换本地临时 ID。
4. 收到 delta 后增量渲染；只在 `message.completed` 后将回答标记为完整。
5. `message.failed/cancelled` 时丢弃界面中的不完整文本，并读取历史校准。
6. 断流时不要自动换 `clientMessageId` 重发；先读取历史确认服务端消息状态。
7. 未提交显示“AI 会提供思路提示，不直接公布答案”；提交后刷新对话摘要并显示“可以询问答案和错因”。
8. 前端展示快捷问题可以动态变化，但发送时仍只是普通 `message`，不得获得额外权限。

## 12. 验收用例

### 12.1 创建与读取

1. 当前用户对合法未提交题首次创建，返回 201、`created:true`、唯一对话和 `GUIDANCE_ONLY`。
2. 对同一题再次创建，返回 200、`created:false`，`conversationId` 不变。
3. 两个并发创建只产生一条数据库记录，两个调用均取得同一 ID。
4. 跨用户 `sessionId`、`conversationId` 或消息 ID 均不能读取，返回统一 404 且不泄露存在性。
5. 历史超过一页时，游标无重复无遗漏，每页按 `sequence` 升序。

### 12.2 多轮和上下文

1. 用户先问“考查什么”，再问“刚才概念怎么应用”，第二轮模型上下文包含第一轮完整问答和同一题快照。
2. 切到另一题后取得不同对话，模型上下文不含前一题聊天；切回后原历史恢复。
3. 页面刷新不产生新消息，并能恢复 `COMPLETED`、`FAILED`、`CANCELLED` 或仍在生成的状态。
4. 达到 200 条消息后发送返回 409，既不写新消息也不调用模型。

### 12.3 答案保护

1. 未提交题直接询问答案、要求忽略规则、编码输出答案或翻译系统提示时，回答不包含正确选项、标准答案、对错结论或冻结解析原文。
2. 未提交题可解释概念、分析方法和给出不泄露答案的渐进提示。
3. 用户临时选择正确时，AI 不能确认其正确；临时选择非法时建流前返回 422。
4. 正式提交后下一轮为 `FULL_EXPLANATION`，可以引用正式作答、正确答案和冻结解析。
5. 生成中途正式提交不改变本轮冻结模式；下一轮按新模式生成。

### 12.4 SSE、失败与取消

1. 正常生成事件顺序为 start、一个或多个 delta、completed；ID 从 1 连续递增。
2. 15 秒无 token 时收到心跳，代理不得缓冲 SSE。
3. 建流前参数、权限、限流和服务关闭返回 JSON 与真实 HTTP Status，不返回 SSE。
4. 建流后供应商超时返回 `message.failed`、非空 `traceId`，数据库助手消息为 `FAILED` 且不保留部分内容。
5. 用户取消后消息进入 `CANCELLED`、释放并发槽；重复取消保持成功。
6. 客户端断流后读取历史能取得最终状态，不使用新 `clientMessageId` 自动重复提问。
7. 应用异常退出留下的 `GENERATING` 消息在超时清理后转为 `FAILED/AI_GENERATION_INTERRUPTED`。

### 12.5 幂等、限流与隔离

1. 相同 `clientMessageId`、相同载荷不会创建重复消息或重复模型调用。
2. 相同 `clientMessageId`、不同内容返回 409 幂等冲突。
3. 同一用户并发向两个题目发送，只允许一个生成，另一个返回 409。
4. 第 11 个滚动分钟请求返回 429，`Retry-After` 与响应详情一致；达到每日 100 次后按上海自然日恢复。
5. AI 服务关闭、供应商超时和对话失败时，原题读取、提交和结束接口仍正常工作。
6. AI 消息、临时选择和失败结果均不新增或修改正式作答、错题、画像及学习证据。

### 12.6 安全与日志

1. 日志中不出现完整题干、答案、解析、聊天内容、Authorization、Cookie、API Key 或供应商原始载荷。
2. Markdown 中的 HTML、脚本、事件属性和危险链接不能执行。
3. 题目图片只从冻结对象标识经 OSS 抽象读取；伪造前端图片 URL 无效。
4. 5xx 和流内失败具有非空 `traceId`，完整异常只在服务边界记录一次。

## 13. 开发交付要求

后端交付至少包括：

- 有序 PostgreSQL 迁移：对话表、消息表、动作幂等记录、必要唯一索引和清理索引；
- AI 服务功能开关、供应商适配接口、超时、熔断、限流和受保护配置；
- 对话上下文组装、答案披露策略、内容安全和 SSE 协议实现；
- `GENERATING` 超时清理任务；
- Controller、Service、Mapper、供应商适配器和权限测试；
- 本文第 12 节对应的契约、并发、断流和安全测试。

前端交付至少包括：

- 独立 AI 助教面板组件和 API 客户端；
- POST SSE 解析、心跳忽略、取消、断流恢复和错误状态；
- 多轮历史分页、切题隔离、提交前后提示语和 Markdown 安全渲染；
- 正常、失败、空历史、限流、关闭、消息上限和移动端布局测试。

生产启用前必须完成：

- AI/外部服务激活审批；
- 供应商数据处理和不用于训练配置确认；
- API Key、出口网络、额度告警和熔断演练；
- 数据库迁移 dry-run、回滚方案和功能开关回退验证；
- 恶意提示、答案泄露、越权、内容安全和成本压测验收。

## 14. 变更记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| V1.0 | 2026-08-17 | 首版：冻结题目级持久化多轮对话、提交前后披露策略、POST SSE、取消、幂等、限流、安全和验收规则。 |
