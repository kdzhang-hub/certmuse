#!/bin/bash
set -Eeuo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Run this script as root: sudo bash $0" >&2
  exit 1
fi

echo "=== PostgreSQL cluster ==="
pg_lsclusters

echo "=== Server settings ==="
runuser -u postgres -- psql -X -At <<'SQL'
SELECT 'server_version=' || current_setting('server_version');
SELECT 'listen_addresses=' || current_setting('listen_addresses');
SELECT 'port=' || current_setting('port');
SELECT 'ssl=' || current_setting('ssl');
SELECT 'config_file=' || current_setting('config_file');
SELECT 'hba_file=' || current_setting('hba_file');
SQL

echo "=== Databases ==="
runuser -u postgres -- psql -X -P pager=off -c \
  "SELECT datname, pg_get_userbyid(datdba) AS owner,
          pg_size_pretty(pg_database_size(datname)) AS size,
          datallowconn
   FROM pg_database
   ORDER BY datname;"

echo "=== Login roles (no passwords) ==="
runuser -u postgres -- psql -X -P pager=off -c \
  "SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolcanlogin
   FROM pg_roles
   WHERE rolcanlogin
   ORDER BY rolname;"

echo "=== Effective HBA rules ==="
runuser -u postgres -- psql -X -P pager=off -c \
  "SELECT rule_number, type, database, user_name, address, auth_method, error
   FROM pg_hba_file_rules
   ORDER BY rule_number;"

echo "=== Non-secret CertMuse database settings ==="
if [ -r /etc/certmuse/runtime.env ]; then
  grep -E '^(CERTMUSE_DB_URL|CERTMUSE_DB_USERNAME)=' /etc/certmuse/runtime.env || true
else
  echo "/etc/certmuse/runtime.env is not readable."
fi
