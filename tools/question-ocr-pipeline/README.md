# 试卷 OCR 转题目 ZIP

一个脱离 Codex 运行的本地 Web 工具。上传系统架构设计师真题解析 PDF 后，工具会调用 PaddleOCR-VL、拆分题目、恢复配图、匹配知识点，并生成 `question-zip/1.0`。

## 启动

Windows 双击 `start.bat`，或执行：

```powershell
python -m pip install -r requirements.txt
python app.py
```

浏览器访问 `http://127.0.0.1:8765`。

## 输入与输出

输入：

- 含综合知识、案例分析、论文写作及答案解析的 PDF；
- PaddleOCR-VL `/layout-parsing` 地址；
- 可选的知识点树 JSONL。留空时使用 `data/knowledge-points.jsonl`。

输出：

- `question-zip/1.0` 导入包；
- Markdown 复核报告；
- 完整知识点映射 JSON；
- 分页 OCR Markdown ZIP。

任务完成后，这四项产物会自动复制到：

```text
桌面/试卷OCR产物/<PDF名称>-<完成时间>/
```

每次任务使用独立时间戳目录，不覆盖以前的结果；Web 页面同时保留下载链接。

## 当前版式约束

- 综合知识固定解析为 75 道 A～D 单选题，支持 `14-15`、`71–75` 之类的共用题干；
- 案例分析识别“试题一”至“试题五”；
- 论文识别“论文写作”下的四个论题；
- 原资料必须包含“答案：”，案例题必须包含答案段；
- 当前保留了 2022.11 来源第 24 题 OCR 漏答案的兼容修复。处理其他年份时，如果第 24 题确实没有答案，需先检查 OCR Markdown。

这是可复用的流程框架，但不同出版方的标题、题号和答案版式可能需要增加解析 Profile。知识点语义匹配和 OCR 低置信内容仍应人工复核。
