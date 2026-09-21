# 知识点树 JSONL 导入规范 V1

> 模板标识：`knowledge_point/1.0`
> 来源数据：`知识点树.md`
> 适用范围：知识点树上传、预检、问题定位和后续确认入库
> 当前能力：上传与预检不写入正式知识点表

## 1. 目的和范围

本规范定义 `知识点树.md` 转换为 JSONL 后的行结构、字段含义、校验规则和数据库映射。后端、前端和测试必须使用同一版本，不得自行增加字段、解释枚举或推导业务默认值。

源文件包含三个考试科目和 1001 个编号知识点。`考试科目1`、`考试科目2`、`考试科目3` 只用于确定 `subject_no`，不转换为知识点记录。科目下的首级知识点，例如 `1.1`、`2.1`、`3.1`，其父知识点为空。

源文件名使用 V7，正文标题仍写 V6。本次按已确认决策，将来源版本冻结为 `knowledge-tree-v7`。该差异记入技术决策清单，不改变本规范的字段和转换结果。

## 2. 文件格式

- 文件名建议使用 `knowledge-point-v7.jsonl`。
- 字符编码使用 UTF-8，不带 BOM。
- 每行必须是一个完整 JSON 对象，每行表示一个知识点。
- 文件不能使用 JSON 数组包裹全部记录。
- 单个对象不能跨行格式化。
- 字段名区分大小写。
- `knowledge_point/1.0` 使用严格字段白名单，未知字段按错误处理。
- 每个物理行去除行终止符后的UTF-8字节数不得超过1MiB，即1,048,576字节；`LF`、`CRLF`和文件末尾不存在的行终止符均不计入。UTF-8 BOM按文件级编码错误处理，超限行不进入JSON解析，返回`KP_JSON_LINE_TOO_LARGE`。
- 每行根节点必须是JSON对象；数组、字符串、数字、布尔值、`null`及纯空白行均按`KP_JSON_INVALID`处理。

## 3. 行结构

每行固定包含以下 13 个字段：

```json
{
  "schema_version": "1.0",
  "source_key": "knowledge-tree-v7:1:1.1",
  "subject_no": 1,
  "syllabus_number": "1.1",
  "syllabus_title": "计算机系统基本知识",
  "parent_syllabus_number": null,
  "tree_depth": 1,
  "sort_order": 1,
  "description": null,
  "importance": null,
  "diagnostic_enabled": false,
  "recommendation_enabled": false,
  "status": "0"
}
```

| 字段 | JSON 类型 | 必填 | 可空 | 来源或默认值 | 数据库落点 |
| --- | --- | --- | --- | --- | --- |
| `schema_version` | string | 是 | 否 | 固定为 `"1.0"` | 仅用于模板校验 |
| `source_key` | string | 是 | 否 | 按来源版本、科目和编号生成 | `cm_import_record.source_key` |
| `subject_no` | integer | 是 | 否 | 由考试科目标题确定 | 显式映射为 `exam_subject_id` |
| `syllabus_number` | string | 是 | 否 | Markdown 节点编号 | `cm_knowledge_point.syllabus_number` |
| `syllabus_title` | string | 是 | 否 | Markdown 节点标题 | `cm_knowledge_point.syllabus_title` |
| `parent_syllabus_number` | string 或 null | 是 | 是 | 由 Markdown 缩进关系确定 | 解析为 `parent_id` |
| `tree_depth` | integer | 是 | 否 | 根据父子关系计算 | `cm_knowledge_point.tree_depth` |
| `sort_order` | integer | 是 | 否 | 同级节点的原始出现顺序 | `cm_knowledge_point.sort_order` |
| `description` | string 或 null | 是 | 是 | 当前统一为 `null` | `cm_knowledge_point.description` |
| `importance` | integer 或 null | 是 | 是 | 当前统一为 `null` | `cm_knowledge_point.importance` |
| `diagnostic_enabled` | boolean | 是 | 否 | 当前统一为 `false` | 同名字段 |
| `recommendation_enabled` | boolean | 是 | 否 | 当前统一为 `false` | 同名字段 |
| `status` | string | 是 | 否 | 当前统一为 `"0"` | 同名字段 |

## 4. 字段说明

### 4.1 `schema_version`

