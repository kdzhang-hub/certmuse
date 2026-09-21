# Backend Development Guidelines

This file applies to `backend/` and supplements the repository root `AGENTS.md`.
Specific business contracts, database designs, and operations documents take precedence over this file.

## Codex Backend Skill

- For backend Java implementation, modification, refactoring, review, and JavaDoc tasks, read and follow `backend/.codex/skills/ruoyi-plus-ai-coding/SKILL.md`.
- Follow the skill's reference-routing rules and load only the references required for the current task. Do not preload every reference.
- Do not load the skill for frontend-only, documentation-only, Git-only, or general discussion tasks.
- Applicable `AGENTS.md` files and frozen API, database, and business contracts remain authoritative. The skill supplies the implementation workflow and detailed guidance; it does not override higher-priority repository instructions.

## Technology Stack

- Use Java 21, Spring Boot 4.1, Spring MVC, Jakarta Validation, and Jackson.
- Use MyBatis-Plus for standard persistence and XML Mappers for complex PostgreSQL queries.
- PostgreSQL 16 is the only CertMuse business database. Do not introduce MySQL dialect, syntax, or functions.
- Use Redis and Redisson for caching, distributed locks, and short-lived state.
- Use Sa-Token for authentication and authorization. Administrative endpoints require permission checks and resource-ownership checks.
- Access MinIO through the existing OSS abstraction. Do not bind business code directly to a storage vendor.
- Reuse existing `ruoyi-common-*` capabilities and the project's Lombok, Hutool, and MapStruct-Plus dependencies.
- This project uses MyBatis-Plus, not JPA. Do not introduce JPA repositories, `@Entity`, or a JPA-style `entity` package.
- AI, workflow, SnailJob, code generation, and external services are not enabled for the first production scope. Do not add their production dependencies, configuration, or menus without an approved activation plan.

## Module Boundaries

- `ruoyi-admin` contains application startup, assembly, global configuration, and runtime profiles. It does not contain CertMuse domain logic.
- `ruoyi-common` contains non-domain infrastructure with multiple independent consumers. Do not place CertMuse business models or rules there.
- `ruoyi-api` contains cross-module service contracts and must not depend on business-module implementations.
- `ruoyi-extend` contains upstream extension capabilities and is not the default location for new CertMuse business code.
- All CertMuse business code belongs in `ruoyi-modules/ruoyi-certmuse` under `org.dromara.certmuse`.
- Keep the current modular-monolith architecture. Do not create Maven business modules or microservices without an approved architecture change.

## CertMuse Package Structure

Organize CertMuse code by bounded domain. Within an existing domain, follow its established package structure; do not introduce another feature subtree solely to separate a page, import type, or aggregate.

```text
org.dromara.certmuse/
|- catalog/                         # Certifications, syllabi, subjects, knowledge points, materials, imports
|  |- config/
|  |- controller/
|  |- domain/
|  |  |- bo/
|  |  |- vo/
|  |  |- enums/
|  |  `- value/
|  |- mapper/
|  |- service/
|  |  `- impl/
|  |- support/
|  `- validation/
|- question/                        # Questions, answers, analysis, question groups, review
|- learning/                        # Plans, progress, error records, review
|- assessment/                      # Practice, simulations, attempts, grading, reports
|- profile/                         # Learning profiles, ability calculations, recommendation evidence
`- shared/                          # Stable cross-domain value objects, events, constants, and web-boundary types
```

- Keep the current `catalog` structure flat by technical layer. Put knowledge-tree, textbook, and import classes in the existing `catalog` packages, using clear type names such as `KnowledgeTreeController`, `TextbookService`, and `ImportService`. Do not create `catalog.knowledge`, `catalog.importing`, or other feature-specific subtrees without an approved architecture change.
- `controller`: HTTP binding, authorization, audit logging, and service invocation.
- `service`: use cases, state transitions, transactions, and domain coordination. Use `service.impl` for concrete service implementations when an interface is required.
- `mapper`: MyBatis Mapper interfaces and XML-backed persistence declarations. Reuse `BaseMapperPlus<DomainModel, XxxVo>` where it fits.
- `validation`: complex business, schema, and cross-field validation.
- `support`: feature-specific storage adapters, exceptions, response assembly, and strategies.
- `config`: Spring configuration owned by the domain or feature.
- `domain`: MyBatis-Plus table models. Follow the upstream naming convention such as `SysConfig`: a table model has no `Entity` suffix, uses `@TableName` and `@TableId` when required, and extends the relevant common base type when it needs audit fields. It is used by Mapper and Service only.
- `domain.bo`: Controller input and Service business objects. BOs carry validation annotations and use `@AutoMapper(target = DomainModel.class)` when automatic conversion is appropriate.
- `domain.vo`: API response models. VOs are the only business models returned by Controllers and use `@AutoMapper(target = DomainModel.class)` when appropriate.
- Do not introduce a project-wide `dto` package for ordinary web requests. Add a DTO only for an external integration or a stable cross-module transport contract, and keep it beside that contract.
- `domain.enums` and `domain.value`: stable domain concepts with behavior and validation; do not replace them with untyped strings or maps.
- Use one public top-level model type per file. New code must not introduce `XxxModels`, nested BO/VO records, or catch-all model containers.
- Do not create unowned `util`, `common`, or `manager` packages. Do not use a catch-all cross-domain package.

