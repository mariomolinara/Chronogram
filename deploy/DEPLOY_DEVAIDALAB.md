# Deploy di Chronogram su devaidalab.unicas.it (stack Docker)

Architettura di produzione — **tutto in container** tranne l'nginx di macchina,
già presente con certificato:

```
Internet ──HTTPS 443──> nginx di macchina (esistente, con certificato)
                          │  location /chronogram/  →  proxy_pass 127.0.0.1:8800
                          ▼
                   ┌─ Docker (/opt/chronogram) ───────────────────────────┐
                   │  tomcat  (tomcat:10.1-jre21, 8080→127.0.0.1:8800)    │
                   │    └── webapps/chronogram.war (backend + SPA)        │
                   │  mysql   (mysql:8.0, volume mysql-data — MAI toccato)│
                   └──────────────────────────────────────────────────────┘
```

Un unico WAR (`chronogram.war`) contiene **backend Spring Boot e frontend
Vue/Ionic** (build Vite con base `/chronogram/`): app e API vivono sullo stesso
origin `https://devaidalab.unicas.it/chronogram`, quindi niente CORS per il web.
Il nome del WAR determina il context path su Tomcat.

Il push su `main` fa partire `.github/workflows/deploy.yml`: build frontend →
test backend → WAR → upload via SSH → swap in `/opt/chronogram/webapps/` →
restart del solo container tomcat → health check.

**Il database non viene mai resettato**: il deploy sostituisce solo il WAR e
riavvia il container tomcat; il container mysql e il suo volume `mysql-data`
non vengono toccati. Lo schema evolve esclusivamente tramite migrazioni Flyway
versionate (`backend/src/main/resources/db/migration/V*.sql`) eseguite dall'app
all'avvio; `ddl-auto=validate` impedisce a Hibernate di modificare le tabelle.
Regole: mai modificare una migrazione già applicata (solo nuove `V<n>__*.sql`),
mai `flyway clean`, mai `docker compose down -v` (la `-v` cancella il volume!).

---

## 1. Setup una-tantum del server (root su devaidalab)

### 1.1 Docker

Se non già presente:

```bash
apt-get update && apt-get install -y docker.io docker-compose-v2
systemctl enable --now docker
```

(qualsiasi installazione recente con il plugin `docker compose` va bene).

### 1.2 Directory dell'applicazione

Copia dal repo (cartella `deploy/`) i due file di orchestrazione:

```bash
mkdir -p /opt/chronogram/webapps
cp deploy/docker-compose.prod.yml /opt/chronogram/
cp deploy/chronogram.env.example  /opt/chronogram/chronogram.env
vi /opt/chronogram/chronogram.env     # inserisci i valori reali
chmod 600 /opt/chronogram/chronogram.env
```

Obbligatori in `chronogram.env`: password MySQL, `JWT_SECRET_KEY` (≥32
caratteri, `openssl rand -base64 48`), `CORS_ALLOWED_ORIGINS`,
`APP_CANONICAL_URL`, `ADMIN_EMAIL`/`ADMIN_INITIAL_PASSWORD` (account
amministratore creato al primo avvio), credenziali mail e chiave LLM.

Primo avvio dello stack (il WAR arriverà col primo deploy; intanto parte MySQL,
che alla prima esecuzione crea database e utente dalle variabili `MYSQL_*`):

```bash
cd /opt/chronogram && docker compose -f docker-compose.prod.yml up -d
```

### 1.3 Script di deploy

```bash
install -o root -g root -m 755 deploy/chronogram-deploy.sh /usr/local/bin/chronogram-deploy.sh
```

### 1.4 Utente di deploy per GitHub Actions

```bash
useradd -m -s /bin/bash deploy
sudo -u deploy ssh-keygen -t ed25519 -N '' -f /home/deploy/.ssh/id_deploy
sudo -u deploy sh -c 'cat /home/deploy/.ssh/id_deploy.pub >> /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys'
```

Autorizza SOLO lo script di deploy via sudo (`visudo -f /etc/sudoers.d/chronogram`):

