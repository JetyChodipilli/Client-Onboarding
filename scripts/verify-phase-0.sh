#!/usr/bin/env sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

cd "$project_root/backend"
./mvnw -B -ntp verify

cd "$project_root/frontend"
npm ci
npm run check

if command -v docker >/dev/null 2>&1; then
  cd "$project_root"
  docker compose config --quiet
else
  echo "Docker is unavailable; Compose runtime validation was not executed." >&2
fi

