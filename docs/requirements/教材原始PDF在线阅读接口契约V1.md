# 教材原始 PDF 在线阅读接口契约 V1

## 目标与边界

本契约新增独立教材原始 PDF 附件和阅读能力，不改变现有教材导入、发布、下架、内容块或知识点绑定接口。

## 接口

| 调用方 | 方法与路径 | 权限 | 行为 |
|---|---|---|---|
| 管理端 | `GET /api/admin/catalog/textbooks/{textbookId}/original-pdf` | `certmuse:catalog:resource:query` | 返回安全附件元信息。 |
| 管理端 | `POST /api/admin/catalog/textbooks/{textbookId}/original-pdf` | `certmuse:catalog:resource:edit` | `multipart/form-data` 的 `file`，仅草稿教材可上传或替换。 |
| 管理端 | `DELETE /api/admin/catalog/textbooks/{textbookId}/original-pdf` | `certmuse:catalog:resource:edit` | 仅草稿教材可删除原始 PDF 附件；不删除 JSON 教材、内容块、知识点绑定或教材主记录。 |
| 管理端 | `GET /api/admin/catalog/textbooks/{textbookId}/original-pdf/reader` | `certmuse:catalog:resource:query` | 返回短时效阅读 URL。 |
| 学生端 | `GET /api/learning/textbooks` | `certmuse:student` + `certmuse:learning:textbook:query` | 返回当前学习资格下的已发布、已附 PDF 教材。 |
| 学生端 | `GET /api/learning/textbooks/{textbookId}/reader` | 同上 | 返回经资格与发布状态校验的短时效阅读 URL。 |
| 游客 | `GET /api/public/textbooks/certifications` | 无 | 返回可用于临时浏览选择的启用资格。 |
| 游客 | `GET /api/public/textbooks?certificationId={id}&keyword={text}` | 无 | 返回该启用资格下已发布、已附 PDF 的教材。 |
| 游客 | `GET /api/public/textbooks/{textbookId}/reader` | 无 | 仅为已发布、资格仍启用的教材签发匿名短时效阅读 URL。 |

所有 JSON 响应使用 `R<T>`，不返回 OSS 对象键。上传只接受非空 PDF、默认最大 200 MiB（受保护运行配置 `certmuse.textbook-pdf.max-size-bytes` 可调整）。教材发布即允许游客阅读其原始 PDF；下架、删除 PDF、资格停用后不再能签发新的匿名阅读 URL。

| HTTP / `R.code` | `data.errorCode` | 场景 |
|---|---|---|
| 400 | `TEXTBOOK_PDF_REQUEST_INVALID` / `TEXTBOOK_PDF_INVALID` | 教材 ID、文件名、文件头或请求格式不合法。 |
| 404 | `TEXTBOOK_NOT_FOUND` / `TEXTBOOK_PDF_NOT_FOUND` | 教材、附件或学习资格不可访问；学生端用相同响应避免资源枚举。 |
| 409 | `TEXTBOOK_PDF_UPLOAD_FORBIDDEN` / `TEXTBOOK_PDF_DELETE_FORBIDDEN` / `TEXTBOOK_PDF_DELETE_CONFLICT` | 教材不是草稿，或删除时附件已被其他操作变更。 |
| 413 | `TEXTBOOK_PDF_TOO_LARGE` | 文件超过运行配置的上限。 |
| 422 | `TEXTBOOK_PDF_NOT_READY` | 预留给后续需要显式报告的教材分发门禁；当前未以此替代 404 的访问隐藏策略。 |
| 503 | `TEXTBOOK_PDF_STORAGE_UNAVAILABLE` | OSS 上传、签名或持久化暂不可用；`retryable=true`。 |

## 安全与验收

- OSS 原文件必须私有；登录阅读 URL 与匿名阅读 URL 均为 60 分钟失效的同源 ticket。匿名 ticket 只能由 `/api/public/**` 签发和解析，登录 ticket 不得通过匿名 reader 路径读取。
- 前端隐藏下载与打印入口并显示阅读水印，但不能承诺浏览器侧绝对防下载。
- 管理端不可见教材、学生非当前资格、已下架教材和无 PDF 教材均不得获取阅读 URL；游客只可获取已发布、资格启用且有 PDF 教材的匿名 URL。
- PDF.js 的跨域读取依赖私有 OSS Bucket 的 CORS 规则：只允许管理端与学生端实际前端来源发起 `GET`、`HEAD` 和 `Range` 请求；允许请求头 `Range`；暴露响应头 `Accept-Ranges`、`Content-Range`、`Content-Length`、`Content-Type`。不得为此设置 `*` 来源、公共 Bucket 或公开目录。
- 替换上传先完成 OSS 写入，再在短数据库事务中锁定教材并写入附件；持久化失败时服务端尽力删除新对象，替换成功后通过既有持久化清理队列异步删除旧对象。删除附件也在短事务内锁定草稿教材、仅删除附件表记录，并在成功后异步清理对应私有对象。
