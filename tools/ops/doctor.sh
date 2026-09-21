#!/usr/bin/env bash
set -euo pipefail

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
runtime_dir="$project_root/tools/ops/local"
status=0

check_path() {
  local label=$1 path=$2
  if [ -e "$path" ]; then
    echo "[ok] $label: $path"
  else
    echo "[missing] $label: $path" >&2
    status=1
  fi
}

check_path "srv-ops runtime" "$runtime_dir/srv-ops/bin/srvctl"
check_path "RouteFlow runtime" "$runtime_dir/routeflow/tools/deployer/deployer"

if git -C "$project_root" check-ignore -q tools/ops/local/.private-check; then
  echo "[ok] tools/ops/local is ignored by Git"
else
  echo "[missing] tools/ops/local is not ignored by Git" >&2
  status=1
fi

if [ -x "$runtime_dir/srv-ops/bin/srvctl" ]; then
  "$runtime_dir/srv-ops/bin/srvctl" --help >/dev/null
  echo "[ok] srvctl starts"
fi

if [ -d "$runtime_dir/srv-ops/.srv-ops-local" ]; then
  echo "[ok] srv-ops private profile is present"
else
  echo "[notice] srv-ops private profile has not been imported"
fi

if [ -f "$runtime_dir/routeflow/tools/deployer/certmuse.yml" ] \
  && [ -f "$runtime_dir/routeflow/platform/linux/hosts/certmuse-app/host.yml" ]; then
  echo "[ok] CertMuse Deployer private config and host profile are present"
else
  echo "[notice] CertMuse Deployer private config or host profile has not been imported"
fi

if [ -d "$runtime_dir/routeflow/platform" ] && [ -d "$runtime_dir/routeflow/shared" ]; then
  echo "[ok] RouteFlow private overlay is present"
else
  echo "[notice] RouteFlow private overlay has not been installed"
fi

exit "$status"
