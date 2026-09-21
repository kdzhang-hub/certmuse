# CertMuse 后端专项约定

## 适用范围与决策顺序

本文件只适用于 `ruoyi-modules/ruoyi-certmuse`。先遵守当前任务适用的 `AGENTS.md`，再用本专项补充 CertMuse 的差异化决策；现有代码只能在不违反成文规范时作为实现参考。完整优先级以 [SKILL.md](../SKILL.md) 为准。

题目修订、内容导入、知识树、异步任务、幂等操作、审计和多表关系维护属于复杂领域用例。简单配置、映射或字典式管理，并且不存在状态机、修订、幂等、多表事务和特殊可见性时，才属于标准 CRUD。

## Controller 与入口 Service

- Controller 负责接参、基础校验、`@SaCheckPermission`、操作日志和响应包装，不承载领域编排。
- Controller 直接依赖的入口 Service 必须是 `service` 包下的接口；接口名称不加 `I` 前缀。
- 实现类放在 `service.impl`，使用 `接口名 + Impl`，并通过 `implements` 显式实现接口。
- `ImportService` + `ImportServiceImpl` 是现有标准示例。
- 题目入口服务目标结构为 `QuestionService` 接口 + `QuestionServiceImpl` 实现。
- 知识树同时包含查询和删除，目标结构为 `KnowledgeTreeService` 接口 + `KnowledgeTreeServiceImpl` 实现，不继续使用会误导职责的 `KnowledgeTreeQueryService` 名称。
- Service 接口及关键领域方法使用简洁 JavaDoc，说明业务动作、关键参数和返回语义。

上述接口要求只适用于 Controller 直接依赖的入口服务。持久化、图片 URL、存储、解析、调度和 Worker 等辅助组件不强制抽接口，也不新增 `service.internal` 或 `service.support` 分包。`service.impl` 可以保留 `ImportPersistenceService` 这类现有持久化实现组件，领域根目录下已有的 `support` 包继续沿用。

## 领域动作与标准 CRUD

复杂领域入口接口使用能够表达业务语义的方法，例如：

- 题目：`list`、`detail`、`save`、`preview`、`delete`。
- 导入：`createQuestion`、`createTextbook`、`validate`、`confirm`、`progress`、`preview`。
- 知识树：`syllabuses`、`knowledgeTree`、`deleteKnowledgeTree`。

不要为了形式统一补充可能绕过版本、幂等、审计、状态校验、资源归属或删除前校验的通用增删改方法。

只有同时满足以下条件时，才提供 `queryById`、`queryPageList`、`queryList`、`insertByBo`、`updateByBo`、`deleteWithValidByIds` 标准方法簇：

- 单表或近似单表管理。
- 没有状态机、修订链和幂等协议。
- 没有多表原子写入或复杂关联维护。
- 没有超出常规部门/本人范围的资源可见性规则。

符合这些条件的新功能可以读取 generator 模板，并使用通用后端 reference；否则继续采用领域动作。

## 持久层选型

CertMuse 运行在仓库统一的 MyBatis-Plus 环境中，同时允许 MyBatis-Plus Mapper 与原生 MyBatis XML Mapper 在同一模块共存。持久层按业务复杂度选择，不以统一技术形式或减少 XML 数量为目标。

### 什么时候使用 MyBatis-Plus

新增功能满足以下条件时，默认使用 `BaseMapperPlus<Entity, Vo>`、`QueryBuilder` 和项目已有 Wrapper 能力：

- 一个 Mapper 主要对应一张业务表。
- 查询条件可以通过 Wrapper 清晰表达。
- 写入是普通新增、按 ID 修改、逻辑删除或批量单表操作。
- 不涉及修订选择、行锁、幂等写入、多表关系整体替换或复杂审计。

简单配置、模板、映射、字典式管理优先采用这种方式，不为它们新增无必要的 XML SQL。

### 什么时候保留或使用 MyBatis XML

存在以下任一情况时，可以继续使用普通 Mapper 接口和 XML 手写 SQL：

