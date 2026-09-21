# 主观题 AI 评分前端交接 V1

## 目标与范围

学生端需在以下入口支持 `CASE`（案例题）和 `ESSAY`（论文题）的纯文本作答、异步 AI 评分状态展示和结果展示：

- 首次诊断；
- 历年真题练习、历年真题考试；
- 模拟考试；
- 错题订正。

选择题现有交互与提交格式不变。主观题首期不支持富文本、图片、附件、子问或断点续传。

## 页面改造清单

| 页面/流程 | 前端需要完成的内容 |
| --- | --- |
| 题目组件 | 按 `questionType` 渲染：`CHOICE` 使用既有选项组件；`CASE`、`ESSAY` 使用多行文本框。 |
| 文本输入 | 必填、去首尾空格后非空；最多 20,000 Unicode 字符；显示实时字数和超限提示。 |
| 练习/订正提交 | 提交成功后锁定该题输入；状态为 `pending` 时展示“AI 正在评分”，不展示参考答案、解析或内部零分。 |
| 考试/模拟 | 草稿需随会话恢复；交卷后跳转结果等待页，按结果状态轮询。 |
| 结果页 | 分题展示得分率/反馈；结果为 `PARTIAL` 时提示“部分题目 AI 评分异常，未计入错题与能力画像”。 |
| 错题订正 | 若结束接口返回 `MISTAKE_CORRECTION_GRADING_PENDING`，留在会话页继续轮询，不得将会话视为结束。 |

## 作答请求

### 真题与诊断、错题订正

主观题沿用各自既有提交接口外层结构；`answer` 内传入以下 JSON：

```json
{
  "schemaVersion": "subjective_answer/1.0",
  "questionType": "CASE",
  "value": "学生作答正文"
}
```

字段规则：

- `schemaVersion` 固定为 `subjective_answer/1.0`；
- `questionType` 必须与题目返回的 `questionType` 一致，只能是 `CASE` 或 `ESSAY`；
- `value` 为纯文本，最大 20,000 Unicode 字符；
- 保留既有 `X-Request-Id`，同一次用户提交重试必须复用同一个 UUID。

### 模拟考试

提交路径：

```text
POST /api/assessment/simulations/sessions/{sessionId}/items/{questionOrder}
```

选择题请求保持单选数组：

```json
{ "value": ["A"] }
```

主观题请求：

```json
{ "text": "学生作答正文" }
```

请求头必须携带 `X-Request-Id`。每题提交后不可编辑；若网络超时，使用原请求号重试，避免重复提交。

## 模拟考试接口与状态

| 接口 | 使用时机 | 前端处理 |
| --- | --- | --- |
| `POST /api/assessment/simulations/{collectionId}/sessions` | 点击开始考试 | 保存 `sessionId`、`deadlineTime`，进入答题页。 |
| `GET /api/assessment/simulations/sessions/{sessionId}` | 首次进入、刷新页、恢复会话 | 使用服务端 `deadlineTime` 计算倒计时；以 `navigation` 恢复题目进度。 |
| `GET /api/assessment/simulations/sessions/{sessionId}/items/{questionOrder}` | 切题、轮询评分 | 渲染冻结题目、已提交答案和 `gradingStatus`。 |
| `POST /api/assessment/simulations/sessions/{sessionId}/items/{questionOrder}` | 提交单题 | 选择题立即显示已提交；主观题显示待评分。 |
| `POST /api/assessment/simulations/sessions/{sessionId}/finish` | 用户确认交卷或倒计时结束 | 跳转结果等待页。 |
| `GET /api/assessment/simulations/sessions/{sessionId}/result` | 等待页轮询 | 根据 `status`、`scoreComplete`、`aiFailedCount` 渲染最终结果。 |

### `gradingStatus` 映射

| 后端值 | 显示文案 | 允许动作 |
| --- | --- | --- |
| `pending` / `processing` | AI 正在评分 | 轮询；不展示参考答案与解析。 |
| `graded` | 已评分 | 展示得分和 AI 反馈。 |
| `failed` | AI 评分异常 | 展示“该题评分失败，请稍后查看或联系管理员”；不显示“0 分”。 |
| `null` | 未提交 | 允许作答。 |

建议轮询间隔为 2 秒；连续 30 次仍处于 `pending/processing` 时停止自动轮询并提供“重新查询”按钮。页面离开或组件卸载时必须取消定时器。

### 模拟结果状态

- `COMPLETED`：展示 `score`、`maxScore` 和完整结果。
- `PARTIAL`：展示已有总分，但必须标识结果不完整，并展示 `aiFailedCount`。
- `PROCESSING`：保留等待页，继续轮询。

## 异常与交互规则

| 错误码 | 前端动作 |
| --- | --- |
| `MISTAKE_CORRECTION_GRADING_PENDING` | 提示订正题仍在评分，继续轮询题目后再结束。 |
| `SIMULATION_SESSION_NOT_ACTIVE` | 停止作答，刷新会话状态；已交卷则跳转结果页。 |
| `SIMULATION_REVISION_CHANGED` | 提示试卷已更新，返回详情页后重新开始。 |
| `SIMULATION_GOAL_VERSION_CONFLICT` | 提示学习目标已变化，刷新目标/试卷页。 |
| `AI_PROVIDER_UNAVAILABLE` / `AI_PROVIDER_TIMEOUT` / `AI_OUTPUT_INVALID` | 仅显示通用“AI 评分异常”；不要展示原始技术错误。 |

对于所有 `409` 响应，先查询会话/题目最新状态再决定提示；不要盲目重复提交新请求号。

## 安全与展示边界

- 不在浏览器持久化参考答案、量表、`grading_result` 原始 JSON 或 AI provider 信息。
- `pending` 与 `failed` 的主观题不得把内部 0 分渲染成学生可见成绩。
- 所有题目和结果以服务端返回的冻结快照为准，前端不得根据当前题库重新拼装题干或选项。
- 倒计时以服务端 `deadlineTime` 为基准；前端本地时间只用于显示。

## 前端验收清单

- [ ] CASE、ESSAY 与 CHOICE 使用正确的输入组件。
- [ ] 空文本、纯空白文本、超过 20,000 字符不能提交。
- [ ] 主观题提交后能展示 `pending → graded/failed` 状态切换。
- [ ] `pending` 时不展示参考答案、解析或评分零分。
- [ ] 模拟考试刷新页面后能恢复剩余时间、导航和已提交答案。
- [ ] `PARTIAL` 结果有明确异常提示，不误导为完整成绩。
- [ ] 错题订正 AI 评分中不能结束会话。
- [ ] 网络重试复用原 `X-Request-Id`，不重复创建答案。
