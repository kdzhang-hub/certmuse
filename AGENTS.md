# Repository Guidelines

## Project Structure & Module Organization

CertMuse combines a Java backend and Vue administration client. `backend/` is a Maven multi-module Spring Boot application: `ruoyi-admin` contains the executable entry point, `ruoyi-api` defines shared service contracts, `ruoyi-common` holds reusable infrastructure, and business features live in `ruoyi-modules`. Java follows the standard `src/main/java`, `src/main/resources`, and `src/test/java` layout. `web/admin/` contains the Vue 3/Vite client; place pages in `src/views`, API clients in `src/api`, shared UI in `src/components`, and static files in `public`. Architecture and operational decisions belong in `docs/`; deployment resources and maintenance utilities live in `infra/` and `tools/`.

Changes under `backend/**` must also follow `backend/AGENTS.md`. API contracts must define requests, success and failure responses, and acceptance cases; store them under `docs/requirements/` or the relevant domain documentation directory.

## Build, Test, and Development Commands

Run commands from the relevant subdirectory:

- `cd backend && .\mvnw.cmd clean package` — compile all backend modules and package the application (use `./mvnw` on macOS/Linux).
- `cd backend && .\mvnw.cmd test` — run the Java test suite.
- `cd backend && .\mvnw.cmd -pl ruoyi-admin spring-boot:run` — start the backend locally.
- `cd web/admin && pnpm install` — install the locked frontend dependencies; Node 20.19+ and pnpm 10+ are required.
- `pnpm dev` — launch the Vite development server.
- `pnpm build` — create a production bundle.
- `pnpm lint` / `pnpm fmt` — check TypeScript/Vue code or format the frontend.

## Coding Style & Naming Conventions

Honor each subtree's `.editorconfig`: use spaces, UTF-8, LF endings, four-space indentation by default, and two spaces in JSON/YAML. Java packages are lowercase; classes use PascalCase and established suffixes such as `Controller`, `ServiceImpl`, `DTO`, `Bo`, and `Vo`. Vue components use PascalCase filenames, while composables follow `useFeature`. Keep domain code in its owning module instead of expanding common modules prematurely. Run Oxfmt and Oxlint before submitting frontend changes.

## Testing Guidelines

Backend tests use JUnit and belong beside their module under `src/test/java`; name test classes `*Test.java` or retain the existing `*UnitTest.java` pattern. Add focused tests for changed business rules and regressions. The frontend includes Vitest but no test script or established suite yet; add colocated `*.spec.ts` tests when introducing testable logic, and always run lint plus a production build.

## Commit & Pull Request Guidelines

Recent history follows Conventional Commit-style subjects such as `docs: add D1 knowledge tree import plan`. Use a short imperative subject with a scope prefix (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`), and keep unrelated changes separate. Pull requests should explain intent, affected modules, configuration or migration steps, and verification performed. Link relevant issues or design documents; include screenshots for visible UI changes and sample requests/responses for API changes. Never commit secrets, local profiles, generated `target/` output, or frontend build artifacts.
