#!/usr/bin/env bash
set -euo pipefail

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
lock_file="$project_root/infra/ops/agent-tools.lock"
runtime_dir="$project_root/tools/ops/local"

# The lock file is maintained as shell-compatible KEY=VALUE assignments.
# shellcheck source=/dev/null
. "$lock_file"

os=$(uname -s)
arch=$(uname -m)
case "$os/$arch" in
  Darwin/arm64)
    platform_path="darwin-arm64"
    srv_archive="$SRV_OPS_DARWIN_ARM64_ARCHIVE"
    srv_sha="$SRV_OPS_DARWIN_ARM64_SHA256"
    deployer_archive="$ROUTEFLOW_DARWIN_ARM64_ARCHIVE"
    deployer_sha="$ROUTEFLOW_DARWIN_ARM64_SHA256"
    ;;
  Linux/x86_64)
    platform_path="linux-amd64"
    srv_archive="$SRV_OPS_LINUX_AMD64_ARCHIVE"
    srv_sha="$SRV_OPS_LINUX_AMD64_SHA256"
    deployer_archive="$ROUTEFLOW_LINUX_AMD64_ARCHIVE"
    deployer_sha="$ROUTEFLOW_LINUX_AMD64_SHA256"
    ;;
  *)
    echo "Unsupported platform: $os/$arch" >&2
    exit 2
    ;;
esac

sha256_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    sha256sum "$1" | awk '{print $1}'
  fi
}

download_and_extract() {
  local relative_path=$1 archive=$2 expected_sha=$3
  local downloaded="$runtime_dir/downloads/$archive"
  local actual_sha

  mkdir -p "$runtime_dir/downloads"
  curl --fail --location --silent --show-error \
    "$AGENT_TOOLS_BASE_URL/$relative_path/$AGENT_TOOLS_RELEASE/$archive" \
    --output "$downloaded"
  actual_sha=$(sha256_file "$downloaded")
  if [ "$actual_sha" != "$expected_sha" ]; then
    echo "SHA-256 mismatch for $archive" >&2
    rm -f "$downloaded"
    exit 1
  fi
  tar -xzf "$downloaded" -C "$runtime_dir"
}

download_and_extract "srv-ops" "$srv_archive" "$srv_sha"
download_and_extract "routeflow-deployer" "$deployer_archive" "$deployer_sha"

echo "Installed public agent tools for $platform_path under $runtime_dir"
echo "Next: import an authorized srv-ops profile and RouteFlow private overlay into that ignored directory."
