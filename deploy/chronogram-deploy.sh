#!/usr/bin/env bash
# Server-side deploy script for Chronogram on devaidalab.unicas.it (Docker stack).
#
# Install (as root):
#   install -o root -g root -m 755 chronogram-deploy.sh /usr/local/bin/chronogram-deploy.sh
# and allow the deploy user to run it without password (visudo -f /etc/sudoers.d/chronogram):
#   deploy ALL=(root) NOPASSWD: /usr/local/bin/chronogram-deploy.sh
#
# Usage: chronogram-deploy.sh /path/to/chronogram.war
#
# Swaps the WAR under /opt/chronogram/webapps and restarts only the tomcat
# container. The mysql container and its data volume are NEVER touched: schema
# evolution happens exclusively through Flyway versioned migrations executed by
# the application itself at startup (spring.jpa.hibernate.ddl-auto=validate),
# so data survives every deploy.
set -euo pipefail

WAR_SRC="${1:?usage: chronogram-deploy.sh /path/to/chronogram.war}"
APP_DIR="${APP_DIR:-/opt/chronogram}"
COMPOSE="docker compose -f $APP_DIR/docker-compose.prod.yml"
HEALTH_URL="http://127.0.0.1:8800/chronogram/actuator/health"

[ -f "$WAR_SRC" ] || { echo "WAR not found: $WAR_SRC" >&2; exit 1; }
[ -f "$APP_DIR/docker-compose.prod.yml" ] || { echo "Missing $APP_DIR/docker-compose.prod.yml" >&2; exit 1; }

cd "$APP_DIR"

echo "==> Stopping tomcat container"
$COMPOSE stop tomcat

echo "==> Replacing webapps/chronogram.war"
mkdir -p webapps
rm -rf webapps/chronogram webapps/chronogram.war
install -m 644 "$WAR_SRC" webapps/chronogram.war

echo "==> Starting stack (mysql stays up; tomcat redeploys the WAR)"
$COMPOSE up -d

echo "==> Waiting for $HEALTH_URL"
for i in $(seq 1 90); do
    if curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"UP"'; then
        echo "==> Deploy OK ($(date -Is))"
        rm -f "$WAR_SRC"
        exit 0
    fi
    sleep 2
done

echo "==> Deploy FAILED: health check never turned UP. Recent logs:" >&2
docker logs --tail 80 chronogram-tomcat >&2 || true
exit 1
