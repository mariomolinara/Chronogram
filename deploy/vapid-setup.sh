#!/usr/bin/env bash
# Setup una-tantum delle chiavi VAPID su devaidalab (vedi DEPLOY_DEVAIDALAB.md).
#
# Genera la coppia di chiavi Web Push, la scrive in /opt/chronogram/chronogram.env
# e ricrea il SOLO container tomcat (le variabili di env_file vengono iniettate
# alla creazione del container, non al riavvio). Idempotente: se una chiave
# pubblica e' gia' presente non rigenera nulla, perche' rigenerare la coppia
# invaliderebbe tutte le subscription gia' registrate dai browser.
#
# Va eseguito come root sul server:  sudo bash /tmp/vapid-setup.sh
set -euo pipefail

ENV_FILE=/opt/chronogram/chronogram.env
COMPOSE="docker compose -f /opt/chronogram/docker-compose.prod.yml"

[ -f "$ENV_FILE" ] || { echo "ERRORE: $ENV_FILE non trovato"; exit 1; }

current_pub=$(grep -E '^VAPID_PUBLIC_KEY=' "$ENV_FILE" | head -1 | cut -d= -f2- || true)
if [ -n "${current_pub}" ]; then
    echo "Chiavi VAPID gia' presenti (public: ${current_pub:0:12}...): non rigenero."
else
    # Il server non ha Node: si usa l'immagine node ufficiale via Docker, gia'
    # presente sulla macchina per lo stack dell'applicazione.
    echo "Genero la coppia VAPID (docker run node:20-alpine)..."
    out=$(docker run --rm node:20-alpine npx -y web-push generate-vapid-keys)
    pub=$(printf '%s\n' "$out" | awk '/Public Key:/{getline; gsub(/[\r ]/,""); print; exit}')
    priv=$(printf '%s\n' "$out" | awk '/Private Key:/{getline; gsub(/[\r ]/,""); print; exit}')
    [ -n "$pub" ] && [ -n "$priv" ] || { echo "ERRORE: output inatteso da web-push"; exit 1; }

    # Sostituisce la riga se esiste (il file deriva da chronogram.env.example),
    # altrimenti la appende (file creato prima che la feature esistesse).
    set_var() {
        local name=$1 value=$2
        if grep -qE "^${name}=" "$ENV_FILE"; then
            sed -i "s#^${name}=.*#${name}=${value}#" "$ENV_FILE"
        else
            printf '%s=%s\n' "$name" "$value" >> "$ENV_FILE"
        fi
    }
    set_var VAPID_PUBLIC_KEY "$pub"
    set_var VAPID_PRIVATE_KEY "$priv"
    grep -qE '^VAPID_SUBJECT=.+' "$ENV_FILE" || set_var VAPID_SUBJECT "mailto:m.molinara@unicas.it"
    chmod 600 "$ENV_FILE"
    # La chiave privata non viene mai stampata: vive solo in chronogram.env.
    echo "Chiavi scritte in $ENV_FILE (public: ${pub:0:12}...)."
fi

echo "Ricreo il solo container tomcat (mysql e il suo volume non vengono toccati)..."
$COMPOSE up -d --force-recreate --no-deps tomcat

echo "Attendo l'avvio dell'applicazione (max 120s)..."
for _ in $(seq 1 60); do
    if curl -fsS http://127.0.0.1:8800/chronogram/actuator/health 2>/dev/null | grep -q '"UP"'; then
        echo
        echo "OK: applicazione UP."
        printf 'VAPID nel container (primi 12 caratteri): '
        docker exec chronogram-tomcat printenv VAPID_PUBLIC_KEY | cut -c1-12
        exit 0
    fi
    sleep 2
done

echo "ERRORE: health check non UP dopo 120s. Vedi: docker logs --tail 100 chronogram-tomcat"
exit 1
