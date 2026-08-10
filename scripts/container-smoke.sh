#!/usr/bin/env bash
set -euo pipefail

project="rosies-books-smoke-$$"
compose=(docker compose --project-name "$project" --file compose.smoke.yaml)

cleanup() {
  "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [[ "${ROSIES_BOOKS_SMOKE_SKIP_BUILD:-false}" != "true" ]]; then
  ./scripts/build-production-image.sh
fi
"${compose[@]}" up --detach

for attempt in $(seq 1 60); do
  if "${compose[@]}" exec --no-TTY app \
    curl --fail --silent --show-error http://localhost:8080/q/health/ready >/dev/null 2>&1; then
    docker image inspect "${ROSIES_BOOKS_IMAGE:-rosies-books:latest}" \
      --format '{{.Config.User}}' | grep --fixed-strings --quiet 'rosies:rosies'
    echo "Container smoke check passed."
    exit 0
  fi
  sleep 2
done

"${compose[@]}" logs
echo "Timed out waiting for readiness." >&2
exit 1