`schema_version` 表示当前行使用的 JSONL 结构版本。V1 模板固定写字符串 `"1.0"`，不能写为数字 `1.0`。

后端根据该字段选择校验规则。不支持的版本返回 `KP_SCHEMA_VERSION_UNSUPPORTED`。该字段不写入 `cm_knowledge_point`。

### 4.2 `source_key`

`source_key` 是知识点在来源数据中的稳定标识，用于重复检查、问题定位和后续重试。

生成格式固定为：

```text
knowledge-tree-v7:{subject_no}:{syllabus_number}
```

例如：

```json
"source_key": "knowledge-tree-v7:1:1.1.1"
```

长度限制为 1 至 200 个字符，同一文件内不得重复。知识点改名时，`source_key` 保持不变。

预检不自动修剪或规范化`source_key`。前缀、科目、编号、长度或首尾空白不符合本节规则时，产生`KP_SOURCE_KEY_INVALID`。

### 4.3 `subject_no`

`subject_no` 是来源文件中的考试科目编号，不是数据库主键。

| `subject_no` | 科目名称 |
| --- | --- |
| `1` | 系统架构设计综合知识 |
| `2` | 系统架构设计案例分析 |
| `3` | 系统架构设计论文 |

上传批次必须显式提交 `subject_no -> cm_exam_subject.id` 映射。服务端不能根据科目名称猜测，也不能在映射缺失时自动创建科目。

### 4.4 `syllabus_number`

`syllabus_number` 是知识点在考试大纲中的完整编号，例如 `1.1`、`1.1.1`、`2.9.3.2`。

校验规则如下：

- 长度为 1 至 100 个字符。
- 格式为数字分段，使用正则表达式 `^[1-3](\.[1-9][0-9]*)+$`。
- 第一段必须等于 `subject_no`。
- 同一 `subject_no` 内不得重复。
- 编号不能补零，例如使用 `1.1`，不使用 `01.01`。

预检不自动修剪`syllabus_number`。存在首尾空白时按`KP_SYLLABUS_NUMBER_INVALID`处理。

题目知识点映射文件中的 `code` 与本字段含义相同。关联时使用 `subject_no + syllabus_number` 定位唯一知识点。

### 4.5 `syllabus_title`

`syllabus_title` 是知识点名称。转换程序删除 Markdown 树形符号、编号和首尾空白，保留标题中的中文、英文、标点、括号和专业术语。

该字段去除首尾空白后长度为 1 至 500 个字符，不能是空字符串。

预检以去除首尾空白后的值校验标题。合法 JSON 的`raw_record`保留上传对象；后续正式入库使用去除首尾空白后的标题。

例如，源文本：

```text
│  │  ├─ 1.1.1 计算机系统概述
```

转换结果：

```json
"syllabus_title": "计算机系统概述"
```

### 4.6 `parent_syllabus_number`

`parent_syllabus_number` 是当前知识点的直接父知识点编号。转换程序必须依据 Markdown 的真实缩进关系确定父节点，不能只删除编号的最后一段来猜测。

| 当前编号 | 父编号 |
| --- | --- |
| `1.1` | `null` |
| `1.1.1` | `1.1` |
| `1.1.1.1` | `1.1.1` |
| `2.1` | `null` |

父节点必须满足以下条件：

- 与当前节点属于同一 `subject_no`。
- 已在当前文件中定义。
- 不能是当前节点自身或当前节点的后代。
- 转换结果中父节点必须排在子节点之前。

后续正式入库时，服务端使用该字段解析 `cm_knowledge_point.parent_id`。

预检不自动修剪非空的`parent_syllabus_number`。存在首尾空白时按`KP_SYLLABUS_NUMBER_INVALID`处理。

预检中，只有编号、科目、父引用、层级和循环检查均通过的父记录才可作为有效父节点。父记录只有标题、描述、重要度、开关或状态值错误时，不影响子节点引用该父节点的预检判断。

后续正式导入采用整批原子写入：只要批次存在任一error，批次不得确认导入，`cm_knowledge_point`不写入任何记录。因此，父记录存在非结构字段错误时，子记录可以在预检中保持结构有效，但不能绕过该父记录单独入库；预检不执行该正式导入。

### 4.7 `tree_depth`

