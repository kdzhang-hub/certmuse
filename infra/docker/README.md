# CertMuse Docker Environment

The repository has one persistent Compose project, `certmuse`. It runs PostgreSQL 16,
Redis, MinIO, the Spring Boot backend, and the Vue/Nginx frontend. Do not create a second
persistent project with a different Compose project name.

## Start

```powershell
Set-Location infra/docker
Copy-Item .env.example .env
docker compose config
docker compose up -d --build
docker compose ps
```

Open:

- Application: <http://localhost:3000>
- Backend: <http://localhost:18080>
- MinIO console: <http://localhost:9001>

The PostgreSQL volume is initialized once with the RuoYi and SnailJob PostgreSQL baselines, CertMuse SQL `001` through `006`, every tracked dated CertMuse patch required by the image, local MinIO/menu adjustments, and `verify_local.sql`. The RuoYi seed administrator uses the default password documented in `postgres_ry_vue.sql`.

The former source-mounted `certmuse-local` stack was retired. Development that only needs
infrastructure should start selected services from `compose.yml`.

## Development and operations

```powershell
# Start host-based development: Docker runs PostgreSQL, Redis, and MinIO;
# separate terminals run the Spring Boot backend and Vite frontend.
Set-Location ../..
.\start-dev.bat

# Follow backend logs
docker compose logs -f backend

# Rebuild only application images after source changes
docker compose up -d --build backend frontend

# Start only infrastructure for host-based development
docker compose up -d postgres redis minio minio-init

# Stop while preserving data
docker compose down
```

`start-dev.bat` creates `infra/docker/.env` from `.env.example` on first use,
then shares its database and Redis connection settings with the host backend.
Frontend changes hot-reload through Vite. The backend does not currently include
Spring Boot DevTools, so restart its terminal after Java changes. Use `start.bat`
when you need the full production-style Docker image stack instead. The root
script rebuilds the application images (including the backend Maven build) while the current application keeps running,
applies existing-volume migrations, replaces the application containers, checks that
they use the newly built images, verifies the mapped frontend URL, and then opens that
URL in the default browser. If build or migration fails, the current application is
left running and the browser is not opened for an unverified replacement.

For deterministic local deployment, the script first fetches `origin/develop` and
refuses to build when the local checkout is behind. It fingerprints all application
build inputs (including uncommitted files), rebuilds only the backend and frontend
images while reusing BuildKit dependency caches, embeds that fingerprint in both
images, and verifies it again after container replacement. Rebuild the `postgres`
service separately when its Dockerfile, initialization SQL, or base image changes.
The frontend entry document is served with `no-store` and opened with the fingerprint
in its query string so an old browser entry cannot masquerade as the newly deployed
application. Named data volumes remain persistent.

To deliberately recreate the local database from the current SQL baseline:

```powershell
docker compose down -v
docker compose up -d --build
```

The `down -v` command permanently deletes this Compose project's local PostgreSQL, Redis, and MinIO data. Do not run it against data that must be retained.

PostgreSQL runs files from `/docker-entrypoint-initdb.d` only when its data directory is empty. Rebuilding an image does not migrate an existing volume. `start.bat` therefore runs `tools/ops/migrate-local-database.ps1` after the containers become healthy. The migration runner applies tracked incremental SQL in release order, records each SHA-256 in `certmuse_meta.schema_migration`, skips matching migrations, and stops if an applied file changes or any migration fails. Run the same script manually after starting only the infrastructure services.

SnailJob, SnailAI, the monitoring server, and TLS termination are intentionally outside this local first-phase stack.
