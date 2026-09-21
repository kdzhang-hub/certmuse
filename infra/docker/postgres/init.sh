#!/bin/sh
set -eu

run_sql() {
    echo "Applying $1"
    psql --set=ON_ERROR_STOP=1 \
        --username "$POSTGRES_USER" \
        --dbname "$POSTGRES_DB" \
        --file "$1"
}

register_local_migrations() {
    psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE SCHEMA IF NOT EXISTS certmuse_meta;
CREATE TABLE IF NOT EXISTS certmuse_meta.schema_migration (
    migration_name varchar(255) PRIMARY KEY,
    sha256 char(64) NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now()
);
SQL

    while IFS= read -r migration_name; do
        case "$migration_name" in ''|'#'*) continue ;; esac
        migration_file="/opt/certmuse/migrations/$migration_name"
        test -f "$migration_file"
        migration_sha256=$(sha256sum "$migration_file" | awk '{print $1}')
        psql --set=ON_ERROR_STOP=1 \
            --username "$POSTGRES_USER" \
            --dbname "$POSTGRES_DB" \
            --set=migration_name="$migration_name" \
            --set=migration_sha256="$migration_sha256" <<'SQL'
INSERT INTO certmuse_meta.schema_migration(migration_name, sha256)
VALUES (:'migration_name', :'migration_sha256')
ON CONFLICT (migration_name) DO UPDATE SET sha256 = EXCLUDED.sha256;
SQL
    done < /opt/certmuse/migrations/local-migration-manifest.txt
}

apply_local_migrations() {
    while IFS= read -r migration_name; do
        case "$migration_name" in ''|'#'*) continue ;; esac
        run_sql "/opt/certmuse/migrations/$migration_name"
    done < /opt/certmuse/migrations/local-migration-manifest.txt
}

psql --set=ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" <<SQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'certmuse_owner') THEN
        CREATE ROLE certmuse_owner NOLOGIN;
    END IF;
END
\$\$;
GRANT certmuse_owner TO "$POSTGRES_USER";
SQL

for sql_file in \
    /opt/certmuse/sql/00-postgres-ry-vue.sql \
    /opt/certmuse/sql/01-postgres-ry-job.sql \
    /opt/certmuse/sql/10-foundation.sql \
    /opt/certmuse/sql/11-content.sql \
    /opt/certmuse/sql/12-learning.sql \
    /opt/certmuse/sql/13-profile.sql \
    /opt/certmuse/sql/14-support.sql \
    /opt/certmuse/sql/15-constraints-indexes-seed.sql
do
    run_sql "$sql_file"
done

apply_local_migrations

psql --set=ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --set=oss_access_key="$MINIO_ROOT_USER" \
    --set=oss_secret_key="$MINIO_ROOT_PASSWORD" \
    --set=oss_bucket="$MINIO_BUCKET" \
    --set=oss_endpoint="$MINIO_INTERNAL_ENDPOINT" <<'SQL'
UPDATE sys_oss_config
SET access_key = :'oss_access_key',
    secret_key = :'oss_secret_key',
    bucket_name = :'oss_bucket',
    -- Browser requests use the frontend's same-origin proxy. The backend and
    -- nginx therefore share this internal endpoint without exposing it.
    endpoint = :'oss_endpoint',
    is_https = 'N',
    access_policy = '0',
    status = 'Y',
    update_time = now()
WHERE config_key = 'minio';
SQL

if [ "${LOCAL_PUBLIC_REGISTRATION:-true}" = "true" ]; then
    psql --set=ON_ERROR_STOP=1 \
        --username "$POSTGRES_USER" \
        --dbname "$POSTGRES_DB" <<'SQL'
UPDATE sys_config
SET config_value = 'true', update_time = now()
WHERE config_key = 'sys.account.registerUser';

UPDATE sys_config
SET config_value = COALESCE((
    SELECT string_agg(client_id, ',' ORDER BY client_id)
    FROM sys_client
    WHERE status = '0'
      AND ('password' = ANY(string_to_array(grant_type, ',')))
), ''), update_time = now()
WHERE config_key = 'sys.account.registerClientIds';
SQL
fi

register_local_migrations

run_sql /opt/certmuse/sql/99-verify.sql