`tree_depth` 表示知识点在当前考试科目知识树中的层级。考试科目本身不计入知识点层级。

| 编号示例 | `tree_depth` |
| --- | --- |
| `1.1` | `1` |
| `1.1.1` | `2` |
| `1.1.1.1` | `3` |
| `1.1.1.1.1` | `4` |

转换程序根据 Markdown 缩进栈计算层级。预检服务还要按照父节点重新计算并比对，防止文件中的层级值与父子关系不一致。

`tree_depth`必须是 PostgreSQL `smallint` 可表示的正整数，即 1 至 32,767。非根节点必须等于有效父节点的`tree_depth + 1`；根节点必须为 1。

### 4.8 `sort_order`

`sort_order` 表示当前节点在同一父节点下的显示顺序。它不表示知识点的重要程度。

例如：

```text
1.1.1 计算机系统概述
1.1.2 计算机硬件
1.1.3 计算机软件
```

转换结果：

| `syllabus_number` | `parent_syllabus_number` | `sort_order` |
| --- | --- | --- |
| `1.1.1` | `1.1` | `1` |
| `1.1.2` | `1.1` | `2` |
| `1.1.3` | `1.1` | `3` |

同一父节点下从 1 开始连续编号。进入另一个父节点后重新从 1 开始。同一 `subject_no + parent_syllabus_number + sort_order` 组合不得重复。

预检以`subject_no + parent_syllabus_number`划分兄弟组，根节点在同一科目内构成一个兄弟组。兄弟组有`n`条排序字段有效的记录时，`sort_order`的期望集合为`1..n`。出现重复、缺号或超出该集合的值时，该组全部排序字段有效的记录产生`KP_SORT_ORDER_INVALID`；排序字段本身无效的记录不叠加排序推断错误。

`sort_order`必须是 PostgreSQL `integer` 可表示的正整数，即 1 至 2,147,483,647。

### 4.9 `description`

`description` 是知识点的补充说明，不是知识点名称的副本。

当前 Markdown 每个节点只有一段可识别标题，没有独立且可靠的说明字段，因此转换时统一写 `null`。不能将 `syllabus_title` 重复写入 `description`，也不能使用空字符串代替 `null`。

后续内容人员可以在管理端补充该字段。

### 4.10 `importance`

`importance` 表示知识点的考试重要程度。

| 值 | 含义 |
| --- | --- |
| `1` | 补充知识点 |
| `2` | 常规知识点 |
| `3` | 核心知识点 |
| `null` | 尚未配置 |

源文件没有重要度信息，转换程序不能根据树层级、标题或题目数量推断，因此统一写 `null`。

### 4.11 `diagnostic_enabled`

`diagnostic_enabled` 表示知识点是否允许进入诊断测评的选题范围。

- `true`：诊断服务可以围绕该知识点选题。
- `false`：该知识点暂不参与诊断选题。

源文件没有诊断配置，转换时统一写布尔值 `false`。不能写成字符串 `"false"`，也不能写成数字 `0`。

### 4.12 `recommendation_enabled`

`recommendation_enabled` 表示知识点是否允许进入学习任务和内容推荐。

- `true`：推荐服务可以使用该知识点。
- `false`：该知识点暂不参与推荐。

该字段与 `diagnostic_enabled` 相互独立。源文件没有推荐配置，转换时统一写布尔值 `false`。

### 4.13 `status`

`status` 表示知识点是否启用，使用 RuoYi 状态编码。

| 值 | 含义 |
| --- | --- |
| `"0"` | 启用 |
| `"1"` | 停用 |

该字段必须使用字符串。源文件中的节点首版统一写 `"0"`。

`status = "0"` 只表示知识点可用于当前业务，不表示它自动进入诊断或推荐。诊断和推荐还要分别检查对应开关以及 `del_flag = "0"`。

## 5. 不进入 JSONL 的字段

下列字段由服务端根据批次上下文、父子关系和当前用户生成，不能由上传文件提供：

