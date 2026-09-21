#!/usr/bin/env bash
set -euo pipefail
umask 077

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)
srvctl="$script_dir/local/srv-ops/bin/srvctl"
config="$script_dir/local/srv-ops/.srv-ops-local/config.yaml"
env_file="$script_dir/local/srv-ops/.srv-ops-local/env/passwords.env"
server="certmuse-app"
remote_root="/app/certmuse-references"

usage() {
  cat <<'EOF'
Usage:
  tools/ops/sync-reference-material.sh push <source|work|archive> <relative-file>
  tools/ops/sync-reference-material.sh pull <source|work|archive> <relative-file>

Transfers exactly one reference-material file through the CertMuse local srvctl.
The local file is under docs/references/<scope>/; the remote counterpart is
/app/certmuse-references/<scope>/. Application releases and MinIO import files
must not use this command.
EOF
}

operation=${1:-}
scope=${2:-}
relative_file=${3:-}
[ "$#" -eq 3 ] || { usage >&2; exit 2; }
case "$operation" in push|pull) ;; *) usage >&2; exit 2 ;; esac
case "$scope" in source|work|archive) ;; *) usage >&2; exit 2 ;; esac
case "/$relative_file/" in
  *'/../'*|*'//'|'/./'*|*'\\'*) echo "relative-file must stay below its selected scope" >&2; exit 2 ;;
esac
[ -n "$relative_file" ] && [ "${relative_file#/}" = "$relative_file" ] || {
  echo "relative-file must be a non-empty relative path" >&2
  exit 2
}
[ -x "$srvctl" ] || { echo "srvctl is not installed; run tools/ops/certmusectl doctor" >&2; exit 1; }
[ -f "$config" ] || { echo "No local CertMuse srv-ops profile is available" >&2; exit 1; }

local_path="$project_dir/docs/references/$scope/$relative_file"
remote_path="$remote_root/$scope/$relative_file"
cd "$project_dir"

if [ "$operation" = push ]; then
  [ -f "$local_path" ] || { echo "Local regular file not found: $local_path" >&2; exit 1; }
  "$srvctl" -config "$config" -env "$env_file" policy check "$server" --write "$remote_path"
  exec "$srvctl" -config "$config" -env "$env_file" upload "$server" "$local_path" "$remote_path"
fi

"$srvctl" -config "$config" -env "$env_file" policy check "$server" --read "$remote_path"
mkdir -p "$(dirname -- "$local_path")"
exec "$srvctl" -config "$config" -env "$env_file" download "$server" "$remote_path" "$local_path"
