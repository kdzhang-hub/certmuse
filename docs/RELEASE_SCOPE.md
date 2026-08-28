# Release scope

CertMuse is published as a source-only release. This page defines what that means for users, contributors, and downstream redistributors.

## Included

- Java and Spring Boot source for the CertMuse domain and retained platform modules.
- Vue source for the learner and administrator applications.
- Public documentation, licensing, contribution, security, and community guidance.
- A disposable Docker local demo for authentication, session identity and a minimal administrator route. Its schema and seed contain no imported users, examination content or external keys.

## Intentionally excluded

- Environment files, deployment credentials, tokens, and private keys. Runtime-sensitive settings in the retained source are empty environment-variable placeholders, not deployable defaults.
- Complete production database initialization, migrations, seed data, and operational backups. The retained local demo schema is intentionally limited to authentication and the minimal route required to exercise it.
- Production deployment manifests, production service definitions, and infrastructure automation.
- Private reference material, examination content, user data, and imported documents.
- CI configuration and a supported production startup workflow.

## What this means in practice

Do not expect a fresh clone to start the complete application. The authentication demo is runnable with Docker and documented in [infra/docker](../infra/docker/README.md); it is deliberately not a substitute for the complete database and operational stack. If you are studying the code, begin with [Project overview](PROJECT_OVERVIEW.md). If you are proposing a contribution, follow [CONTRIBUTING.md](../CONTRIBUTING.md).

## For downstream users

You are responsible for your own environment design, database schema, security hardening, credentials, licensing review, and compliance assessment. Nothing in this repository is a production deployment guide or a guarantee that a particular examination, curriculum, or data source is supported.

When deploying a fork, route `/prod-api/` to the Java backend explicitly. Do not use a single-page-app fallback for that path: a fallback can return `index.html` with HTTP 200 for a GET request and reject a login POST with HTTP 405, neither of which is an API response. The local Docker demo proves this proxy contract, but downstream production deployments must supply and operate their own complete backend, PostgreSQL schema and migrations, Redis, secrets, and administrator bootstrap data.

## Publication safeguards

Before publishing a new public release, maintainers must verify that no credentials, private keys, user data, private documents, or operational artifacts are included in the commit or its reachable Git history. See [SECURITY.md](../SECURITY.md) for the reporting policy.
