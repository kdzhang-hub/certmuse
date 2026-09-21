#!/bin/sh
set -eu

database="${1:-certmuse}"
migration_dir="${2:-/app/certmuse/backend/migrations}"
state_dir="${3:-/var/lib/certmuse-deployer/migrations}"

install -d -o root -g root -m 0700 "$state_dir"

found=0
for migration in "$migration_dir"/*.sql; do
  [ -f "$migration" ] || continue
  found=1

  name="$(basename "$migration")"
  checksum="$(sha256sum "$migration" | awk '{print $1}')"
  marker="$state_dir/$name.sha256"

  if [ -f "$marker" ]; then
    recorded="$(cat "$marker")"
    if [ "$recorded" != "$checksum" ]; then
      echo "[ERROR] Applied migration checksum changed: $name" >&2
      exit 1
    fi
    echo "[SKIP] $name"
    continue
  fi

  echo "[APPLY] $name"
  sudo -u postgres psql -v ON_ERROR_STOP=1 -d "$database" -f "$migration"
  marker_tmp="$marker.tmp.$$"
  printf '%s\n' "$checksum" >"$marker_tmp"
  chmod 0600 "$marker_tmp"
  mv "$marker_tmp" "$marker"
  echo "[OK] $name"
done

if [ "$found" -eq 0 ]; then
  echo "[INFO] No PostgreSQL migrations found in $migration_dir"
fi
