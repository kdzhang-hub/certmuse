# 参考原始资料

本目录管理 CertMuse 的教材、考试大纲、历年试卷、答案与解析等参考资料。

Git 只保存本说明和资料索引；原始 PDF、扫描件、OCR 输出及其他大文件不提交到 Git。原始文件先放在本地 `source/`，后续由受控同步流程归档到 77 的资料库。

## 目录

```text
docs/references/
├── README.md
├── catalog/
│   └── materials.yml        # 可提交的资料元数据索引
├── source/                  # 本地原始文件工作副本，Git 忽略
│   ├── textbooks/
│   ├── exam-papers/
│   ├── syllabus/
│   └── answer-keys/
├── work/                    # OCR、提取与分析中间产物，Git 忽略
└── archive/                 # 已校验的历史资料版本与归档包，Git 忽略
```

## 归档规则

- 以 `catalog/materials.yml` 记录每份资料的稳定 ID、名称、类别、适用证书/科目、来源、版权状态、文件名、SHA-256 与归档状态。
- 文件名使用 `类别_证书或科目_年份或版本_简短名称.pdf`，例如 `exam-paper_pmp_2025_mock-a.pdf`。
- 原始文件进入 `source/` 后不得直接修改；需要 OCR、裁切或标注时，在 `work/` 生成派生文件。
- 没有来源、版权状态或 SHA-256 的资料不得标记为可用题库来源。
- 不将资料目录映射到 Nginx 公共路径；应用使用前必须经过受控导入与权限设计。

## 本地与远端

本地 `source/`、`work/` 与 `archive/` 均为按需工作副本，不是 Git 内容。77 的
对应目录固定为：

```text
/app/certmuse-references/
├── source/                  # 原始教材、考纲、试卷、答案
├── work/                    # OCR、提取、切块、标注与分析产物
└── archive/                 # 已校验的历史版本和归档件
```

日常资料文件只通过项目本地 `srvctl` 同步，不使用 SSH、SCP、SFTP 客户端或
Deployer。macOS/Linux 使用：

```bash
# 上传本地原始考纲
tools/ops/certmusectl references push source syllabus/architecture-2026.pdf

# 从 77 下载处理产物
tools/ops/certmusectl references pull work ocr/architecture-2026.txt
```

Windows PowerShell 使用：

```powershell
.\tools\ops\certmusectl.ps1 references push source syllabus/architecture-2026.pdf
.\tools\ops\certmusectl.ps1 references pull work ocr/architecture-2026.txt
```

每次同步脚本都会先执行对应的 `policy check`，并且只接受 `source`、`work`、
`archive` 下的单个相对文件路径。`catalog/materials.yml` 仍由 Git 管理；D1 的
知识点树 JSONL 应从管理端上传至 MinIO，不使用本同步通道。
