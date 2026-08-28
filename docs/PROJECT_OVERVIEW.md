# Project overview

This guide helps readers navigate the public CertMuse source without implying that the release is independently deployable. For the publication boundary, read [Release scope](RELEASE_SCOPE.md).

## Product domains

The `backend/ruoyi-modules/ruoyi-certmuse/` module is organized into the following areas:

| Area | Responsibility |
| --- | --- |
| `assessment` | Diagnostic and practice-oriented assessment workflows |
| `catalog` | Certification-oriented content and catalog operations |
| `learning` | Learning goals, tasks, records, and learner progression |
| `profile` | Learner profile and progress-oriented views of domain data |
| `question` | Question-bank and question lifecycle concerns |
| `agent` and `ai` | AI and agent-oriented extensions around learning workflows |
| `shared` | Cross-domain contracts, validation, and web-facing support |

## Applications

The two Vue applications intentionally serve different users.

| Application | Audience | Main areas in the public source |
| --- | --- | --- |
| `web/student/` | Learners | Account, dashboard, onboarding, goals, diagnostics, practice, tasks, progress, history, mistakes, results, and resources |
| `web/admin/` | Administrators and content operators | Catalog, insight, learning, and question operations |

## Backend layout

| Directory | Role |
| --- | --- |
| `backend/ruoyi-modules/ruoyi-certmuse/` | CertMuse-specific domain source |
| `backend/ruoyi-common/` | Shared Java capabilities used across modules |
| `backend/ruoyi-api/` | API-facing contracts and application integration points |
| `backend/ruoyi-admin/` | Application entry source |
| `backend/ruoyi-modules/` | Other platform modules retained with the source release |

## Reading order

1. Start with the learner and administrator view directories to see the product surfaces.
2. Follow their API clients into `backend/ruoyi-modules/ruoyi-certmuse/`.
3. Read the corresponding domain area before changing a controller, mapper, or user interface.
4. Check shared contracts and common modules before duplicating utility or infrastructure code.

## What this guide does not provide

This repository does not publish environment values, database provisioning, migrations, operational scripts, CI workflows, or a supported startup path. Do not infer absent infrastructure details from source code or issue requests.
