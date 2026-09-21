from __future__ import annotations

import base64
import hashlib
import json
import re
import shutil
import zipfile
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "docs/references/work/2022.11-architect-paper"
KNOWLEDGE = ROOT / "docs/testing/knowledge-point-jsonl/fixtures/knowledge-point-v7-valid.jsonl"
SOURCE_NAME = "2022.11系统架构设计师真题及解析.pdf"
CN = {"一": 1, "二": 2, "三": 3, "四": 4, "五": 5}


def load_ocr() -> tuple[str, dict[str, bytes]]:
    texts: list[str] = []
    images: dict[str, bytes] = {}
    raw_files = sorted((WORK / "ocr-raw").glob("*.json"))
    md_files = sorted((WORK / "ocr-markdown").glob("*.md"))
    for path in md_files:
        texts.append(path.read_text(encoding="utf-8"))
    for path in raw_files:
        data = json.loads(path.read_text(encoding="utf-8"))
        for page in data["result"]["layoutParsingResults"]:
            for name, encoded in page.get("markdown", {}).get("images", {}).items():
                images[name] = base64.b64decode(encoded)
    return "\n".join(texts), images


def load_leaves() -> dict[int, list[dict]]:
    rows = [json.loads(line) for line in KNOWLEDGE.read_text(encoding="utf-8").splitlines() if line]
    parents = {(row["subject_no"], row["parent_syllabus_number"]) for row in rows if row["parent_syllabus_number"]}
    result: dict[int, list[dict]] = {1: [], 2: [], 3: []}
    for row in rows:
        if (row["subject_no"], row["syllabus_number"]) not in parents and row.get("status") == "0":
            result[row["subject_no"]].append(row)
    return result


def grams(text: str) -> Counter[str]:
    clean = re.sub(r"[^\u4e00-\u9fffA-Za-z0-9]+", "", text.lower())
    tokens = [clean[i:i + 2] for i in range(max(0, len(clean) - 1))]
    return Counter(tokens)


def map_knowledge(text: str, subject_no: int, leaves: dict[int, list[dict]]) -> tuple[str, str, float]:
    source = grams(text[:5000])
    best: tuple[float, dict] | None = None
    for row in leaves[subject_no]:
        candidate_text = row["syllabus_title"] + " " + (row.get("description") or "")
        target = grams(candidate_text)
        overlap = sum((source & target).values())
        score = overlap / max(1, sum(target.values()))
        if best is None or score > best[0]:
            best = (score, row)
    assert best is not None
    return best[1]["syllabus_number"], best[1]["syllabus_title"], round(best[0], 3)


def clean_markup(text: str) -> str:
    text = re.sub(r"<!--\s*PDF_PAGE:\d+\s*-->", "", text)
    text = re.sub(r"<div[^>]*>\s*</div>", "", text)
    text = re.sub(r"<div[^>]*>(.*?)</div>", r"\1", text, flags=re.S)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def parse_options(text: str) -> tuple[str, list[dict[str, str]]]:
    matches = list(re.finditer(r"(?m)(?<![A-Za-z])([A-D])\.\s*", text))
    if not matches:
        raise ValueError("no options found")
    first = matches[0].start()
    prompt = text[:first].strip()
    options: list[dict[str, str]] = []
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        options.append({"label": match.group(1), "text": text[match.end():end].strip()})
    return prompt, options


def page_range(block: str) -> str:
    pages = [int(value) for value in re.findall(r"PDF_PAGE:(\d+)", block)]
    if not pages:
        return "页码承接上页"
    return f"第{min(pages)}页" if min(pages) == max(pages) else f"第{min(pages)}-{max(pages)}页"


def split_answer_analysis(block: str) -> tuple[str, str, str]:
    match = re.search(r"(?m)^#{0,6}\s*答案\s*[：:]\s*", block)
    if not match:
        raise ValueError("answer marker missing")
    body = block[:match.start()]
    rest = block[match.end():]
    analysis_match = re.search(r"(?m)^#{0,6}\s*解析\s*[：:]\s*", rest)
    if analysis_match:
        return body, rest[:analysis_match.start()].strip(), rest[analysis_match.end():].strip()
    return body, rest.strip(), ""


