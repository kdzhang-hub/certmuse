# Local demo

This is a new, isolated demonstration environment. It creates only the tables
needed for administrator authentication and a small dashboard route; it does
not include production migrations, real users, exam content, or external keys.

1. Copy `.env.example` to `.env` if you need different local ports.
2. Run `docker compose -f infra/docker/compose.yml up --build`.
3. Open `http://localhost:13000` and sign in with `demo-admin` and `certmuse-demo`.
4. Run `pwsh -File infra/docker/verify-demo.ps1` to check proxying, login, identity and routes.

Captcha is disabled only in this isolated demo profile so the integration check
can log in non-interactively. It remains enabled by default for every other
profile.

The demo database is disposable. To initialize it again, remove only the
`certmuse-demo_postgres_data` and `certmuse-demo_redis_data` volumes before the
next `up` command.
