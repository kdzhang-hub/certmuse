# 题库 JSONL 导入规范 V1

> 2026-08-18 变更：题库不再维护人工评分点，CASE/ESSAY 仅要求题干、参考答案和题目级知识点。正式格式不得携带 `scoring_points`；过渡期空数组兼容通过，非空值返回 `QUESTION_SCORING_POINTS_UNSUPPORTED`。本文后续评分点章节仅作为已废止格式的历史记录。

> 题目模板：`question/1.0`  
> 知识点映射模板：`question_knowledge/1.0`  
> 评分点模板：`question_scoring_point/1.0`  
> 契约状态：frozen  
> 冻结日期：2026-07-31

## 1. 目的

本规范固定题目 JSONL、答案值、题目知识点映射规则和评分点 JSONL 格式。上传端、预检服务、正式导入、管理端和测试必须使用相同字段与含义。源记录不就地改写，格式归一和数据库字段转换由导入服务完成。

`frozen` 表示 V1 字段、值和转换规则不能原地修改，不表示每条题目都已具备发布或计分条件。当前基线中的空答案按空答案冻结，缺少的评分点按“未提供”冻结；以后补充时创建新修订或新版本，不能回写 V1 原始记录。

一次题库导入使用以下文件：

```text
question-bank-v1.zip
├─ questions.jsonl
├─ question-knowledge.jsonl
└─ images/
```

后续补充主观题评分点时单独上传 `question-scoring-points.jsonl`。

当前冻结基线不包含 `question-scoring-points.jsonl`。本规范冻结评分点格式和落库规则，实际评分点数据条数为 0。没有评分点的主观题不得进入正式计分、作答结算或人物画像。

本仓库只保存本规范、文本清单和数据库约束。题目数据与图片资产不纳入仓库，也不属于本次提交内容。

## 2. 通用文件规则

- JSONL 使用 UTF-8 编码，不带 BOM。
- 每个物理行只能包含一个完整 JSON 对象，不能使用 JSON 数组包裹全部记录。
- 空行、重复字段名、未知字段和非对象根节点均为错误。
- 字段名区分大小写。
- 单行去除换行符后的 UTF-8 字节数不得超过 1 MiB。
- 完整上传包不得超过 100 MiB。该限制属于上传和预检规则，不修改数据库现有的 10 MiB 单批次源文件上限。
- ZIP 根目录只允许 `questions.jsonl`、`question-knowledge.jsonl` 和 `images/`，不能再套一层目录。
- `questions.jsonl` 与 `question-knowledge.jsonl` 必须使用相同的 5 位题目键，并且一一对应。
- 文件解析采用严格字段白名单。V1 不接受上传端自定义扩展字段。

## 3. `questions.jsonl`

每行固定包含 9 个字段：

```json
{
  "qid": "00001",
  "source": "https://example.com/question/1",
  "images": [],
  "type": "single",
  "question": "题干",
  "options": {"A": "A. 选项一", "B": "B. 选项二"},
  "answer": "A",
  "analysis": "解析",
  "subject": "系统架构"
}
```

| 字段 | JSON 类型 | 必填 | 可空 | 规则 |
| --- | --- | --- | --- | --- |
| `qid` | string | 是 | 否 | 正则 `^[0-9]{5}$`，文件内唯一 |
| `source` | string | 是 | 否 | HTTPS URL 或固定值 `rengong` |
| `images` | string array | 是 | 数组可空 | 相对路径必须以 `images/` 开头，禁止 `..` |
| `type` | string | 是 | 否 | 仅允许 `single`、`subjective` |
| `question` | string | 是 | 否 | 去除首尾空白后不能为空 |
| `options` | object | 是 | 对象可空 | 单选题至少两个选项，主观题必须为空对象 |
| `answer` | string 或 null | 是 | 是 | 单选题必须是已有选项键，主观题可为字符串或 `null` |
| `analysis` | string | 是 | 字符串可空 | 原样保存 |
| `subject` | string | 是 | 否 | V1 固定为 `系统架构`，不能据此推断考试科目 |

### 3.1 题型转换

| 来源值 | 数据库值 | 转换条件 |
| --- | --- | --- |
| `single` | `CHOICE` | 固定转换 |
| `subjective` | `ESSAY` | 题干以“论”开头，或题干包含“请围绕” |
| `subjective` | `CASE` | 不满足论文题条件 |

题型转换只决定数据库枚举和页面呈现。本批次不根据题干自动生成主观题分值或评分点。

### 3.2 选项转换