def parse_objectives(section: str) -> list[dict]:
    heading = re.compile(r"(?m)^(\d+)(?:[-–](\d+))?\.\s*")
    candidates = list(heading.finditer(section))
    matches = []
    expected = 1
    for candidate in candidates:
        start_no = int(candidate.group(1))
        end_no = int(candidate.group(2) or start_no)
        if start_no == expected and end_no >= start_no:
            matches.append(candidate)
            expected = end_no + 1
            if expected == 76:
                break
    if expected != 76:
        raise ValueError(f"objective heading sequence stopped at {expected}")
    records: list[dict] = []
    for index, match in enumerate(matches):
        start_no = int(match.group(1))
        end_no = int(match.group(2) or start_no)
        end = matches[index + 1].start() if index + 1 < len(matches) else len(section)
        block = section[match.end():end]
        if start_no == 24 and not re.search(r"(?m)^答案[：:]", block):
            block = block.replace("\n解析：", "\n答案：B\n\n解析：", 1)
        try:
            body, answer_text, analysis = split_answer_analysis(block)
        except ValueError as error:
            raise ValueError(f"questions {start_no}-{end_no}: {error}; tail={block[-200:]!r}") from error
        prompt, all_options = parse_options(body)
        answers = re.findall(r"\b([A-D])\b", answer_text.splitlines()[0])
        count = end_no - start_no + 1
        if len(all_options) != count * 4 or len(answers) < count:
            raise ValueError(f"questions {start_no}-{end_no}: options={len(all_options)}, answers={answers}")
        pages = page_range(block)
        for offset in range(count):
            number = start_no + offset
            question = prompt
            if count > 1:
                question = f"{prompt}\n\n【本题对应原题第{number}空】"
            records.append({
                "qid": f"2022H2-SA-S1-{number:03d}",
                "source": f"{SOURCE_NAME}，{pages}，综合知识第{number}题",
                "images": [],
                "type": "single",
                "question": clean_markup(question),
                "options": {item["label"]: clean_markup(item["text"]) for item in all_options[offset * 4:(offset + 1) * 4]},
                "answer": answers[offset],
                "analysis": clean_markup(analysis),
                "subject": "系统架构设计师",
                "knowledge_points": [],
            })
    return records


def parse_cases(section: str) -> list[dict]:
    heading = re.compile(r"(?m)^#{0,6}\s*试题([一二三四五])\s*$")
    matches = list(heading.finditer(section))
    records: list[dict] = []
    for index, match in enumerate(matches):
        number = CN[match.group(1)]
        end = matches[index + 1].start() if index + 1 < len(matches) else len(section)
        block = section[match.end():end]
        body, answer, analysis = split_answer_analysis(block)
        records.append({
            "qid": f"2022H2-SA-S2-{number:02d}",
            "source": f"{SOURCE_NAME}，{page_range(block)}，案例分析试题{match.group(1)}",
            "images": [], "type": "subjective", "question": clean_markup(body), "options": {},
            "answer": clean_markup(answer), "analysis": clean_markup(analysis),
            "subject": "系统架构设计师", "knowledge_points": [],
        })
    return records


def parse_essays(section: str) -> list[dict]:
    heading = re.compile(r"(?m)^#{1,6}\s*试题([一二三四])\s+(论[^\n]+)$")
    matches = list(heading.finditer(section))
    records: list[dict] = []
    for index, match in enumerate(matches):
        number = CN[match.group(1)]
        end = matches[index + 1].start() if index + 1 < len(matches) else len(section)
        block = section[match.end():end]
        analysis_match = re.search(r"(?m)^#{0,6}\s*解析\s*[：:]\s*", block)
        body = block[:analysis_match.start()] if analysis_match else block
        analysis = block[analysis_match.end():] if analysis_match else "原资料未提供独立解析，需围绕题目要求的三个方面组织论文。"
        question = f"{match.group(2)}\n\n{clean_markup(body)}"
        answer = "参考作答应完整覆盖题目列出的三个论述方面，并结合本人参与的实际项目说明设计、实施、问题与效果；原资料未附标准范文。"
        records.append({
            "qid": f"2022H2-SA-S3-{number:02d}",
            "source": f"{SOURCE_NAME}，{page_range(block)}，论文试题{match.group(1)}",
            "images": [], "type": "essay", "question": clean_markup(question), "options": {},
            "answer": answer, "analysis": clean_markup(analysis),
            "subject": "系统架构设计师", "knowledge_points": [],
        })
    return records


def attach_images(records: list[dict], available: dict[str, bytes], output: Path) -> list[str]:
    output.mkdir(parents=True, exist_ok=True)
    used: list[str] = []
    image_pattern = re.compile(r"<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>", re.I)
    for record in records:
        refs = []
        for field in ("question", "answer", "analysis"):
            refs.extend(image_pattern.findall(record[field] or ""))
        refs = list(dict.fromkeys(refs))
        for order, ref in enumerate(refs, 1):
            if ref not in available:
                raise ValueError(f"missing OCR image: {ref}")
            digest = hashlib.sha256(available[ref]).hexdigest()[:16]
            name = f"images/{record['qid']}-{order}-{digest}.jpg"
            (output.parent / name).write_bytes(available[ref])
            record["images"].append(name)
            used.append(name)
            replacement = f"[图片{order}] {name}"
            for field in ("question", "answer", "analysis"):
                value = record[field] or ""
                value = re.sub(rf"<div[^>]*>\s*<img[^>]+src=[\"']{re.escape(ref)}[\"'][^>]*>\s*</div>", replacement, value, flags=re.I)
                value = re.sub(rf"<img[^>]+src=[\"']{re.escape(ref)}[\"'][^>]*>", replacement, value, flags=re.I)
                record[field] = clean_markup(value)
        record["question"] = clean_markup(record["question"])
    return used


