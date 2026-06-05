#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose down -v
docker compose up -d

echo "waiting for schema registry..."
for i in $(seq 1 90); do
  if curl -sf http://localhost:8081/subjects > /dev/null; then
    break
  fi
  if [ "$i" -eq 90 ]; then
    echo "❌ schema registry not ready after 90s" >&2
    exit 1
  fi
  sleep 1
done

./gradlew :schemas:registerAllSchemas
echo "✅ infra reset, schemas registered"
