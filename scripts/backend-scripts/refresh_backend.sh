#!/bin/bash
#
# Rebuilds and restarts ONLY the backend service after code changes,
# leaving the MySQL data volume intact.

set -euo pipefail

cd "$(cd "$(dirname "$0")" && pwd)/../.." || exit 1

echo "🔄 Rebuilding and restarting the backend..."
docker compose --env-file .env -f docker/docker-compose.yml up -d --build backend

echo "✅ Backend refreshed."