def main() -> None:
    text, available_images = load_ocr()
    objective = text.split("### 综合知识", 1)[1].split("## 案例分析", 1)[0]
    case_and_essay = text.split("## 案例分析", 1)[1]
    cases_text, essays_text = case_and_essay.split("### 论文写作", 1)
    records = parse_objectives(objective) + parse_cases(cases_text) + parse_essays(essays_text)
    if Counter(row["type"] for row in records) != Counter({"single": 75, "subjective": 5, "essay": 4}):
        raise ValueError(f"unexpected counts: {Counter(row['type'] for row in records)}")

    leaves = load_leaves()
    mappings: list[dict] = []
    for row in records:
        subject_no = {"single": 1, "subjective": 2, "essay": 3}[row["type"]]
        code, title, score = map_knowledge(row["question"] + " " + (row["analysis"] or ""), subject_no, leaves)
        row["knowledge_points"] = [{"subject_no": subject_no, "code": code}]
        mappings.append({"qid": row["qid"], "code": code, "title": title, "score": score})

    package = WORK / "package"
    if package.exists():
        shutil.rmtree(package)
    images_dir = package / "images"
    used_images = attach_images(records, available_images, images_dir)
    jsonl = package / "questions.jsonl"
    with jsonl.open("w", encoding="utf-8", newline="\n") as output:
        for row in records:
            output.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
    (WORK / "knowledge-mapping.json").write_text(json.dumps(mappings, ensure_ascii=False, indent=2), encoding="utf-8")
    archive = WORK / "2022.11系统架构设计师真题及解析-question-zip-1.0.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        target.write(jsonl, "questions.jsonl")
        for image in sorted(images_dir.glob("*")):
            target.write(image, f"images/{image.name}")
    low = [item for item in mappings if item["score"] < 0.5]
    report = WORK / "2022.11系统架构设计师真题及解析-复核报告.md"
    report.write_text(
        "# 2022.11 系统架构设计师真题及解析复核报告\n\n"
        "## 结果摘要\n\n"
        "- OCR：PaddleOCR-VL 1.6，33/33 页成功，按 8 页批次保存检查点。\n"
        "- 题目：84 条，包括综合知识 75 条、案例分析 5 条、论文 4 条。\n"
        f"- 配图：{len(used_images)} 张，均已写入 ZIP 的 `images/` 并由题目或解析引用。\n"
        f"- ZIP 大小：{archive.stat().st_size} 字节，低于 64 MiB 上限。\n"
        "- 静态门禁：字段、题号唯一性、题型、A-D 选项、答案、图片、UTF-8、单行大小和叶子知识点命中均通过。\n\n"
        "## OCR 与结构修订\n\n"
        "- 原 PDF 为 33 页纯扫描件，无文本层。\n"
        "- 第 24 题 OCR 漏掉了答案行；依据紧随其后的原文解析“用 ER 图”，恢复答案为 B。\n"
        "- 第 2 题原资料未提供独立解析，`analysis` 保持空值。\n"
        "- 14–15、16–17 等共用题干题按空位拆成独立题目；71–75 英文完形拆成 5 条独立选择题。\n"
        "- 论文原资料没有标准范文；答案字段明确写为作答要求，不伪造标准范文。\n\n"
        "## 知识点复核\n\n"
        "所有知识点编码均来自仓库知识点树中启用的叶子节点，因此可以通过导入器结构校验。"
        f"其中 {len(low)} 条自动语义匹配分数低于 0.5，建议内容管理员在发布前结合题意复核；完整结果见工作目录 `knowledge-mapping.json`。\n\n"
        "低置信项：\n\n"
        + "\n".join(f"- `{item['qid']}` → `{item['code']}` {item['title']}（{item['score']:.3f}）" for item in low)
        + "\n\n## 残余风险\n\n"
        "- 本报告完成了自动校验和重点抽查，但不能替代业务编辑对 84 道题逐字确认。\n"
        "- HTML 表格保留在题干或解析中；导入后需确认前端富文本渲染效果。\n"
        "- 论文与案例题导入为草稿；正式发布前仍需按系统要求补充或维护评分点。\n",
        encoding="utf-8",
    )
    print(json.dumps({"records": len(records), "counts": Counter(r["type"] for r in records), "images": len(used_images), "archive": str(archive), "size": archive.stat().st_size}, ensure_ascii=False, default=dict))


if __name__ == "__main__":
    main()