```
deploy ALL=(root) NOPASSWD: /usr/local/bin/chronogram-deploy.sh
```

La **chiave privata** `/home/deploy/.ssh/id_deploy` va copiata nel secret
GitHub `DEPLOY_SSH_KEY` (poi puoi rimuoverla dal server: serve solo a GitHub).

### 1.5 nginx (di macchina)

Aggiungi i blocchi di `deploy/nginx-chronogram.conf`:

- le due righe `limit_req_zone` in un file caricato nel contesto `http {}`
  (es. `/etc/nginx/conf.d/chronogram-zones.conf`);
- i `location /chronogram/...` **dentro** il `server { listen 443 ssl; }`
  esistente di devaidalab.unicas.it (proxy verso `http://127.0.0.1:8800`).

Poi `nginx -t && systemctl reload nginx`.

---

## 2. Configurazione GitHub (una-tantum)

Nel repository GitHub che ospita il codice (es. `mariomolinara/Chronogram`):

1. **Settings → Secrets and variables → Actions → New repository secret**:

   | Secret           | Valore                                              |
   |------------------|-----------------------------------------------------|
   | `DEPLOY_HOST`    | `devaidalab.unicas.it`                              |
   | `DEPLOY_USER`    | `deploy`                                            |
   | `DEPLOY_SSH_KEY` | contenuto integrale di `/home/deploy/.ssh/id_deploy` (chiave privata, righe `-----BEGIN/END OPENSSH PRIVATE KEY-----` comprese) |
   | `DEPLOY_PORT`    | (opzionale) porta SSH se diversa da 22              |

2. Il workflow è `.github/workflows/deploy.yml` e parte **a ogni push su
   `main`** (o manualmente da *Actions → Deploy to devaidalab → Run workflow*).

3. Il job `build` esegue anche i test backend (Testcontainers su MySQL): un
   test rosso **blocca il deploy**.

---

## 3. Primo deploy e verifica

1. Push (o merge) su `main` → segui il run in *Actions* (oppure *Run workflow*).
2. A fine run:
   - `https://devaidalab.unicas.it/chronogram/actuator/health` → `{"status":"UP"}`
   - `https://devaidalab.unicas.it/chronogram/` → app web (login).
3. Al primo avvio Flyway crea le tabelle e viene provisionato l'account admin
   (`ADMIN_EMAIL`/`ADMIN_INITIAL_PASSWORD`, cambio password obbligatorio al
   primo accesso).
4. Log applicativi sul server: `docker logs -f chronogram-tomcat`.

### Rollback

Riesegui da *Actions* il workflow di un commit precedente, oppure a mano sul
server:

```bash
sudo /usr/local/bin/chronogram-deploy.sh /percorso/chronogram-vecchio.war
```

I dati non sono coinvolti (attenzione solo a non tornare a una versione
precedente a una migrazione Flyway già applicata: l'app la segnalerebbe
all'avvio).

### Backup del database (consigliato, indipendente dal deploy)

```bash
docker exec chronogram-mysql sh -c 'mysqldump --single-transaction -uroot -p"$MYSQL_ROOT_PASSWORD" chronogram' \
  | gzip > /var/backups/chronogram-$(date +%F).sql.gz
```

### Comandi utili

```bash
cd /opt/chronogram
docker compose -f docker-compose.prod.yml ps            # stato dei container
docker compose -f docker-compose.prod.yml restart tomcat
docker compose -f docker-compose.prod.yml stop          # ferma tutto (dati salvi)
docker compose -f docker-compose.prod.yml up -d         # riparte tutto
```

---

## 4. Note per l'app Android

La stessa build usa `VITE_API_BASE_URL=https://devaidalab.unicas.it/chronogram`
(`frontend/.env.production`), quindi l'APK di release parla già col server di
produzione. La build **web** usa `npm run build:web` (base `/chronogram/`),
mentre la build per Capacitor/Android continua a usare `npm run build`
(base `/`, asset locali nel WebView).
