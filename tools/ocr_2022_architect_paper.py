from __future__ import annotations

import argparse
import base64
import json
import time
import urllib.request
from pathlib import Path

from pypdf import PdfReader, PdfWriter


ENDPOINT = "http://192.168.6.120:8081/layout-parsing"


def split_pdf(source: Path, chunks: Path, batch_size: int) -> list[tuple[int, int, Path]]:
    reader = PdfReader(str(source))
    result: list[tuple[int, int, Path]] = []
    chunks.mkdir(parents=True, exist_ok=True)
    for start in range(0, len(reader.pages), batch_size):
        end = min(start + batch_size, len(reader.pages))
        path = chunks / f"pages-{start + 1:02d}-{end:02d}.pdf"
        if not path.exists():
            writer = PdfWriter()
            for page in reader.pages[start:end]:
                writer.add_page(page)
            with path.open("wb") as output:
                writer.write(output)
        result.append((start + 1, end, path))
    return result


def ocr_chunk(start: int, end: int, pdf: Path, raw_dir: Path, md_dir: Path) -> None:
    raw_path = raw_dir / f"pages-{start:02d}-{end:02d}.json"
    md_path = md_dir / f"pages-{start:02d}-{end:02d}.md"
    if raw_path.exists() and md_path.exists():
        print(f"skip {start}-{end}: checkpoint exists", flush=True)
        return
    payload = {
        "file": base64.b64encode(pdf.read_bytes()).decode("ascii"),
        "fileType": 0,
        "restructurePages": True,
        "mergeTables": True,
        "relevelTitles": True,
        "formatBlockContent": True,
    }
    print(f"ocr {start}-{end}: upload {pdf.stat().st_size} bytes", flush=True)
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(ENDPOINT, data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=1800) as response:
        data = json.loads(response.read())
    if data.get("errorCode") != 0:
        raise RuntimeError(f"OCR {start}-{end} failed: {data.get('errorMsg')} ({data.get('errorCode')})")
    raw_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    blocks = data.get("result", {}).get("layoutParsingResults", [])
    markdown: list[str] = []
    for offset, block in enumerate(blocks):
        page = start + offset
        text = block.get("markdown", {}).get("text", "")
        markdown.append(f"\n<!-- PDF_PAGE:{page} -->\n\n{text.rstrip()}\n")
    md_path.write_text("\n".join(markdown), encoding="utf-8")
    print(f"done {start}-{end}: {len(blocks)} page results", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("work", type=Path)
    parser.add_argument("--batch-size", type=int, default=8)
    args = parser.parse_args()
    args.work.mkdir(parents=True, exist_ok=True)
    chunks = args.work / "chunks"
    raw_dir = args.work / "ocr-raw"
    md_dir = args.work / "ocr-markdown"
    raw_dir.mkdir(exist_ok=True)
    md_dir.mkdir(exist_ok=True)
    for start, end, path in split_pdf(args.source, chunks, args.batch_size):
        for attempt in range(1, 4):
            try:
                ocr_chunk(start, end, path, raw_dir, md_dir)
                break
            except Exception as error:
                print(f"attempt {attempt}/3 failed for {start}-{end}: {error}", flush=True)
                if attempt == 3:
                    raise
                time.sleep(5 * attempt)


if __name__ == "__main__":
    main()
