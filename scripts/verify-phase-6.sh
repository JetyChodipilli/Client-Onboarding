#!/usr/bin/env sh
set -eu

# Start private versioned storage and ClamAV before the live test.
# The live test needs the fresh bootstrap backend/SMTP configuration documented in CI.
# Refuse to silently skip that required scenario during a phase completion gate.
if [ "${LIVE_BACKEND:-}" != "1" ]; then
  echo "Start a fresh Phase 6 bootstrap backend using the CI browser configuration, then set LIVE_BACKEND=1." >&2
  exit 1
fi

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

cd "$project_root/backend"
./mvnw -B -ntp verify

cd "$project_root/frontend"
npm ci
npm run check
PLAYWRIGHT_HTML_OPEN=never npm run test:e2e

if command -v docker >/dev/null 2>&1; then
  cd "$project_root"
  docker compose config --quiet
  docker compose build
else
  echo "Docker is unavailable; Compose and container-image validation could not execute." >&2
  exit 1
fi