- 复杂联表、聚合、递归树、`EXISTS` 或当前修订选择规则。
- `SELECT ... FOR UPDATE`、带版本条件的更新或数据库特有语法。
- `ON CONFLICT`、批量落库、修订复制或多张关系表维护。
- 一个领域操作需要同时读取或写入多张表，并且 SQL 显式表达更清晰。
- 查询返回持久化投影、聚合 VO 或 record，而不是单表 Entity。

题目、导入和知识树现有 Mapper 默认保留 XML；不得仅为了改成 MyBatis-Plus 而重写已稳定的复杂 SQL。

### 混合使用与迁移规则

- 同一业务域可以同时存在单表 `BaseMapperPlus` Mapper 和复杂领域 XML Mapper，Service 在事务中统一编排。
- 两类 Mapper 的职责必须清晰，不要为同一条简单查询重复维护 Wrapper 和 XML 两套实现。
- 现有 XML 只有在 SQL 已退化为稳定、独立的单表 CRUD，且迁移能减少代码并保持权限、锁、事务和返回语义时，才考虑改用 MyBatis-Plus。
- 不要仅为让 Mapper 继承 `BaseMapperPlus` 而创建没有真实领域意义的 Entity、拆散原子操作或改变接口契约。
- `@DataPermission` 可以作用于符合拦截器要求的 XML SQL，接入数据权限本身不是迁移到 MyBatis-Plus 的理由。

## Mapper 与数据模型

- 复杂查询、锁定、审计、修订和批量关系维护允许使用 MyBatis XML Mapper。
- 允许使用 record、持久化投影和手工 DTO 组装，不强制切换为 `BaseEntity`、`BaseMapperPlus` 或 `@AutoMapper`。
- 不要为了套用 generator 而把多表领域模型伪装成单表 Entity CRUD。
- SQL 参数、结果映射和表别名必须与 Mapper 接口及数据权限配置保持一致。

## Domain 模型组织与粒度

本节在 `backend/AGENTS.md` 的“一文件一个公开顶层模型”约束下补充 CertMuse 的模型职责。简单 CRUD 继续执行通用规范；复杂 XML Mapper 可以使用专用投影和写入参数，但不得因此创建新的聚合模型容器。

### 目录职责

CertMuse 不为模型粒度额外增加通用 `model`、`dto` 或 `common` 包，沿用以下结构：

```text
domain/
├── CmQuestion.java                 # 表模型，不使用 Entity 后缀
├── QuestionRevisionRow.java        # XML 查询投影
├── QuestionImageRow.java           # XML 查询投影
├── TextbookChunkInsert.java        # Mapper 写入参数
├── TextbookRecordResultUpdate.java # Mapper 更新参数
├── bo/                             # Controller 请求与查询模型
└── vo/                             # 对外响应模型
```

- `domain.bo` 只放 Controller 请求、查询和命令模型，例如 `XxxQueryBo`、`XxxSaveBo`、`XxxCreateBo`。
- `domain.vo` 只放对外响应模型，例如 `XxxListVo`、`XxxDetailVo`、`XxxPreviewVo`、`XxxResultVo`。
- `domain` 根目录放表模型、稳定领域模型和 Mapper 专用的顶层投影或写入参数。
- 表模型沿用表对应的业务名称，例如 `CmQuestion`，不增加 `Entity` 后缀。
- 每个 Java 文件只定义一个公开顶层模型；新代码不得新增 `XxxModels`、`XxxRows`、`XxxWriteModels` 或类似兜底容器。
- 不使用 Entity、Mapper 投影或写入模型直接作为 Controller 请求或响应。

### 什么时候创建独立模型

满足以下任一条件时，可以使用独立顶层类：

- 是 Controller 的主要请求 BO 或主要响应 VO。
- 是 MyBatis-Plus 单表模型。
- 被多个 Mapper、Service、Validator 或 Worker 复用。
- 具有独立业务身份、生命周期、校验或序列化契约。
- 独立命名能明显提升 Mapper XML、Service 或测试的可读性。

模型是否独立建类以职责、复用范围和生命周期为依据，不以字段数量或引用次数作为唯一标准。