`options` 的键写入 `cm_question_option.option_label`。值写入 `option_text` 前，可删除与键相同的单个开头标记，例如 `A.`、`A．`或`A、`。不满足该形式时保留原值。排序按选项键的原始顺序生成，从 1 开始。

### 3.3 答案转换

单选题答案写入 `cm_question_revision.answer`：

```json
{"schema_version":"1.0","answer_type":"option_keys","value":["A"]}
```

主观题非空答案写为：

```json
{"schema_version":"1.0","answer_type":"text","value":"参考答案"}
```

主观题的 `answer` 为 `null` 或空字符串时，数据库均写为 `NULL`。导入服务不能根据解析生成答案，也不能把空答案改写为占位文字。

主观题空答案是合法来源值，不额外生成草稿。当前 4,658 条来源题中有 80 道主观题的答案为 `null`，没有空字符串答案。拆分后有 81 条题目记录保持空答案，其中 80 条可作为不计分内容发布，1 条因知识点不合规进入草稿。这些记录在补齐参考答案和评分点前不得进入正式计分、作答结算或人物画像。

### 3.4 来源转换

HTTPS 地址写入 `source_locator`，域名写入 `source_name`，`source_type` 使用 `external_web`。固定值 `rengong` 表示人工补录，写入 `source_type = 'manual'`，`source_locator = NULL`。人工补录题不能生成虚假 URL。

## 4. `question-knowledge.jsonl`

每行固定包含 3 个字段：

```json
{
  "question_key": "00001",
  "knowledge_points": [{"subject_no": 2, "code": "2.9.1.1"}],
  "tag_status": "needs_review"
}
```

| 字段 | JSON 类型 | 必填 | 可空 | 规则 |
| --- | --- | --- | --- | --- |
| `question_key` | string | 是 | 否 | 与 `questions.jsonl.qid` 一一对应 |
| `knowledge_points` | object array | 是 | 数组可空 | 同题内 `subject_no + code` 唯一 |
| `tag_status` | string | 是 | 否 | `reviewed`、`needs_review`、`unclassified` |

`knowledge_points` 元素只允许以下字段：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `subject_no` | integer | 仅允许 1、2、3 |
| `code` | string | 知识点完整编号，第一段必须等于 `subject_no` |

知识点使用 `subject_no + code` 查找当前导入上下文中的 `cm_knowledge_point`，不能按标题匹配。只有未删除、启用的叶子知识点可以写入 `cm_question_knowledge`。

### 4.1 映射状态

| 来源状态 | 数据库状态 | 行为 |
| --- | --- | --- |
| `reviewed` | `unclassified` | 保留映射和原始状态；本科目只有一个有效知识点时可标记为 `primary`，但仍须经过平台确认后才能写为 `reviewed` |
| `needs_review` | `unclassified` | 保留映射和原始状态，必须经过后续复核 |
| `unclassified` | `unclassified` | `knowledge_points` 必须为空，题目只进入草稿区 |

`tag_status` 只表示来源数据的人工复核状态，不能替代平台对主次关系、知识点存在性、启用状态和叶子属性的校验。新建映射的数据库状态统一从 `unclassified` 开始。

### 4.2 多科目题目

数据库中的一道题只属于一个考试科目。映射涉及多个 `subject_no` 时，导入服务按科目生成多条题目记录，每条记录只保留本科目的知识点。不同科目的副本使用不同的 `evidence_group_key`，同一科目内的相同来源题使用相同键，正式组卷不得在同一场次重复抽取同一科目的相同来源题。

稳定标识固定如下：

```text
source_key = question-bank-v1:{qid}:{subject_no}
evidence_group_key = question-bank-v1:{qid}:{subject_no}
question_code = 应用生成的 Q + 19 位十进制雪花值
```

`source_key` 用于导入幂等和来源冲突检查，不能写入 `question_code`。`question_code` 由应用独立生成，在 `Q` 后恰好包含 19 位数字，唯一冲突时重新生成。无知识点题按题型确定科目：`CHOICE` 使用科目 1，`CASE` 使用科目 2，`ESSAY` 使用科目 3。当前 10 道无知识点题均为 `CHOICE`，因此进入科目 1 草稿，不使用虚假科目或空科目。

### 4.3 无知识点草稿

`knowledge_points=[]` 的题目仍写入 `cm_question` 和修订表，但满足以下限制：

