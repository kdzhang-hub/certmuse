from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import threading
import traceback
import uuid
import zipfile
from datetime import datetime
from email import policy
from email.parser import BytesParser
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse


ROOT = Path(__file__).resolve().parent
JOBS_ROOT = ROOT / "work/jobs"
DEFAULT_KNOWLEDGE = ROOT / "data/knowledge-points.jsonl"
MAX_UPLOAD = 80 * 1024 * 1024
JOBS: dict[str, dict] = {}
LOCK = threading.Lock()


def parse_multipart(content_type: str, body: bytes) -> tuple[dict[str, str], dict[str, tuple[str, bytes]]]:
    message = BytesParser(policy=policy.default).parsebytes(
        f"Content-Type: {content_type}\r\nMIME-Version: 1.0\r\n\r\n".encode("ascii") + body
    )
    if not message.is_multipart():
        raise ValueError("请求不是 multipart/form-data")
    fields: dict[str, str] = {}
    files: dict[str, tuple[str, bytes]] = {}
    for part in message.iter_parts():
        name = part.get_param("name", header="content-disposition")
        if not name:
            continue
        payload = part.get_payload(decode=True) or b""
        filename = part.get_filename()
        if filename:
            files[name] = (Path(filename).name, payload)
        else:
            fields[name] = payload.decode(part.get_content_charset() or "utf-8", errors="replace")
    return fields, files


