# 学生端题目 AI 讲解前端对齐契约

```yaml
document_id: learner-question-ai-chat-frontend-alignment
document_version: 1.0
status: implementation-ready
consumer: web/student
backend_source: 学生端题目AI多轮对话后端接口契约.md
updated_at: 2026-08-18
```

## 1. 范围

本文件只定义学生端 `/learning/session/practice?sessionId=...` 的 AI 讲解前端消费行为。后端路由、请求字段、响应、鉴权、持久化、答案披露和错误码以《学生端题目AI多轮对话后端接口契约》为唯一事实来源；本文件不新增或改变任何后端能力。

AI 对话以“当前用户 × 练习会话 × 题序”持久化。前端在用户点击当前题题干旁的“AI讲解”后才创建或取得对话，不能在题目页加载或切题时预创建。

## 2. 接口消费映射

| 前端动作 | 既有接口 | 前端规则 |
| --- | --- | --- |
| 首次打开当前题面板 | `POST .../knowledge-practices/{sessionId}/items/{questionOrder}/ai-conversations` | 生成新的小写 UUID 作为 `X-Request-Id`；随后读取摘要和最新历史页。 |
| 刷新披露状态 | `GET .../ai-conversations/{conversationId}` | 打开、提交本题后读取；按 `answerDisclosureMode` 更新提示文案。 |
| 初始与向前翻页 | `GET .../messages` | 首次不带游标；“加载更早对话”使用 `nextBeforeSequence`。数组按服务端顺序展示。 |
| 发送消息 | `POST .../messages/stream` | 使用原生 `fetch`；`message` 为 trim 后文本，`currentSelection` 为当前选择数组，`clientMessageId` 在本次发送的网络重试中保持不变。 |
| 停止、切题、卸载 | `POST .../messages/{assistantMessageId}/cancel` | 同时中止本地 fetch；只取消当前 `GENERATING` 助手消息。 |

读取摘要、历史和取消使用既有 `request`。创建对话与 POST SSE 使用原生 `fetch`：创建接口按后端契约可能返回 HTTP/`R.code` 201，而通用 axios 拦截器只将 200 视为成功；原生请求必须正确接收 200 与 201。两类原生请求均携带 `Authorization`、`clientid`、`Content-Language`、`X-Request-Id`；SSE 另携带 JSON 请求体和 `Accept: text/event-stream`。收到非 2xx 或非 SSE `Content-Type` 时，按 `R<AiChatErrorVo>` 读取 `data.errorCode`，不得作为 SSE 解析。

## 3. SSE 与前端状态

- 注释心跳不渲染、不改变消息状态。
- `message.start` 用服务端用户/助手消息 ID 替换本地临时用户消息，并新增 `GENERATING` 助手消息。
- `message.delta` 追加到同一助手消息；`completed` 标为完整；`failed`、`cancelled` 清除不完整文本并显示终态。
- 断流不自动更换 `clientMessageId` 重发。前端展示失败入口，用户主动重试时生成新的发送意图；再次打开面板或刷新页面从历史接口校准。
- `AI_CHAT_RATE_LIMITED` 显示后端给出的等待秒数；`AI_CHAT_MESSAGE_LIMIT_REACHED` 禁用发送；`AI_CHAT_DISABLED` 显示不可用；其余错误使用安全兜底文案。

## 4. 悬浮窗与本地清屏

- “AI讲解”按钮位于题干旁。面板固定在页面左侧，桌面端宽 `25vw`、高 `75vh`；移动端留 12px 边距并使用可视宽度。
- 标题栏是拖动把手；使用 Pointer Events，拖动位置限制在视口内 12px，收起后的位置仅在当前页面运行期保留。
- 收起不取消生成；再次展开继续显示已经累计的流式内容。
- 扫帚“清屏”只清空当前组件的可见消息和错误提示，不调用任何删除接口、不修改服务端历史，也不修改后端模型上下文。清屏后新发送的消息正常显示；同页重新展开继续保持清屏结果，页面刷新、重新进入题目或重新创建组件后按后端历史恢复。
- 切题时先取消正在生成的当前题回答，再切换到新题独立的对话状态；不同题的消息不得混合显示。

## 5. 安全与验收

- 助手 Markdown 先转换为 HTML，再通过既有 `sanitizeHtml` 净化后才使用 `v-html`；原始 HTML、事件属性和危险 URL 不能执行。
- 前端固定显示“AI 生成内容可能有误，请以题目正式解析为准”。
- 验收覆盖：打开与恢复历史、SSE 事件顺序、JSON 错误分流、停止与切题取消、收起继续流、拖动边界、本地清屏、限流/消息上限/关闭状态、刷新后服务端历史恢复和 Markdown XSS 净化。
