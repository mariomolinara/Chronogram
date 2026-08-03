# Deploy di Chronogram su devaidalab.unicas.it

Architettura di produzione:

```
Internet ──HTTPS 443──> nginx (già presente, con certificato)
                          │  location /chronogram/  →  proxy_pass 127.0.0.1:8800
                          ▼
                        Tomcat 10.1 (porta 8800, systemd: chronogram-tomcat)
                          │  webapps/chronogram.war  (backend + frontend SPA)
                          ▼
                        MySQL 8 (locale, MAI toccato dal deploy)
```

Un unico WAR (`chronogram.war`) contiene **backend Spring Boot e frontend
Vue/Ionic** (build Vite con `--base=/chronogram/` copiata in
`classpath:/static`): l'app web e le API vivono sullo stesso origin
`https://devaidalab.unicas.it/chronogram`, quindi niente CORS per il web.
Il nome del WAR determina il context path su Tomcat (`chronogram.war` →
`/chronogram`).

Il push su `main` fa partire `.github/workflows/deploy.yml`: build frontend →
test backend → WAR → upload via SSH → swap in `webapps/` → health check.

**Il database non viene mai resettato**: il pipeline copia solo il WAR. Lo
schema evolve esclusivamente tramite migrazioni Flyway versionate
(`backend/src/main/resources/db/migration/V*.sql`) eseguite dall'app all'avvio;
`spring.jpa.hibernate.ddl-auto=validate` impedisce a Hibernate di modificare le
tabelle. Regole: mai modificare una migrazione già applicata (solo nuove
`V<n>__*.sql`), mai usare `flyway clean`.

---

## 1. Setup una-tantum del server (root su devaidalab)

### 1.1 Java 21 + Tomcat 10.1

Spring Boot 3.3 richiede **Tomcat ≥ 10.1** (Jakarta EE 10) e Java 21:

```bash
apt-get update && apt-get install -y openjdk-21-jdk-headless
useradd -r -m -d /opt/tomcat -s /bin/false tomcat

TOMCAT_VER=10.1.34   # o ultima 10.1.x
curl -fsSL "https://dlcdn.apache.org/tomcat/tomcat-10/v${TOMCAT_VER}/bin/apache-tomcat-${TOMCAT_VER}.tar.gz" \
  | tar xz -C /opt
mv /opt/apache-tomcat-${TOMCAT_VER}/* /opt/tomcat/ && rmdir /opt/apache-tomcat-${TOMCAT_VER}
rm -rf /opt/tomcat/webapps/*        # niente app di default (manager, docs…)
chown -R tomcat:tomcat /opt/tomcat
```

### 1.2 Porta 8800

In `/opt/tomcat/conf/server.xml` porta il connettore HTTP a **8800** e legalo
solo a localhost (ci arriva solo nginx):

```xml
<Connector port="8800" address="127.0.0.1" protocol="HTTP/1.1"
           connectionTimeout="20000" maxPostSize="2097152" />
```

(rimuovi/commenta l'eventuale connettore AJP 8009; la shutdown port 8005 può
restare).

### 1.3 MySQL

Se non già presente: `apt-get install -y mysql-server`. Poi crea database e
utente (una sola volta — i dati poi restano per sempre):

```sql
CREATE DATABASE chronogram CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'chronouser'@'localhost' IDENTIFIED BY '<password-robusta>';
GRANT ALL PRIVILEGES ON chronogram.* TO 'chronouser'@'localhost';
FLUSH PRIVILEGES;
```

Le tabelle le crea Flyway al primo avvio dell'app (migrazioni `V1..Vn`).

### 1.4 Variabili d'ambiente dell'app

```bash
mkdir -p /opt/chronogram
cp deploy/chronogram.env.example /opt/chronogram/chronogram.env
vi /opt/chronogram/chronogram.env      # inserisci segreti reali
chown root:tomcat /opt/chronogram/chronogram.env
chmod 640 /opt/chronogram/chronogram.env
```