- `cm_question.exam_subject_id` 按题型规则填写；
- 修订状态只能是 `draft`；
- 不写入 `cm_question_knowledge`；
- 管理端显示在草稿区；
- 学员查询、组卷、评分和画像均不得读取；
- 管理员补齐至少一个同科目的叶子知识点并完成映射确认后，才能提交审核。

### 4.4 不合规知识点草稿

按科目拆分后，只要某个科目分组包含未找到、停用、已删除或非叶子知识点，该科目的题目副本就整体进入草稿区。导入服务必须保留原始映射和问题记录，但不能把该分组中的任何知识点写入 `cm_question_knowledge`，也不能静默丢弃不合规映射后发布剩余关系。

这类草稿保留已经确定的 `exam_subject_id`，修订状态固定为 `draft`，`knowledge_mapping_status` 写为 `unclassified`。管理员改为至少一个同科目的有效叶子知识点并完成映射确认后，才能提交审核。

## 5. `question-scoring-points.jsonl`

评分点文件用于后续补齐主观题评分规则，不修改已经冻结的题目文件。每行结构如下：

```json
{
  "schema_version": "1.0",
  "source_key": "question-bank-v1:00212:2",
  "question_max_score": 10.00,
  "scoring_points": [
    {
      "scoring_code": "SP1",
      "description": "说明表现层及其职责",
      "max_score": 3.00,
      "sort_order": 1,
      "knowledge_points": [{"subject_no": 2, "code": "2.3.1"}]
    }
  ]
}
```

| 字段 | 规则 |
| --- | --- |
| `schema_version` | 固定为字符串 `1.0` |
| `source_key` | 必须命中唯一题目记录，且题型为 `CASE` 或 `ESSAY` |
| `question_max_score` | 大于 0，最多两位小数；只用于校验评分点合计 |
| `scoring_points` | 非空数组，编码和排序在题内唯一 |

`scoring_code` 长度不得超过 50，只能使用大写字母、数字和下划线，并以大写字母开头。每个评分点必须有非空说明、正数分值、从 1 连续递增的排序和至少一个叶子知识点。全部 `max_score` 之和必须等于 `question_max_score`。

评分点知识点沿用现有数据库规则：可以跨考试科目，但必须属于本批次 `syllabus_version_id` 对应的考纲版本，并且是启用、未删除的叶子知识点。`question_max_score` 不新增数据库字段，数据库中的题目总分按 `cm_question_scoring_point.max_score` 求和得到。

评分点导入创建新的题目修订，复制题干、答案、解析、选项、图片和题目级知识点，再写入 `cm_question_scoring_point` 与 `cm_scoring_point_knowledge`。新修订从草稿开始，审核发布后替换旧的已发布修订。

评分点补充沿用现有 `cm_import_batch.import_type = 'question'`，使用 `template_version = 'question_scoring_point/1.0'` 区分处理模式，并按目标题目的考试科目拆分批次。这样不需要增加数据库导入类型。当前没有实际评分点文件，本次只冻结文件格式和修订规则。

## 6. 版本和兼容规则

- V1 使用严格字段白名单。
- 新增可选字段时升级次版本，例如 `1.1`。
- 删除字段、修改类型、改变枚举或字段含义时升级主版本。
- 已创建批次始终使用创建时记录的 `template_version` 和 `parser_version`。
- 原始行保存到 `cm_import_record.raw_record`，行哈希保存到 `raw_hash`。后续版本不能覆盖旧批次原始记录。
- `raw_record` 使用 `question_import_record/1.0` 包装题目行和映射行，固定包含 `schema_version`、`question` 和 `knowledge_mapping`：

```json
{
  "schema_version": "question_import_record/1.0",
  "question": {},
  "knowledge_mapping": {}
}
```

- `result_data` 使用 `question_import_result/1.0`，固定包含 `schema_version`、`question_id`、`question_revision_id`、`subject_no` 和 `revision_status`。未生成目标记录时为 `null`。

## 7. 验收条件

1. 两个基础 JSONL 的题目键一一对应，均无重复。
2. 所有图片引用存在，图片目录不存在未引用文件。
3. 知识点合规题目的编号与科目一致，并命中当前考纲版本的有效叶子知识点；不合规分组完整进入隔离草稿。
4. 多科目拆分、两类草稿分流、答案转换和题型转换可重复执行并得到相同结果。
5. 80 道来源主观题的 `null` 答案保持不变，不能从解析推导答案。
6. 冻结清单重新计算后必须与清单值一致。
7. 当前评分点数据条数保持为 0；以后导入评分点时不能修改旧修订或已经发生的作答快照。
