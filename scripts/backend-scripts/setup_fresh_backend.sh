#!/bin/bash
#
# Starts a fresh backend environment from scratch.
# The Java build now happens INSIDE the Docker image (multi-stage build),
# so Maven/JDK are no longer required on the host — only Docker.

set -euo pipefail

# Move to the project root (two levels above this script)
cd "$(cd "$(dirname "$0")" && pwd)/../.." || exit 1

echo "🧹 Cleaning up existing environment (containers + volumes)..."
docker compose --env-file .env -f docker/docker-compose.yml down -v

echo "🚀 Building and starting the stack (MySQL + Spring Boot backend + Nginx)..."
docker compose --env-file .env -f docker/docker-compose.yml up --build -d

echo "✅ Backend environment started. Flyway will create/migrate the schema on first boot."
echo "   API base:  http://localhost:8080/chronogram"
echo "   Health:    http://localhost:8080/chronogram/actuator/health"
