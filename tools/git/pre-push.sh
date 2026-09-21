#!/usr/bin/env sh

set -u

run_check() {
  name="$1"
  shift
  echo "[pre-push] Starting $name..."
  if "$@"; then
    echo "[pre-push] Passed $name."
    return 0
  fi
  echo "[pre-push] Failed $name." >&2
  return 1
}

run_check "admin frontend" pnpm run check:admin &
admin_pid=$!
run_check "student frontend" pnpm run check:student &
student_pid=$!
run_check "CertMuse backend" ./backend/mvnw -f ./backend/pom.xml -pl ruoyi-modules/ruoyi-certmuse -am -Dmaven.test.skip=false test &
backend_pid=$!

status=0
wait "$admin_pid" || status=1
wait "$student_pid" || status=1
wait "$backend_pid" || status=1

run_check "Docker Compose configuration" docker compose -f infra/docker/compose.yml config --quiet || status=1

if [ "$status" -ne 0 ]; then
  echo "[pre-push] Push blocked because one or more checks failed." >&2
  exit "$status"
fi

echo "[pre-push] All tests, frontend builds, and configuration checks passed."