Obbligatori in profilo `prod`: `JWT_SECRET_KEY` (≥32 caratteri,
`openssl rand -base64 48`), `CORS_ALLOWED_ORIGINS`, `APP_CANONICAL_URL`,
credenziali DB. `CORS_ALLOWED_ORIGINS` deve includere anche gli origin
dell'app Android (`https://localhost`, `capacitor://localhost`).

### 1.5 Servizio systemd + script di deploy

```bash
cp deploy/chronogram-tomcat.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now chronogram-tomcat

install -o root -g root -m 755 deploy/chronogram-deploy.sh /usr/local/bin/chronogram-deploy.sh
```

### 1.6 Utente di deploy per GitHub Actions

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

### 1.7 nginx

Aggiungi i blocchi di `deploy/nginx-chronogram.conf`:

- le due righe `limit_req_zone` in un file caricato nel contesto `http {}`
  (es. `/etc/nginx/conf.d/chronogram-zones.conf`);
- i `location /chronogram/...` **dentro** il `server { listen 443 ssl; }`
  esistente di devaidalab.unicas.it.

Poi `nginx -t && systemctl reload nginx`.

---

## 2. Configurazione GitHub (una-tantum)

Nel repository GitHub che ospita il codice (quello su cui fai push,
es. `mariomolinara/Chronogram`):

1. **Settings → Secrets and variables → Actions → New repository secret**:

   | Secret           | Valore                                              |
   |------------------|-----------------------------------------------------|
   | `DEPLOY_HOST`    | `devaidalab.unicas.it`                              |
   | `DEPLOY_USER`    | `deploy`                                            |
   | `DEPLOY_SSH_KEY` | contenuto integrale di `/home/deploy/.ssh/id_deploy` (chiave privata, righe `-----BEGIN/END OPENSSH PRIVATE KEY-----` comprese) |
   | `DEPLOY_PORT`    | (opzionale) porta SSH se diversa da 22              |

2. Il workflow è `.github/workflows/deploy.yml` e parte **a ogni push su
   `main`** (o manualmente da *Actions → Deploy to devaidalab → Run workflow*).
   Quindi il flusso operativo è: merge/push su `main` → deploy automatico.

3. Se Actions è disabilitato sul repo: *Settings → Actions → General →
   Allow all actions*.

Il job `build` esegue anche i test backend (Testcontainers su MySQL): un test
rosso **blocca il deploy**.

---

## 3. Primo deploy e verifica

1. Push (o merge) su `main` → segui il run in *Actions*.
2. A fine run:
   - `https://devaidalab.unicas.it/chronogram/actuator/health` → `{"status":"UP"}`
   - `https://devaidalab.unicas.it/chronogram/` → app web (login).
3. Sul server: `journalctl -u chronogram-tomcat -f` per i log applicativi.

### Rollback

Riesegui da *Actions* il workflow di un commit precedente (Run workflow sul
commit desiderato), oppure a mano sul server:

```bash
sudo /usr/local/bin/chronogram-deploy.sh /percorso/chronogram-vecchio.war
```

I dati non sono coinvolti: il rollback del WAR non tocca MySQL (attenzione solo
a non tornare a una versione **precedente** a una migrazione Flyway già
applicata: in quel caso l'app segnala all'avvio la migrazione sconosciuta).

### Backup del database (consigliato, indipendente dal deploy)

```bash
mysqldump --single-transaction chronogram | gzip > /var/backups/chronogram-$(date +%F).sql.gz
```

---

## 4. Note per l'app Android

La stessa build usa `VITE_API_BASE_URL=https://devaidalab.unicas.it/chronogram`
(`frontend/.env.production`), quindi l'APK di release parla già col server di
produzione. Ricorda che la build **web** usa `npm run build:web` (base
`/chronogram/`), mentre la build per Capacitor/Android continua a usare
`npm run build` (base `/`, asset locali nel WebView).
