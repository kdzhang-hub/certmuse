# API 接口契约模板

> 契约版本：V1.0

## 1. 目标与边界

说明消费场景、包含与不包含的能力，以及兼容要求。

## 2. 接口定义

逐个接口定义方法、路径、认证、权限、请求头、路径参数、查询参数和请求体。每个字段写明类型、必填性、格式、长度、范围、枚举和空值语义。

## 3. 成功响应

提供完整 `R<T>` JSON 示例，定义字段、分页、排序、时间及时区口径。真实 HTTP Status 必须等于 `R.code`。

## 4. 失败响应

提供完整 JSON 示例和错误表：HTTP Status、`errorCode`、触发条件、`retryable`、字段错误及前端行为。前端只根据 `errorCode` 分支，不匹配 `msg`。

CertMuse 业务接口遵循以下异常边界：

- 真实 HTTP Status 必须等于 `R.code`。
- 预期业务失败使用稳定的 `data.errorCode`；不得使用 `IllegalArgumentException`、`ServiceException` 或异常文案表达可预期的 CertMuse 业务分支。
- 400 字段错误码统一使用 `REQUIRED`、`INVALID_FORMAT` 或 `OUT_OF_RANGE`，字段路径使用请求 JSON 的 camelCase 名称；无字段错误时返回空数组。
- 请求参数类型转换、Controller 方法参数校验和参数约束失败属于客户端可修正的 400；返回值校验、缺失路径变量等服务端配置或实现错误不得伪装为 400，应进入安全 500。
- 4xx 默认不生成 `traceId`，除非具体冻结契约或既有业务流程明确要求。
- 5xx 必须返回安全文案和非空 `traceId`；同一个 `traceId` 用于响应和服务端唯一一次完整异常日志。
- 401/403 沿用认证兼容格式，只承诺 `code/msg` 且 `data` 为空，不承诺 `data.errorCode`。
- 上述规则只适用于 CertMuse 新建或已迁移接口；尚未迁移的 RuoYi 公共接口继续遵守其既有兼容契约。

```json
{
  "code": 409,
  "msg": "资源已被其他操作更新",
  "data": {
    "errorCode": "DOMAIN_RESOURCE_VERSION_CONFLICT",
    "retryable": true,
    "traceId": null,
    "fieldErrors": [],
    "details": null
  }
}
```

## 5. 行为规则

定义权限与资源可见性、幂等键和重复调用、并发冲突、状态转换、分页、时间、重试及依赖失败行为。

## 6. 安全与可观测性

定义敏感字段、脱敏、日志字段、requestId/traceId 传播，以及禁止返回或记录的数据。

## 7. 验收用例

列出成功、参数错误、认证、权限、不存在、冲突、业务门禁和系统故障用例；每例包含输入及可断言的状态、错误码和响应结构。

## 8. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | YYYY-MM-DD | 初稿 |