Mapper 专用模型即使只服务一个 Mapper 或流程，也使用语义明确的独立顶层名称，例如 `QuestionRevisionRow`、`TextbookChunkInsert`。不要按 Mapper 方法机械拆类；只有参数或结果确实形成稳定、可命名的数据结构时才创建模型。只在一个 Service 实现类内部使用、且不需要 Mapper XML 直接引用的短小模型，可以定义为实现类的 `private record`。

### MyBatis 映射兼容性

- XML `resultType` 使用顶层模型的全限定类名。
- 依赖无参构造和 setter 属性填充的查询投影，使用普通顶层类配合 Lombok `@Data`。
- record 只用于构造器映射明确、列名与组件稳定对应并已有 Mapper 测试覆盖的场景；不得为了不可变形式牺牲 MyBatis 映射可靠性。
- Mapper 写入参数可以使用 record，但参数名、编译器保留信息和 XML 属性访问必须通过测试验证。
- 调整模型结构时，要同步修改 Mapper 接口、XML 全限定类型名、Service 引用和相关测试。

### BO、VO 与 Mapper 模型边界

- BO 负责基础格式校验；跨字段、状态、版本、资源归属和删除条件仍由 Service 校验。
- `@AutoMapper` 只用于确实需要在 BO/VO 与单表 Entity 间转换的简单 CRUD；XML Mapper 投影和写入模型不使用 `@AutoMapper`。
- 列表、详情、预览和操作结果属于不同公开契约时分别建 VO，不合并成包含大量可空字段的通用 VO。
- 只作为一个父 VO 内部元素且不会独立返回的类型，优先定义为父 VO 的嵌套类型。
- 公开 VO 中雪花 ID 使用 `String`，内部 Mapper 模型可以使用 `long` 或 `Long`。
- 公开 VO 不得包含 `storagePath`、对象键、幂等响应体、审计内部字段或其他敏感持久化信息；集合无数据时返回空集合，不返回 `null`。
- 当 XML 查询结果与一个只读 VO 完全一致、不会带入内部字段且不存在复用歧义时，可以直接映射 VO；否则先映射 Rows，再由 Service 组装 VO。

### 演进规则

- 本规则约束新代码和被实际修改的模型，不要求在无关任务中一次性重构遗留模型。
- 现有 `QuestionRows` 是兼容性例外：无关修改不得顺手拆分；新代码不得仿照它新增模型容器。只有专项重构且能同步修改 Mapper、XML、Service 和测试时，才拆成语义明确的顶层模型。
- 不要一个 Mapper 方法机械拆一个顶层文件，也不要为了减少文件数合并无关模型。
- 不使用 `Map<String, Object>` 替代字段和语义明确的 BO、VO 或 Mapper 投影。

## 数据权限

CertMuse 使用三层权限模型：

1. `@SaCheckPermission` 控制当前用户能否调用接口。
2. 显式资源可见性控制当前用户能否访问具体数据，是不可省略的安全基线。
3. `@DataPermission` 只在需要复用角色的全部、部门、部门及下级、本人或自定义数据范围时叠加使用。

具体约束：

- 列表查询可以通过 `visibleUserId`、资源归属条件或等价领域策略显式限制数据。
- 单条详情、行锁查询、修改、删除、异步任务推进和幂等重放必须显式校验资源可见性，不得仅依赖 `@DataPermission`。
- 引入 `@DataPermission` 时，在 Mapper 方法上配置 `@DataColumn`，其列名必须包含 XML SQL 的真实表别名，例如 `q.create_by` 或 `b.create_dept`。
- 框架数据范围与显式业务可见性共同生效，不得用注解覆盖或移除领域约束。
- 超级管理员、本人、同部门、跨部门、自定义范围和不可见资源都要有集成测试；复杂 XML 还要验证权限条件生成后的 SQL 可执行。

## 校验、事务与幂等

- BO 和 Controller 参数负责必填、长度、格式、数值范围等基础校验。
- 字段之间的组合规则、状态流转、版本冲突、资源归属和删除条件放在 Service。
- 多表写入使用 `@Transactional(rollbackFor = Exception.class)`，事务边界覆盖领域数据、关系数据、幂等结果和审计记录的一致性要求。
- 保留现有 `X-Request-Id`、请求载荷哈希、幂等记录、行版本、锁定和审计链路，不使用 `@RepeatSubmit` 替代领域幂等协议。
- 异步导入的任务受理、Worker 执行和持久化阶段要保持现有状态机与失败回执语义。

