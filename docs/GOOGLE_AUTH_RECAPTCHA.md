# Accesso con Google e reCAPTCHA v3 — guida alla configurazione

Entrambe le funzionalità sono **spente finché non vengono configurate le
chiavi**: senza `VITE_GOOGLE_CLIENT_ID` il bottone Google non compare, senza
`RECAPTCHA_SECRET_KEY` il backend non verifica nulla. Sviluppo e test locali
funzionano quindi senza alcun account Google.

## 1. Accesso con Google (login e registrazione)

### Come funziona

- **Web**: la pagina di login e quella di registrazione mostrano il bottone
  ufficiale di Google Identity Services. Al click Google restituisce un **ID
  token** firmato.
- **Android**: il bottone "Continue with Google" usa il Credential Manager
  nativo (plugin `@capgo/capacitor-social-login`), che restituisce lo stesso
  tipo di ID token, emesso per il client **web**.
- In entrambi i casi il token va a `POST /api/auth/google`; il backend lo
  verifica con Google (`tokeninfo`), controlla che l'`aud` sia uno dei nostri
  client ID e che l'email sia verificata, poi:
  - se esiste un account con quel Google subject → login;
  - se esiste un account locale con la stessa email → lo collega e fa login;
  - altrimenti **crea l'account** (nome e cognome dal token, nessuna password
    locale) applicando la solita `RegistrationPolicy`: fuori dai domini
    auto-approvati l'account nasce PENDING e l'utente vede il messaggio di
    attesa approvazione.

Un account nato da Google non ha password locale: può impostarne una col
flusso "Forgot password". Il login classico su quell'email risponde "Invalid
credentials" senza rivelare che l'account esiste.

### Setup in Google Cloud Console

1. Progetto su <https://console.cloud.google.com> → menu **API e servizi →
   Google Auth Platform** (link diretto:
   <https://console.cloud.google.com/auth/overview>). È la sezione che ha
   sostituito la vecchia "OAuth consent screen" — NON sta nella Libreria API.
   Configurare **Branding** (nome app, email di contatto, logo) e **Pubblico**
   (tipo utente *Esterno*; finché l'app resta in stato "Test" possono accedere
   solo gli utenti di test elencati qui — pubblicarla quando si va in
   produzione).
2. **Google Auth Platform → Client → Crea client**
   (<https://console.cloud.google.com/auth/clients>; i client compaiono anche
   in API e servizi → Credenziali):
   - **Web application**: aggiungere fra le *Authorized JavaScript origins*
     `https://devaidalab.unicas.it` (e `http://localhost:5173` per lo sviluppo).
     Non servono redirect URI (flusso a ID token).
     → questo è il **client ID da usare ovunque**.
   - **Android**: crearne uno con package name `it.unicas.aidalab.chronogram`
     e l'impronta **SHA-1** del certificato di firma (debug e release/Play
     App Signing: `gradlew signingReport` oppure Play Console → App integrity).
     Questo client non produce un ID proprio da configurare: serve a Google
     per autorizzare l'app Android a chiedere token per il client web.
3. Configurare:
   - frontend: `VITE_GOOGLE_CLIENT_ID=<web client id>` in
     `frontend/.env.production` (e `.env.development` per i test locali);
   - backend: `GOOGLE_CLIENT_IDS=<web client id>` nel `.env` di produzione.
4. Rebuild del frontend e riavvio del backend.

> L'`appId` Capacitor è `it.unicas.aidalab.chronogram`: un eventuale vecchio
> `google-services.json` con `it.unicas.chronogram` non è valido (e per il
> Credential Manager non serve alcun `google-services.json`).

## 2. reCAPTCHA v3 sulla registrazione

### Come funziona

reCAPTCHA v3 è invisibile: nessun "seleziona i semafori", solo un punteggio
0–1 calcolato da Google. Al submit del form la pagina chiede un token per
l'azione `register` e lo mette nel payload; il backend lo verifica con la
secret key (`siteverify`) e rifiuta `success=false`, azione diversa da
`register` o punteggio sotto `RECAPTCHA_MIN_SCORE` (default 0.5). Se Google è
irraggiungibile la registrazione fallisce chiusa (meglio un ritentativo che
una finestra senza protezione). La registrazione via Google non passa dal
captcha: l'OAuth di Google è già una barriera anti-bot.

Il badge fisso di reCAPTCHA è nascosto via CSS, come consentito da Google,
perché l'attribuzione ("This site is protected by reCAPTCHA…") compare nel
form di registrazione.

### Setup

1. <https://www.google.com/recaptcha/admin> → nuovo sito, tipo
   **reCAPTCHA v3**.
2. Domini: `devaidalab.unicas.it` **e `localhost`** — quest'ultimo è
   indispensabile perché la WebView dell'app Android ha origine
   `https://localhost`.
3. Configurare:
   - frontend: `VITE_RECAPTCHA_SITE_KEY=<site key>` (pubblica);
   - backend: `RECAPTCHA_SECRET_KEY=<secret key>` (segreta), eventualmente
     `RECAPTCHA_MIN_SCORE`.
4. Rebuild del frontend e riavvio del backend. La protezione è attiva solo con
   **entrambe** le chiavi configurate.

## 3. Variabili riassuntive

| Dove | Variabile | Contenuto |
|---|---|---|
| `frontend/.env.*` | `VITE_GOOGLE_CLIENT_ID` | client ID OAuth **web** (pubblico) |
| `frontend/.env.*` | `VITE_RECAPTCHA_SITE_KEY` | site key reCAPTCHA v3 (pubblica) |
| env backend | `GOOGLE_CLIENT_IDS` | stesso client ID web (lista, virgole) |
| env backend | `RECAPTCHA_SECRET_KEY` | secret key reCAPTCHA v3 |
| env backend | `RECAPTCHA_MIN_SCORE` | soglia punteggio, default `0.5` |

L'"env backend" dipende da dove gira il backend:

- **produzione (devaidalab)**: `/opt/chronogram/chronogram.env` sul server
  (template: `deploy/chronogram.env.example`). Le variabili di `env_file` sono
  iniettate **alla creazione** del container, non al riavvio: dopo la modifica
  serve ricrearlo —

  ```bash
  cd /opt/chronogram
  docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps tomcat
  ```

- **locale**: il file `.env` nella root del repository (template:
  `.env.example`), oppure normali variabili d'ambiente del processo.

## 4. Note su schema DB e migrazione

La migrazione `V5__google_oauth.sql` aggiunge a `user_auth`:

- `auth_provider` (`LOCAL`/`GOOGLE`) — origine dell'account;
- `google_subject` (claim `sub`, univoco) — collegamento all'identità Google;
- `password_hash` diventa nullable (account Google senza password locale).

Nessun backfill necessario: gli account esistenti restano `LOCAL`.

## 5. Difese anti-bot complessive sulla registrazione

- reCAPTCHA v3 verificato server-side (questa guida);
- approvazione amministrativa per i domini non fidati (`RegistrationPolicy`):
  anche un bot che superasse il captcha finisce PENDING;
- rate-limiting per IP a livello nginx (già previsto per il reset password:
  estendere la stessa `limit_req` a `/chronogram/api/auth/register` è la
  naturale terza gamba).