| 字段 | 生成方式 |
| --- | --- |
| `id` | 应用侧 MyBatis-Plus 雪花策略生成 |
| `syllabus_version_id` | 来自上传时选择的考纲版本 |
| `exam_subject_id` | 由 `subject_no` 显式映射得到 |
| `parent_id` | 由 `parent_syllabus_number` 解析得到 |
| `del_flag` | 服务端初始化为 `"0"` |
| `create_dept` | 取当前用户创建时部门 |
| `create_by` | 取当前用户 ID |
| `create_time` | 取服务端时间 |
| `update_by` | 由服务端维护 |
| `update_time` | 由服务端维护 |

上传文件出现上述字段时，按未知字段处理，不能覆盖服务端值。

## 6. 转换示例

源 Markdown：

```text
├─ 考试科目1 系统架构设计综合知识
│  ├─ 1.1 计算机系统基本知识
│  │  ├─ 1.1.1 计算机系统概述
│  │  │  └─ 1.1.1.1 计算机系统的定义、组成和分类
```

转换后的 JSONL：

```jsonl
{"schema_version":"1.0","source_key":"knowledge-tree-v7:1:1.1","subject_no":1,"syllabus_number":"1.1","syllabus_title":"计算机系统基本知识","parent_syllabus_number":null,"tree_depth":1,"sort_order":1,"description":null,"importance":null,"diagnostic_enabled":false,"recommendation_enabled":false,"status":"0"}
{"schema_version":"1.0","source_key":"knowledge-tree-v7:1:1.1.1","subject_no":1,"syllabus_number":"1.1.1","syllabus_title":"计算机系统概述","parent_syllabus_number":"1.1","tree_depth":2,"sort_order":1,"description":null,"importance":null,"diagnostic_enabled":false,"recommendation_enabled":false,"status":"0"}
{"schema_version":"1.0","source_key":"knowledge-tree-v7:1:1.1.1.1","subject_no":1,"syllabus_number":"1.1.1.1","syllabus_title":"计算机系统的定义、组成和分类","parent_syllabus_number":"1.1.1","tree_depth":3,"sort_order":1,"description":null,"importance":null,"diagnostic_enabled":false,"recommendation_enabled":false,"status":"0"}
```

## 7. 转换规则

转换程序按以下顺序处理源文件：

1. 读取 Markdown 中的 `text` 代码块。
2. 遇到 `考试科目1`、`考试科目2`、`考试科目3` 时切换当前 `subject_no`，不生成知识点记录。
3. 提取编号节点的 `syllabus_number` 和 `syllabus_title`。
4. 使用 Unicode 树形缩进栈计算 `parent_syllabus_number` 和 `tree_depth`。
5. 按同一父节点下的原始出现顺序生成 `sort_order`。
6. 根据来源版本、科目和编号生成 `source_key`。
7. 写入固定默认值：`description = null`、`importance = null`、`diagnostic_enabled = false`、`recommendation_enabled = false`、`status = "0"`。
8. 完成全文件校验后再输出最终 JSONL。

转换程序不能根据编号截断、标题关键词或题目数量推断父子关系和学习配置。

## 8. 校验规则和问题码

| 校验场景 | 问题码 | 级别 |
| --- | --- | --- |
| JSON行无法解析、根节点不是对象或纯空白行 | `KP_JSON_INVALID` | error |
| 单个 JSON 对象出现重复字段名 | `KP_JSON_DUPLICATE_KEY` | error |
| 单行UTF-8字节数超过1MiB | `KP_JSON_LINE_TOO_LARGE` | error |
| Schema 版本不支持 | `KP_SCHEMA_VERSION_UNSUPPORTED` | error |
| 缺少必填字段 | `KP_REQUIRED_FIELD_MISSING` | error |
| 字段类型错误 | `KP_FIELD_TYPE_INVALID` | error |
| 字段类型正确但值、长度或枚举非法 | `KP_FIELD_VALUE_INVALID` | error |
| 出现未知字段 | `KP_UNKNOWN_FIELD` | error |
| `subject_no` 不在 1 至 3 | `KP_SUBJECT_NO_INVALID` | error |
| 科目没有显式映射 | `KP_SUBJECT_MAPPING_UNKNOWN` | error |
| 编号格式错误 | `KP_SYLLABUS_NUMBER_INVALID` | error |
| 编号首段与科目不一致 | `KP_SUBJECT_NUMBER_MISMATCH` | error |
| 来源键格式、长度、前缀、科目、编号或首尾空白不符合规则 | `KP_SOURCE_KEY_INVALID` | error |
| 来源键重复 | `KP_DUPLICATE_SOURCE_KEY` | error |
| 同科目编号重复 | `KP_DUPLICATE_SYLLABUS_NUMBER` | error |
| 父节点不存在 | `KP_PARENT_NOT_FOUND` | error |
| 父子节点跨科目 | `KP_PARENT_SUBJECT_MISMATCH` | error |
| 父节点存在但晚于子节点 | `KP_PARENT_ORDER_INVALID` | error |
| 父节点存在但不满足结构性有效条件 | `KP_PARENT_INVALID` | error |
| 层级与父节点不一致 | `KP_TREE_DEPTH_INVALID` | error |
| 出现自引用或循环 | `KP_TREE_CYCLE` | error |
| 同级排序值重复或不连续 | `KP_SORT_ORDER_INVALID` | error |
| 来源文件名与正文版本不一致 | `KP_SOURCE_VERSION_MISMATCH` | 仅 Markdown 转 JSONL 转换报告使用，不进入上传预检或`warningCount` |