## 日志与审计

CertMuse 继承 RuoYi 的 `@Log` 操作日志，并为异步任务和复杂领域流程补充少量结构化运行日志。操作日志、运行日志和业务审计职责不同，不互相替代，也不要对同一事件机械重复记录。

### RuoYi 操作日志

- 新增、修改、删除、导入、导出、确认等管理写接口使用 `@Log`，`BusinessType` 必须与真实操作一致。
- 普通列表、详情、选项、进度和预览等只读接口默认不加 `@Log`，除非存在明确审计要求。
- `@Log` 默认保存请求和响应；涉及题干、答案、解析、上传文件或大对象时，必须通过 `isSaveRequestData = false`、`isSaveResponseData = false` 或 `excludeParamNames` 避免保存敏感或大体积内容。
- 题目保存等内容型接口优先同时关闭请求和响应保存；文件导入接口至少关闭请求保存。

```java
@Log(
    title = "题目草稿",
    businessType = BusinessType.UPDATE,
    isSaveRequestData = false,
    isSaveResponseData = false
)
```

### 结构化运行日志

- 只记录 `@Log` 无法覆盖的后台生命周期和关键状态变化，例如任务受理、Worker 开始、阶段完成、任务完成、任务失败，以及创建新题目修订。
- 日志使用参数化占位符，不使用字符串拼接，不序列化完整 BO、VO、Entity、文件内容或请求体。
- 按场景携带必要的关联字段，例如 `traceId`、`requestId`、`batchId`、`questionId`、`revisionId`、`stage`、`operatorId`、`durationMs` 和汇总计数。
- 异步任务失败日志至少包含业务主键和 `traceId`；向外返回的 `traceId` 应能关联到同一次失败日志。
- 一个异常只在能够补充完整上下文的边界记录一次；全局异常处理器已记录的未预期异常，不在 Service 和 Worker 层重复打印相同堆栈。

```java
log.info(
    "Import job completed, batchId={}, validCount={}, failedCount={}, durationMs={}, traceId={}",
    batchId,
    validCount,
    failedCount,
    durationMs,
    traceId
);
```

### 日志级别

- `debug`：本地诊断信息，生产环境默认不依赖该级别排查核心流程。
- `info`：异步任务开始/完成、重要修订创建和明确需要观察的状态变化。
- `warn`：可恢复失败、降级、重试、清理失败或数据异常但主流程仍可继续。
- `error`：未预期异常或任务最终失败，记录异常对象和 `traceId`。
- 参数错误、资源不存在、版本冲突、状态不可编辑等预期业务拒绝通常不记录 `error`；只有需要安全审计或异常频率监控时才按聚合方式记录。

### 控制台与日志量控制

- 一次同步写请求只在有必要的关键业务结果处记录，不为每个 Controller、Service 或 Mapper 方法固定打印开始和结束。
- 一次异步任务通常记录开始、成功或失败等少量关键节点；阶段日志只在阶段耗时长或需要独立定位时增加。
- 批量导入按批次和阶段记录汇总，不逐题、逐知识点、逐文档块打印 `info`。
- 循环内部、Mapper 查询、分页读取和普通详情查询不得默认打印 `info`。
- 单条数据只有在异常定位确有必要时记录其 ID，不记录完整业务内容。
- 生产环境不依赖 MyBatis SQL DEBUG；SQL 调试日志与业务生命周期日志分开配置，避免大量 SQL 淹没关键事件。

### 敏感信息与业务审计

- 禁止记录密码、Token、Authorization、Cookie、题干全文、答案、解析、上传文件内容、完整请求 JSON、MinIO 凭证、内部对象键、受控 URL 和幂等响应体全文。
- 如果存储路径或对象键是定位清理失败所必需，只记录脱敏后的摘要、业务 ID 或不可逆哈希，不直接输出完整值。
- 删除、修订创建、发布、驳回、下架、权限敏感变更等需要长期追溯的事件写业务审计表或领域事件；文本日志不能作为唯一审计依据。
- 已有业务审计或修订事件能够完整表达结果时，运行日志只补充执行状态和关联标识，不重复保存 before/after 全量数据。