INDEX = r"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>试卷 OCR 转题目 ZIP</title><style>
*{box-sizing:border-box}body{margin:0;background:#f4f6fa;color:#182033;font:14px/1.55 system-ui,"Microsoft YaHei",sans-serif}
main{max-width:920px;margin:42px auto;padding:0 20px}.card{background:#fff;border:1px solid #dde3ed;border-radius:14px;padding:24px;box-shadow:0 8px 28px #24334d12}h1{margin:0 0 6px;font-size:25px}p{color:#61708a}.grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}label{display:block;font-weight:600;margin:13px 0 6px}input{width:100%;padding:10px 12px;border:1px solid #cbd4e2;border-radius:8px;background:#fff}button{margin-top:20px;padding:11px 18px;border:0;border-radius:8px;background:#2458d3;color:#fff;font-weight:700;cursor:pointer}button:disabled{opacity:.55}.status{display:none;margin-top:20px}.bar{height:8px;background:#e8edf5;border-radius:8px;overflow:hidden}.bar i{display:block;height:100%;width:0;background:#2e66e7;transition:.3s}.log{height:230px;overflow:auto;white-space:pre-wrap;background:#101827;color:#d8e3f7;padding:14px;border-radius:9px}.downloads a{display:inline-block;margin:5px 9px 5px 0;color:#2458d3}@media(max-width:700px){.grid{grid-template-columns:1fr}}
</style></head><body><main><section class="card"><h1>试卷 OCR 转题目 ZIP</h1><p>适用于含“综合知识 / 案例分析 / 论文写作”和逐题答案解析的系统架构设计师资料。</p>
<form id="form"><label>PDF 文件</label><input name="pdf" type="file" accept="application/pdf,.pdf" required>
<div class="grid"><div><label>题号前缀</label><input name="qid_prefix" value="ARCH-PAPER" pattern="[A-Za-z0-9_-]+" required></div><div><label>OCR 每批页数</label><input name="batch_size" type="number" min="1" max="25" value="8" required></div></div>
<label>PaddleOCR-VL 地址</label><input name="endpoint" value="http://192.168.6.120:8081/layout-parsing" required>
<label>知识点 JSONL（可选，留空使用内置三科知识树）</label><input name="knowledge" type="file" accept=".jsonl,application/json">
<button id="submit">开始转换</button></form>
<div id="status" class="status"><h3 id="stage">准备中</h3><div class="bar"><i id="progress"></i></div><p id="summary"></p><div id="downloads" class="downloads"></div><pre id="log" class="log"></pre></div>
</section></main><script>
const form=document.querySelector('#form'), submit=document.querySelector('#submit'), box=document.querySelector('#status');
form.onsubmit=async e=>{e.preventDefault();submit.disabled=true;box.style.display='block';document.querySelector('#downloads').innerHTML='';
 try{const r=await fetch('/jobs',{method:'POST',body:new FormData(form)}),d=await r.json();if(!r.ok)throw Error(d.error||'提交失败');poll(d.id)}catch(e){document.querySelector('#stage').textContent='提交失败';document.querySelector('#log').textContent=e.message;submit.disabled=false}}
async function poll(id){const r=await fetch('/api/jobs/'+id),d=await r.json();document.querySelector('#stage').textContent=d.stage;document.querySelector('#progress').style.width=d.progress+'%';document.querySelector('#summary').textContent=d.summary||'';document.querySelector('#log').textContent=(d.log||[]).join('\n');
 const links=(d.outputs||[]).map(x=>`<a href="${x.url}">${x.label}</a>`).join('');document.querySelector('#downloads').innerHTML=links;
 if(d.status==='done'||d.status==='failed'){submit.disabled=false;return}setTimeout(()=>poll(id),1500)}
</script></body></html>"""


def update(job_id: str, **values: object) -> None:
    with LOCK:
        JOBS[job_id].update(values)


def log(job_id: str, message: str) -> None:
    with LOCK:
        JOBS[job_id]["log"].append(f"[{datetime.now():%H:%M:%S}] {message}")


def run_command(job_id: str, command: list[str]) -> None:
    process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding="utf-8", errors="replace")
    assert process.stdout
    for line in process.stdout:
        log(job_id, line.rstrip())
    if process.wait() != 0:
        raise RuntimeError(f"命令执行失败，退出码 {process.returncode}")


def make_markdown_archive(work: Path) -> Path:
    target = work / "ocr-markdown.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted((work / "ocr-markdown").glob("*.md")):
            archive.write(path, path.name)
    return target


def save_to_desktop(source_files: list[tuple[Path, str]], source_name: str) -> Path:
    desktop = Path.home() / "Desktop"
    desktop.mkdir(parents=True, exist_ok=True)
    safe_stem = re.sub(r'[<>:"/\\|?*\x00-\x1f]+', "-", Path(source_name).stem).strip(" .-") or "试卷"
    target = desktop / "试卷OCR产物" / f"{safe_stem}-{datetime.now():%Y%m%d-%H%M%S}"
    target.mkdir(parents=True, exist_ok=False)
    for path, _label in source_files:
        if path.is_file():
            shutil.copy2(path, target / path.name)
    return target


def run_job(job_id: str) -> None:
    job = JOBS[job_id]
    work = Path(job["work"])
    try:
        update(job_id, status="running", stage="正在执行 PaddleOCR-VL", progress=10)
        run_command(job_id, [sys.executable, str(ROOT / "ocr_engine.py"), job["pdf"], str(work),
                             "--batch-size", str(job["batch_size"]), "--endpoint", job["endpoint"]])
        update(job_id, stage="正在拆题、匹配知识点并打包", progress=72)
        run_command(job_id, [sys.executable, str(ROOT / "transformer.py"), str(work), "--knowledge", job["knowledge"],
                             "--source-name", job["source_name"], "--qid-prefix", job["qid_prefix"]])
        update(job_id, stage="正在整理产物", progress=92)
        markdown = make_markdown_archive(work)
        stem = Path(job["source_name"]).stem
        files = [
            (work / f"{stem}-question-zip-1.0.zip", "题目导入 ZIP"),
            (work / f"{stem}-复核报告.md", "复核报告"),
            (work / "knowledge-mapping.json", "知识点映射"),
            (markdown, "OCR Markdown"),
        ]
        outputs = [{"label": label, "url": f"/download/{job_id}/{path.name}"} for path, label in files if path.exists()]
        desktop_output = save_to_desktop(files, job["source_name"])
        update(job_id, status="done", stage="转换完成", progress=100,
               summary=f"产物已自动保存到：{desktop_output}。请先阅读复核报告，再导入 ZIP。", outputs=outputs)
        log(job_id, f"桌面产物目录：{desktop_output}")
        log(job_id, "全部产物已生成。")
    except Exception as error:
        log(job_id, f"ERROR: {error}")
        log(job_id, traceback.format_exc())
        update(job_id, status="failed", stage="转换失败", summary=str(error))


class Handler(BaseHTTPRequestHandler):
    server_version = "QuestionOcrPipeline/1.0"

    def send_json(self, data: object, status: int = 200) -> None:
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status); self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def do_GET(self) -> None:
        path = unquote(urlparse(self.path).path)
        if path == "/":
            body = INDEX.encode("utf-8"); self.send_response(200); self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body); return
        api = re.fullmatch(r"/api/jobs/([a-f0-9]+)", path)
        if api:
            with LOCK: job = JOBS.get(api.group(1))
            if not job: self.send_json({"error": "任务不存在"}, 404); return
            self.send_json({k: job[k] for k in ("id", "status", "stage", "progress", "summary", "log", "outputs")}); return
        download = re.fullmatch(r"/download/([a-f0-9]+)/([^/]+)", path)
        if download:
            with LOCK: job = JOBS.get(download.group(1))
            if not job: self.send_error(404); return
            file = (Path(job["work"]) / Path(download.group(2)).name).resolve()
            if file.parent != Path(job["work"]).resolve() or not file.is_file(): self.send_error(404); return
            data = file.read_bytes(); self.send_response(200); self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Disposition", f"attachment; filename*=UTF-8''{download.group(2)}")
            self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data); return
        self.send_error(404)

    def do_POST(self) -> None:
        if urlparse(self.path).path != "/jobs": self.send_error(404); return
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > MAX_UPLOAD: self.send_json({"error": "上传内容为空或超过 80 MiB"}, 413); return
        try:
            fields, files = parse_multipart(self.headers.get("Content-Type", ""), self.rfile.read(length))
        except ValueError as error:
            self.send_json({"error": str(error)}, 400); return
        pdf = files.get("pdf")
        if pdf is None or not pdf[0].lower().endswith(".pdf"): self.send_json({"error": "请选择 PDF 文件"}, 400); return
        prefix = fields.get("qid_prefix", "").strip()
        endpoint = fields.get("endpoint", "").strip()
        if not re.fullmatch(r"[A-Za-z0-9_-]+", prefix): self.send_json({"error": "题号前缀只能包含字母、数字、下划线和短横线"}, 400); return
        if not endpoint.startswith(("http://", "https://")): self.send_json({"error": "OCR 地址无效"}, 400); return
        batch_size = max(1, min(25, int(fields.get("batch_size", "8"))))
        job_id = uuid.uuid4().hex
        work = JOBS_ROOT / job_id; work.mkdir(parents=True)
        source_name = pdf[0]
        pdf_path = work / "source.pdf"; pdf_path.write_bytes(pdf[1])
        knowledge_path = DEFAULT_KNOWLEDGE
        if "knowledge" in files:
            knowledge_path = work / "knowledge-points.jsonl"; knowledge_path.write_bytes(files["knowledge"][1])
        job = {"id": job_id, "status": "queued", "stage": "任务已创建", "progress": 2, "summary": "", "log": [], "outputs": [],
               "work": str(work), "pdf": str(pdf_path), "source_name": source_name, "qid_prefix": prefix,
               "endpoint": endpoint, "batch_size": batch_size, "knowledge": str(knowledge_path)}
        with LOCK: JOBS[job_id] = job
        threading.Thread(target=run_job, args=(job_id,), daemon=True).start()
        self.send_json({"id": job_id}, HTTPStatus.ACCEPTED)

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"[{datetime.now():%H:%M:%S}] {self.address_string()} {fmt % args}")


def main() -> None:
    import argparse
    parser = argparse.ArgumentParser(description="Question OCR Pipeline web UI")
    parser.add_argument("--host", default="127.0.0.1"); parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args(); JOBS_ROOT.mkdir(parents=True, exist_ok=True)
    print(f"Open http://{args.host}:{args.port}")
    ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()


if __name__ == "__main__": main()