全文件校验顺序固定如下，前一步无法确定的输入不产生后续推测性问题：

1. 按物理行检查行大小和UTF-8；
2. 拒绝重复字段名后解析JSON，并检查根节点；
3. 检查字段白名单、必填项、类型和单字段取值；
4. 检查科目映射、`source_key`和`subject_no + syllabus_number`的文件内唯一性；
5. 检查父节点存在性、科目、顺序、层级和循环；
6. 按有效兄弟组检查`sort_order`；
7. 汇总问题、记录状态和统计。

存在 error 的记录计入 `failedCount`。warning 按问题条数计入 `warningCount`，但不将记录从 `validCount` 中排除。

一条记录可以产生多个相互独立的问题；依赖无效字段的检查必须跳过，不产生推测性问题。同一`source_key`或同一`subject_no + syllabus_number`的全部冲突记录均产生对应重复问题。问题去重由`batchId + recordId + issueCode + fieldPath`确定；非法 JSON 行使用`batchId + issueCode + fieldPath`。

## 9. 与题目知识点映射的关系

现有 `题目和知识点映射.jsonl` 使用以下结构：

```json
{"question_key":"00001","knowledge_points":[{"subject_no":2,"code":"2.9.1.1"}],"tag_status":"needs_review"}
```

其中 `knowledge_points[].code` 与本规范的 `syllabus_number` 含义相同。关联时按以下组合查询：

```text
subject_no + code
-> exam_subject_id + syllabus_number
-> cm_knowledge_point.id
```

不能只使用 `code` 查询，也不能根据知识点标题匹配。

## 10. 兼容策略

- `1.0` 使用严格字段白名单。
- 新增非必填字段时发布 `1.1`，服务端继续支持 `1.0`。
- 删除字段、修改字段类型、改变枚举或改变字段含义时升级为 `2.0`。
- `source_key` 和 `syllabus_number` 在同一来源版本内保持稳定，知识点改名不能改变这两个字段。
- 新模板发布后，旧批次继续使用创建时冻结的模板版本和解析配置。

## 11. 验收标准

转换结果必须满足以下条件：

1. JSONL 恰好包含 1001 行知识点记录。
2. 三个考试科目均有记录，记录总数之和为 1001。
3. 每行都能独立解析，并且只包含本规范定义的 13 个字段。
4. `source_key` 以及 `subject_no + syllabus_number` 在文件内唯一。
5. `1.1`、`2.1`、`3.1` 等科目首级节点的 `parent_syllabus_number` 为 `null`，`tree_depth` 为 1。
6. 所有非根节点均能找到同科目的唯一父节点，且不存在循环。
7. 同一父节点下的 `sort_order` 从 1 开始连续递增。
8. 题目知识点映射中的 `subject_no + code` 均执行命中检查，未命中项进入校验报告。
9. 转换程序重复运行时生成字节一致的 JSONL。
10. 预检前后 `cm_knowledge_point` 的数量和内容保持不变。

## 12. 预检同步记录

2026-07-31，预检实现和测试已与本规范同步。该记录只确认 V1 规则已落地，不改变 13 个字段、错误码、校验顺序或后续版本升级规则。