## API 契约与异常处理

- 跨领域复用的 HTTP 异常能力放在 org.dromara.certmuse.shared.web，不新增 common 或无归属工具包。
- CertMuseApiException 携带 HTTP 状态、稳定 errorCode、retryable、可空 traceId、字段错误、契约附加信息和原始 cause。
- 已冻结的领域错误 VO 继续使用；不得为了统一类型而删除版本信息、阻断项等稳定字段。
- CertMuseErrorResponses 保证真实 HTTP Status 与 R.code 一致。前端只依赖 errorCode，不匹配 msg。
- 400 表示格式或绑定错误，401 表示认证失败，403 表示权限不足，404 表示资源不可用，409 表示状态、版本或幂等冲突，413 表示上传过大，422 表示业务门禁失败，429 表示限流，500 表示未知故障，503 表示临时依赖不可用。
- 领域 Advice 只映射领域异常或 multipart、SSE 等特殊协议，不捕获通用 Exception。模块 Advice 统一处理请求、认证、权限和未知异常，并使用显式 Order。
- Controller 不捕获宽泛异常。Service 转换解析、序列化或基础设施异常时保留 cause；需要回滚的运行时失败必须逃逸事务。
- 4xx 默认不打印完整堆栈。未知 500 在模块边界生成或传播 traceId，只记录一次完整 cause，响应不泄漏内部或敏感信息。
- 测试断言 HTTP Status、R.code、errorCode、retryable、traceId 和字段错误；覆盖适用状态、安全 500、cause 保留和事务回滚。
- 存量迁移先让领域异常继承统一基类，再移除领域 Advice 中重复的请求、权限和未知异常处理，保留冻结错误码和领域 VO。
- 仅为增强诊断而补充 cause 时，必须保持既有 HTTP 状态、错误码、文案、领域 VO、事务传播和异步状态语义不变；预期 4xx 格式错误无需强制保留 cause。

## 修改现有实现时的边界

- 当前接口路径、请求响应结构、字符串化雪花 ID 和错误码属于兼容性约束，除非任务明确要求，否则不得顺手调整。
- 抽取 Service 接口时保持 Controller 行为和实现逻辑不变，只移动公开契约并让实现类显式 `implements`。
- 不因新增接口而改写 Mapper SQL、幂等协议或事务流程。
- 辅助组件只有在存在多实现、需要跨模块契约或任务明确要求时才抽接口。

## 交付前自检

- Controller 是否只注入入口 Service 接口。
- 接口是否无 `I` 前缀，实现是否位于 `service.impl` 并以 `Impl` 结尾。
- 是否正确判断了复杂领域用例与简单 CRUD。
- 是否按业务复杂度选择 MyBatis-Plus、MyBatis XML 或混合模式，且没有重复实现同一查询。
- Domain 模型是否按公开契约、稳定复用或 Mapper 专用职责选择了合适粒度，且没有让内部模型穿透 Controller。
- 新模型是否一个公开顶层类型一个文件、名称语义明确且未使用 `Entity` 后缀或新增模型容器。
- XML 类型名和映射测试是否随模型变更同步更新。
- 是否保留显式资源可见性，并只按需叠加 `@DataPermission`。
- 多表写事务、幂等、行版本和审计是否完整。
- 写接口是否正确使用 `@Log`，内容型接口是否关闭或排除了敏感请求响应数据。
- 异步任务是否只有必要的结构化生命周期日志，且没有逐条数据、循环或 Mapper 级 `info` 刷屏。
- 异常日志是否带业务主键和 `traceId`、没有重复堆栈，敏感内容是否已禁止或脱敏。
- XML Mapper、record 或手工 DTO 是否因真实复杂度而保留，而非无理由偏离通用能力。
- 是否避免新增 `service.internal`、`service.support` 或无必要的辅助接口。
