# Security policy

## Supported public release

CertMuse is currently published as a source-only pre-release. No supported deployment package or hosted service is provided from this repository.

## Reporting a vulnerability

**Do not open a public issue for a suspected vulnerability, exposed credential, private key, user-data exposure, or security-sensitive operational detail.**

Before publishing, maintainers must enable GitHub private vulnerability reporting for this repository and provide a monitored contact channel in the repository profile. Until that channel is configured, use the repository owner's private GitHub contact route.

Please include:

- A concise description of the impact.
- The affected public path, component, and revision.
- Reproduction steps or a minimal proof of concept that does not disclose personal data or secrets.
- Any mitigation you have already identified.

## Maintainer response commitments

Maintainers should acknowledge a valid private report promptly, assess the impact, coordinate a fix without unnecessary disclosure, and credit reporters when they agree to be named.

## Scope boundaries

Do not submit real credentials, production endpoints, database exports, private documents, or examination content. If a potential secret is found in Git history, treat it as exposed: revoke or rotate it first, then remove it from reachable history before publishing a remediation.
