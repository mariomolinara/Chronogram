#!/usr/bin/env bash
# Server-side deploy script for Chronogram on devaidalab.unicas.it.
#
# Install (as root):
#   install -o root -g root -m 755 chronogram-deploy.sh /usr/local/bin/chronogram-deploy.sh
# and allow the deploy user to run it without password (visudo -f /etc/sudoers.d/chronogram):
#   deploy ALL=(root) NOPASSWD: /usr/local/bin/chronogram-deploy.sh
#
# Usage: chronogram-deploy.sh /path/to/chronogram.war
#
# Swaps the WAR under Tomcat's webapps and waits for the Spring actuator health
# endpoint. It NEVER touches MySQL: schema evolution happens exclusively through
# Flyway versioned migrations executed by the application itself at startup
# (spring.jpa.hibernate.ddl-auto=validate), so data survives every deploy.
set -euo pipefail

WAR_SRC="${1:?usage: chronogram-deploy.sh /path/to/chronogram.war}"
CATALINA_BASE="${CATALINA_BASE:-/opt/tomcat}"
SERVICE="${SERVICE:-chronogram-tomcat}"
HEALTH_URL="http://127.0.0.1:8800/chronogram/actuator/health"
TOMCAT_USER="${TOMCAT_USER:-tomcat}"

[ -f "$WAR_SRC" ] || { echo "WAR not found: $WAR_SRC" >&2; exit 1; }

echo "==> Stopping $SERVICE"
systemctl stop "$SERVICE"

echo "==> Replacing webapps/chronogram.war"
rm -rf "$CATALINA_BASE/webapps/chronogram" "$CATALINA_BASE/webapps/chronogram.war"
install -o "$TOMCAT_USER" -g "$TOMCAT_USER" -m 644 "$WAR_SRC" "$CATALINA_BASE/webapps/chronogram.war"

echo "==> Starting $SERVICE"
systemctl start "$SERVICE"

echo "==> Waiting for $HEALTH_URL"
for i in $(seq 1 60); do
    if curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"UP"'; then
        echo "==> Deploy OK ($(date -Is))"
        rm -f "$WAR_SRC"
        exit 0
    fi
    sleep 2
done

echo "==> Deploy FAILED: health check never turned UP. Recent logs:" >&2
journalctl -u "$SERVICE" -n 50 --no-pager >&2 || true
exit 1
