#!/usr/bin/env python3
"""Convert the Markdown knowledge-point tree into importable JSONL.

The generated records conform to ``knowledge_point/1.0`` as documented in
``docs/imports/知识点树JSONL导入规范.md``.  Parent relationships are derived
from the Unicode tree indentation, not inferred from syllabus numbers.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Iterable


SCHEMA_VERSION = "1.0"
SOURCE_VERSION = "knowledge-tree-v7"
SYLLABUS_RE = re.compile(r"^(?P<number>[1-3](?:\.[1-9][0-9]*)+)\s+(?P<title>.+?)\s*$")
SUBJECT_RE = re.compile(r"^考试科目(?P<number>[1-3])(?:\s|$)")
TREE_LINE_RE = re.compile(r"^(?P<indent>(?:(?:│  |   ))*)(?:├─|└─)\s*(?P<content>.+?)\s*$")


class ConversionError(ValueError):
    """Raised when the Markdown tree cannot be converted safely."""


def text_code_blocks(markdown: str) -> Iterable[tuple[int, list[tuple[int, str]]]]:
    """Yield ``text`` fenced blocks as ``(fence_line, [(line_no, line), ...])``."""
    lines = markdown.splitlines()
    block: list[tuple[int, str]] | None = None
    fence_line = 0
    for line_no, line in enumerate(lines, 1):
        if block is None:
            if re.fullmatch(r"\s*```text\s*", line, re.IGNORECASE):
                block = []
                fence_line = line_no
        elif re.fullmatch(r"\s*```\s*", line):
            yield fence_line, block
            block = None
        else:
            block.append((line_no, line))
    if block is not None:
        raise ConversionError(f"第 {fence_line} 行的 text 代码块没有结束标记 ```")


def convert(markdown: str) -> list[dict[str, object]]:
    blocks = list(text_code_blocks(markdown))
    if not blocks:
        raise ConversionError("未找到 ```text 代码块")
    if len(blocks) != 1:
        locations = ", ".join(str(line) for line, _ in blocks)
        raise ConversionError(f"应恰好有一个 text 代码块，实际位于第 {locations} 行")

    records: list[dict[str, object]] = []
    stack: list[str] = []
    sibling_counts: defaultdict[tuple[int, str | None], int] = defaultdict(int)
    seen_numbers: set[tuple[int, str]] = set()
    current_subject: int | None = None
    subject_tree_level: int | None = None

    for line_no, line in blocks[0][1]:
        if not line.strip():
            continue
        tree_match = TREE_LINE_RE.fullmatch(line)
        if not tree_match:
            # The unadorned document root is allowed; every other line must be
            # an actual Unicode tree line so malformed indentation is visible.
            if line.strip() == "系统架构设计师考试大纲":
                continue
            raise ConversionError(f"第 {line_no} 行不是有效的 Unicode 树节点: {line!r}")

        tree_level = len(tree_match.group("indent")) // 3
        content = tree_match.group("content")
        subject_match = SUBJECT_RE.match(content)
        if subject_match:
            current_subject = int(subject_match.group("number"))
            subject_tree_level = tree_level
            stack.clear()
            continue

        node_match = SYLLABUS_RE.fullmatch(content)
        if not node_match:
            raise ConversionError(f"第 {line_no} 行缺少合法的知识点编号和标题: {content!r}")
        if current_subject is None or subject_tree_level is None:
            raise ConversionError(f"第 {line_no} 行的知识点出现在考试科目节点之前")

        syllabus_number = node_match.group("number")
        title = node_match.group("title").strip()
        if int(syllabus_number[0]) != current_subject:
            raise ConversionError(
                f"第 {line_no} 行编号 {syllabus_number} 与考试科目 {current_subject} 不一致"
            )
        if len(title) > 500:
            raise ConversionError(f"第 {line_no} 行标题超过 500 个字符")

        depth = tree_level - subject_tree_level
        if depth < 1:
            raise ConversionError(f"第 {line_no} 行知识点缩进不在当前考试科目之下")
        if depth > len(stack) + 1:
            raise ConversionError(f"第 {line_no} 行缩进跳级，无法确定直接父节点")
        del stack[depth - 1 :]
        parent = stack[-1] if stack else None

        unique_key = (current_subject, syllabus_number)
        if unique_key in seen_numbers:
            raise ConversionError(f"第 {line_no} 行存在重复编号 {syllabus_number}")
        seen_numbers.add(unique_key)

        sibling_key = (current_subject, parent)
        sibling_counts[sibling_key] += 1
        records.append(
            {
                "schema_version": SCHEMA_VERSION,
                "source_key": f"{SOURCE_VERSION}:{current_subject}:{syllabus_number}",
                "subject_no": current_subject,
                "syllabus_number": syllabus_number,
                "syllabus_title": title,
                "parent_syllabus_number": parent,
                "tree_depth": depth,
                "sort_order": sibling_counts[sibling_key],
                "description": None,
                "importance": None,
                "diagnostic_enabled": False,
                "recommendation_enabled": False,
                "status": "0",
            }
        )
        stack.append(syllabus_number)

    if not records:
        raise ConversionError("text 代码块中没有知识点记录")
    missing_subjects = {1, 2, 3} - {int(record["subject_no"]) for record in records}
    if missing_subjects:
        raise ConversionError(f"缺少考试科目: {sorted(missing_subjects)}")
    return records


def write_jsonl(records: Iterable[dict[str, object]], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = output_path.with_name(output_path.name + ".tmp")
    try:
        with temporary_path.open("w", encoding="utf-8", newline="\n") as output:
            for record in records:
                output.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")))
                output.write("\n")
        temporary_path.replace(output_path)
    finally:
        temporary_path.unlink(missing_ok=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="将知识点树 Markdown 转换为 knowledge_point/1.0 JSONL"
    )
    parser.add_argument("input", type=Path, help="输入 Markdown 文件")
    parser.add_argument("output", type=Path, help="输出 JSONL 文件")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        markdown = args.input.read_text(encoding="utf-8-sig")
        records = convert(markdown)
        write_jsonl(records, args.output)
    except (OSError, UnicodeError, ConversionError) as error:
        print(f"转换失败: {error}", file=sys.stderr)
        return 1
    counts = {subject: 0 for subject in (1, 2, 3)}
    for record in records:
        counts[int(record["subject_no"])] += 1
    print(f"转换完成: {args.output}，共 {len(records)} 条，科目分布 {counts}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
