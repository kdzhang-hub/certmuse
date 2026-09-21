#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)
srvctl="$script_dir/local/srv-ops/bin/srvctl"
config="$script_dir/local/srv-ops/.srv-ops-local/config.yaml"
env_file="$script_dir/local/srv-ops/.srv-ops-local/env/passwords.env"
server="certmuse-app"
remote_dir="/app/certmuse-backups/srv-ops"
bundle_file="certmuse-srv-ops-$(date -u +%Y%m%dT%H%M%SZ).tar.gz.enc"

usage() {
  cat <<'EOF'
Usage: tools/ops/backup-srv-ops-profile.sh [--file <bundle-name>]

Creates an encrypted backup in the Git-ignored project temporary directory:
  tools/ops/local/srv-ops/.srv-ops-local/tmp/

Then uploads the same archive to certmuse-app:/app/certmuse-backups/srv-ops and
updates latest.srvops.enc. The directory is read-only available on the company
intranet at https://cm.jklin.me/ops-backup/. srvctl prompts for the encryption
password; this script does not accept, print, or persist that password.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --file)
      [ "$#" -ge 2 ] || { echo "--file requires a bundle name" >&2; exit 2; }
      bundle_file="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

[ -x "$srvctl" ] || { echo "srvctl is not installed; run tools/ops/certmusectl doctor" >&2; exit 1; }
[ -f "$config" ] || { echo "No local CertMuse profile; restore it before creating a backup." >&2; exit 1; }
case "$bundle_file" in
  */*|*\\*) echo "--file must be a bundle name, not a path" >&2; exit 2 ;;
esac

cd "$project_dir"
exec "$srvctl" \
  -config "$config" \
  -env "$env_file" \
  local export \
  --server "$server" \
  --remote-dir "$remote_dir" \
  --file "$bundle_file"
