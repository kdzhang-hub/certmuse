# 主观题 AI 评分后端接口契约 V1

## 范围

本契约为 `CASE` 与 `ESSAY` 提供纯文本作答、异步 AI 评分和错题订正能力。首期接入首次诊断、历年真题、模拟考试和错题订正。选择题接口和同步判分语义保持不变。

## 答案格式

主观题草稿或提交的 `answer` 为：

```json
{
  "schemaVersion": "subjective_answer/1.0",
  "questionType": "CASE",
  "value": "学生作答正文"
}
```

`value` 必须为非空纯文本，最多 20,000 个 Unicode 字符。`CASE` 与 `ESSAY` 均不支持附件、HTML 或子问。

## 评分与量表

评分量表仅依据冻结的题干、参考答案和知识点生成，并按题目修订持久化。学生答案不得作为量表生成输入。首次生成成功后量表不可修改；新题目修订生成新量表。评分完成前返回 `SUBMITTED_PENDING_GRADING`，完成后返回 `GRADED` 或 `GRADING_FAILED`。

评分成功时，`scoreRate < 0.40` 创建错题并写负向画像证据；其他得分率写正向画像证据。错题订正只关闭或保留错题，不写画像。

AI 调用连续三次失败或结果不符合冻结量表时，服务端记录 `grading_status=failed` 和内部 0 分占位，但响应不返回该占位分数，不创建错题或画像，并使报告标记为 `PARTIAL`。

## 入口与响应状态

所有入口均要求学生身份、资源归属校验和既有 `X-Request-Id` 幂等规则。正常响应继续使用 `R<T>`；客户端仅依据下列稳定状态和错误码，不依据中文文案。

| 入口 | 提交方式 | 待评分表现 | 评分失败表现 | 成功后的结算 |
| --- | --- | --- | --- | --- |
| 首次诊断 | 原诊断草稿/交卷接口 | 诊断任务保持处理中 | 报告为 `PARTIAL` | `<0.40` 错题与负向证据；其余正向证据 |
| 历年真题练习 | 原练习提交接口 | 题目 `gradingStatus=pending`，不披露参考答案 | `gradingStatus=failed`，不披露内部零分 | 按得分率写证据和错题 |
| 历年真题考试 | 原考试草稿/交卷接口 | 结果处理中 | `PARTIAL` | 按得分率写证据和错题 |
| 模拟考试 | `POST /api/assessment/simulations/sessions/{sessionId}/items/{questionOrder}` | `gradingStatus=pending` | `gradingStatus=failed`；结果为 `PARTIAL` | 按得分率写证据和错题 |
| 错题订正 | 原订正提交接口 | 不能结束会话，返回 `MISTAKE_CORRECTION_GRADING_PENDING` | 保持待订正 | `>=0.40` 关闭关联待订正错题；不写画像 |

模拟考试补充接口：

- `GET /api/assessment/simulations/sessions/{sessionId}`：会话、倒计时与导航状态。
- `GET /api/assessment/simulations/sessions/{sessionId}/items/{questionOrder}`：冻结题目和当前评分状态。
- `POST /api/assessment/simulations/sessions/{sessionId}/finish`：提交会话，返回 `PROCESSING`。
- `GET /api/assessment/simulations/sessions/{sessionId}/result`：返回 `COMPLETED` 或 `PARTIAL`、总分、满分、`scoreComplete`、`aiFailedCount` 与每题冻结题面（含选项和受控图片 URL）。

## 错误码与验收

- `AI_PROVIDER_UNAVAILABLE`、`AI_PROVIDER_TIMEOUT`、`AI_GENERATION_INTERRUPTED`、`AI_OUTPUT_INVALID`：仅写入任务/安全评分结果，绝不返回提供商原始错误。
- `MISTAKE_CORRECTION_GRADING_PENDING`：订正会话仍有主观题正在评分，客户端应继续轮询题目状态后再结束。
- `SIMULATION_SESSION_NOT_ACTIVE`：模拟会话已交卷、已结束或已超时，拒绝继续作答。
- `SIMULATION_REVISION_CHANGED`、`SIMULATION_GOAL_VERSION_CONFLICT`：开考前版本校验失败，客户端刷新页面后重试。

验收案例：同一 `question_revision_id` 的第一次主观题答案只会创建一条量表任务，后续答案复用已持久化量表；任一任务重试三次后失败；同一题的 39% 与 40% 分别产生“保留/创建错题”与“正向证据/关闭订正错题”的边界结果；AI 失败不影响选择题的同步评分，也不产生错题或画像证据。
