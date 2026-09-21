#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)
srvctl="$script_dir/local/srv-ops/bin/srvctl"
config="$script_dir/local/srv-ops/.srv-ops-local/config.yaml"
download_base_url="https://cm.jklin.me/ops-backup"
bundle_file="latest.srvops.enc"
local_file=""

usage() {
  cat <<'EOF'
Usage:
  tools/ops/import-srv-ops-backup.sh [--file <bundle-name>]
  tools/ops/import-srv-ops-backup.sh --local-file <encrypted-bundle-path>

Without --local-file, downloads the latest encrypted CertMuse srv-ops backup
from https://cm.jklin.me/ops-backup/ to the Git-ignored project temporary
directory, then restores it. This supports first-time recovery without an
existing local CertMuse profile.

Use --local-file to bootstrap a new workstation from an authorized, locally
available encrypted bundle. srvctl prompts for the bundle password; it is not
accepted by this script and is never written to disk.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --file)
      [ "$#" -ge 2 ] || { echo "--file requires a bundle name" >&2; exit 2; }
      bundle_file="$2"
      shift 2
      ;;
    --local-file)
      [ "$#" -ge 2 ] || { echo "--local-file requires a path" >&2; exit 2; }
      local_file="$2"
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

if [ -n "$local_file" ]; then
  [ "$bundle_file" = "latest.srvops.enc" ] || { echo "--file and --local-file cannot be used together" >&2; exit 2; }
  [ -f "$local_file" ] || { echo "Encrypted bundle not found: $local_file" >&2; exit 1; }
  cd "$project_dir"
  exec "$srvctl" -config "$config" local import --local-file "$local_file"
fi

case "$bundle_file" in
  */*|*\\*) echo "--file must be a bundle name, not a path" >&2; exit 2 ;;
esac

temp_dir="$script_dir/local/srv-ops/.srv-ops-local/tmp/backups"
local_copy="$temp_dir/$(basename -- "$bundle_file")"
partial_copy="$local_copy.part"
mkdir -p "$temp_dir"

cd "$project_dir"
command -v curl >/dev/null 2>&1 || { echo "curl is required for HTTPS backup download" >&2; exit 1; }
rm -f "$partial_copy"
(
  umask 077
  curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
    --output "$partial_copy" "$download_base_url/$bundle_file"
)
mv "$partial_copy" "$local_copy"

exec "$srvctl" -config "$config" local import --local-file "$local_copy"
