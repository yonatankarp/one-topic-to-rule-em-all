#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose down -v
docker compose up -d

echo "waiting for schema registry..."
until curl -sf http://localhost:8081/subjects > /dev/null; do sleep 1; done

./gradlew :schemas:registerAllSchemas
echo "✅ infra reset, schemas registered"