## Layering, API, and Security

- Controllers do not call Mappers or implement complex business logic.
- Services own business rules, state machines, and transaction boundaries. Do not include remote calls, long-running file handling, or unbounded loops in database transactions.
- Mappers own persistence only. Dynamic filters, sort fields, and pagination must use server-side allowlists.
- Do not expose domain table models, raw JSONB snapshots, or internal exception details through APIs.
- Use upstream naming consistently: `Xxx` for a table model, `XxxBo` for input and business data, and `XxxVo` for output. The normal flow is `BO -> domain model -> VO`; do not create duplicate models when a boundary does not require one.
- Cross-domain calls use explicit Service contracts or `ruoyi-api`; never access another domain's Mapper directly.
- Use Sa-Token permission annotations for administrative endpoints and enforce resource visibility in the service or query layer.
- Reuse the existing `R<T>`, `PageResult<T>`, exception handling, and audit-log mechanisms.
- HTTP status, response fields, machine error codes, and redaction rules must exactly match the applicable versioned API contract.
- Do not log or return passwords, tokens, cookies, private keys, OSS credentials, SQL details, or raw sensitive file content.

## API Contracts and Exception Handling

- API contracts must use `docs/requirements/API接口契约模板.md` and define testable request, response, error, behavior, security, and acceptance requirements. “Errors follow the existing mechanism” is prohibited.
- Keep `R<T>` with `code/msg/data`. For every new or migrated endpoint, the real HTTP status equals `R.code`; clients branch on stable `data.errorCode`, never localized `msg`.
- Controllers must not catch broad exceptions. Services throw domain exceptions for expected failures and preserve the cause when translating infrastructure, parsing, or serialization failures.
- Domain advice handles domain exceptions or genuinely special protocols only. The CertMuse module advice handles request, authentication, authorization, and unexpected failures; advice order must be explicit.
- Unexpected 500 responses contain a safe message and `traceId`. Log the complete cause once at the boundary without exposing internal or sensitive details.
- Do not create unowned `common`, `util`, or `manager` packages. Stable cross-domain HTTP boundary types belong in `org.dromara.certmuse.shared.web`.

## Data, Configuration, and Operations

- Add CertMuse database migrations under `backend/script/sql/postgres/certmuse/` using a new ordered migration file. Do not modify frozen or deployed migrations or upstream initialization SQL.
- JSONB follows `docs/architecture/系统架构设计师知识库-JSONB结构规范.md`: explicit schema versions, server-side validation, and stable field semantics.
- JSONB does not replace relational foreign keys and must not contain passwords, tokens, cookies, private keys, or connection strings.
- Use `BigDecimal` and PostgreSQL `numeric` for scores, calculations, and other precision-sensitive values. Do not use `double` or `float`.
- Redis keys use the `certmuse:` prefix. New caches require an expiration policy and documented consistency behavior.
- Database, Redis, and OSS credentials come from protected runtime environment variables only. Never commit real credentials to YAML, SQL, deployment declarations, or test fixtures.
- Production migrations run through RouteFlow Deployer after a dry-run. Do not apply ad hoc SQL directly to production.

## Testing and Verification

- Add focused JUnit tests for changed business rules, permissions, state transitions, persistence behavior, and regressions.
- Tests live in the owning module's `src/test/java`. Development-profile tests use the existing `@Tag("dev")` convention.
- The root Maven POM skips tests by default. In PowerShell, quote the Maven property when running tests:

  `cd backend && .\mvnw.cmd -pl ruoyi-modules/ruoyi-certmuse -am '-Dmaven.test.skip=false' test`

- For shared-module or cross-module changes, run:

  `cd backend && .\mvnw.cmd '-Dmaven.test.skip=false' test`

- For dependency, packaging, or runtime-configuration changes, also run:

  `cd backend && .\mvnw.cmd clean package`

## Documentation and Delivery

- Update the relevant `docs/` contract or architecture document when changing APIs, state machines, JSONB schemas, permissions, or database migrations.
- Keep changes in their owning domain. Document consumers, compatibility, and rollback for public abstractions, cross-domain writes, or upstream-code changes.
