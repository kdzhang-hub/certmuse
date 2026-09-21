# 知识点树 Markdown 转 JSONL 工具说明

> 适用模板：`knowledge_point/1.0`  
> 输入基线：[知识点树 V7](知识点树.md)
> 输出规范：[知识点树 JSONL 导入规范 V1](知识点树JSONL导入规范.md)

## 1. 用途

`tools/knowledge_tree_md_to_jsonl.py` 将 Unicode 树形 Markdown 转换为知识点树导入所需的 JSONL。工具只负责确定性格式转换和源文件结构校验，不调用上传接口，也不写入数据库。

父子关系依据 Markdown 的真实树形缩进栈计算，不通过删除编号最后一段推测。考试科目节点只用于确定 `subject_no`，不会生成知识点记录。

## 2. 运行要求

- Python 3.10 或更高版本；
- 仅使用 Python 标准库；
- 输入文件使用 UTF-8，可兼容读取 UTF-8 BOM；
- 输出文件使用 UTF-8、无 BOM、LF 换行和单行紧凑 JSON。

从仓库根目录执行：

```powershell
python tools/knowledge_tree_md_to_jsonl.py `
  "docs/imports/知识点树.md" `
  "target/knowledge-point-v7.jsonl"
```

命令成功时输出记录总数和三个科目的分布；失败时返回非零退出码，并在标准错误中显示原因。

## 3. 转换结果

对当前 V7 源文件执行的离线核对结果为：

| 项目 | 结果 |
| --- | --- |
| 总记录数 | 1001 |
| 科目 1 | 742 |
| 科目 2 | 218 |
| 科目 3 | 41 |
| 兄弟节点分组 | 330 |
| 最大 JSONL 单行 | 678 UTF-8 字节 |

逐条核对确认 Markdown 与生成 JSONL 的科目、编号、标题、父节点、层级和同级排序一致；`source_key` 和 `subject_no + syllabus_number` 均唯一。该结论属于离线转换核对，不替代上传与预检接口的集成测试。

## 4. 工具校验

工具在写出前检查：

- 必须存在且只能存在一个 `text` 代码块；
- 除文档根标题外，每个内容行必须是合法 Unicode 树节点；
- 知识点必须位于考试科目节点之下；
- 编号首段必须与当前考试科目一致；
- 缩进不能跳级；
- 同一科目内编号不能重复；
- 标题不能为空且不能超过规范长度；
- 三个考试科目必须都有知识点。

输出采用临时文件加替换方式。转换失败时不会用不完整内容覆盖目标文件。

## 5. 版本控制约定

- 转换脚本属于源代码，应纳入版本控制。
- `target/knowledge-point-v7.jsonl` 属于可重复生成产物，默认由 `target/.gitignore` 忽略。
- 不在仓库根目录保存生成的 JSONL 副本。
- 如果未来需要把完整 JSONL 固定为测试基线，应放入 `docs/testing/knowledge-point-import/fixtures/`，同时记录来源版本、生成工具版本和 SHA-256。

## 6. 限制

- 工具只接受当前文档约定的 Unicode 树格式，不解析普通 Markdown 列表或标题层级。
- 工具不会推断重要度、诊断开关或推荐开关，这些字段严格使用规范默认值。
- 工具不会验证数据库科目映射、权限、MinIO、幂等或正式知识点零写入；这些属于 D1 集成测试范围。
