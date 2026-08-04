<p align="center">
  <img src="docs/Logo.png" alt="Chronogram Title" width="400"/>
</p>






<p align="center" style="margin-top: 40px;">
    <img src="https://img.shields.io/github/stars/bonoboprog/Chronogram?style=plastic&color=FF2E2E&labelColor=2d0052" alt="GitHub stars">         <!-- Rosso -->
    <img src="https://img.shields.io/github/contributors/bonoboprog/Chronogram?style=plastic&color=FF7F00&labelColor=2d0052" alt="GitHub contributors"> <!-- Arancione -->
    <img src="https://img.shields.io/github/repo-size/bonoboprog/Chronogram?style=plastic&color=FFFF33&labelColor=2d0052" alt="GitHub repo size">  <!-- Giallo -->
    <img src="https://img.shields.io/github/license/bonoboprog/Chronogram?style=plastic&color=33FF33&labelColor=2d0052" alt="GitHub License">     <!-- Verde -->
    <img src="https://img.shields.io/badge/API%20Status-stable-33CCFF?style=plastic&labelColor=2d0052" alt="API Status">                          <!-- Azzurro -->
    <img src="https://img.shields.io/badge/Platform-Android-6666FF?style=plastic&labelColor=2d0052" alt="Platform">                               <!-- Blu -->
    <img src="https://img.shields.io/badge/Version-2.0.0-CC66FF?style=plastic&labelColor=2d0052" alt="Version">                                   <!-- Viola -->
</p>




The goal of this project is to develop an Android application that allows users to record their daily activities. The app features a main page for real-time activity tracking and lets users review logs from previous days. The design supports both real-time and retrospective logging, making the app suitable for personal use as well as for researchers or organizations conducting time-use studies.

---

## 🚀 Features

* Secure user registration and login (JWT + BCrypt)
* Brute-force protection (temporary account lockout)
* Password reset via email (selector/verifier tokens)
* Activity tracking with categories
* LLM-assisted activity extraction from free text (signed-in users only)
* Admin dashboard with participation metrics and CSV export
* MySQL persistence with versioned schema (Flyway)

---

## 🛠️ Tech Stack

| Layer    | Technology |
| -------- | ---------- |
| Backend  | Spring Boot 3 (Java 21) · Spring Data JPA/Hibernate · Spring Security (JWT) · Flyway |
| Database | MySQL 8 |
| Proxy    | Nginx |
| Frontend | HTML, SCSS, TypeScript, Vue, Ionic, Capacitor (Android) |
| LLM      | Any OpenAI-compatible provider (default: [regolo.ai](https://regolo.ai)) |

> The backend was migrated from Struts 2 / Tomcat WAR to Spring Boot. Details in [`backend/MIGRATION.md`](backend/MIGRATION.md). The HTTP API is unchanged.

---

## 🏗️ Architecture

```
              ┌─────────┐      ┌──────────────────────────┐      ┌─────────┐
  client ───▶ │  nginx  │ ───▶ │  backend (Spring Boot,   │ ───▶ │  MySQL  │
  (80/443)    │ (proxy) │      │  embedded Tomcat :8080)  │      │  :3306  │
              └─────────┘      └──────────────────────────┘      └─────────┘
```

All three run as Docker containers defined in `docker/docker-compose.yml`.
The backend is served under the context path **`/chronogram`**.

---

## ⚙️ Prerequisites

* **Docker** and the **Docker Compose** plugin. Nothing else is required on the host —
  the backend is compiled *inside* the Docker image (multi-stage build).
* For the frontend only: **Node.js 22** and the **Ionic CLI** (`npm i -g @ionic/cli`).

---

## 🔐 Configuration

Copy the template and fill in real values:

```bash
cp .env.example .env
```

Key variables in `.env`:

| Variable | Description |
| --- | --- |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` | Database passwords |
| `MYSQL_USER`, `MYSQL_DATABASE` | Database user / schema name |
| `JWT_SECRET_KEY` | HMAC key for JWTs — **must be ≥ 32 chars** (`openssl rand -base64 48`) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowlist of front-end origins (never `*`) |
| `APP_CANONICAL_URL` | Public base URL used in password-reset links |
| `LLM_API_URL`, `LLM_DEFAULT_MODEL`, `LLM_API_KEY` | LLM provider (default regolo.ai; key from https://dashboard.regolo.ai) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD` | SMTP settings (Gmail: use an App Password) |
| `ADMIN_EMAIL`, `ADMIN_INITIAL_PASSWORD` | Built-in administrator, created on first boot (see [Admin area](#️-admin-area)) |
| `STATS_ACTIVE_WINDOW_DAYS`, `STATS_REGULAR_WINDOW_DAYS`, `STATS_DISTRIBUTION_DAYS` | Dashboard metric windows (defaults 10 / 7 / 30) |

> Save `.env` with **LF** (Unix) line endings.

---

## ▶️ Run (Docker)

From the project root:

```bash
docker compose --env-file .env -f docker/docker-compose.yml up --build -d
```

Or use the helper script (stops old containers, rebuilds, starts fresh):

```bash
scripts/backend-scripts/setup_fresh_backend.sh
```

The database schema is created and migrated automatically by Flyway on first boot.

* API base: `http://localhost:8080/chronogram` (direct) or `http://localhost/chronogram` (via nginx)
* Health:   `http://localhost:8080/chronogram/actuator/health`

Rebuild only the backend after code changes:

```bash
scripts/backend-scripts/refresh_backend.sh
```

Stop everything:

```bash
docker compose -f docker/docker-compose.yml down        # keep data
docker compose -f docker/docker-compose.yml down -v     # also wipe the DB volume
```

---

## 📱 Frontend

```bash
cd frontend
npm install
ionic build
ionic serve            # dev server at http://localhost:8100
```

Set `frontend/.env` → `VITE_API_BASE_URL` to the backend URL (see the file's comments).

---

## 🌐 Deployment (public machine)

Run the same Docker stack on a public server (VM/VPS), with **nginx as the public
entrypoint** proxying to the backend's embedded Tomcat:

1. Point a DNS **A record** for your domain at the server's public IP.
2. In `.env` set the public URLs:
   ```env
   CORS_ALLOWED_ORIGINS=https://your-domain.example
   APP_CANONICAL_URL=https://your-domain.example
   ```
3. Obtain TLS certificates (e.g. Let's Encrypt / certbot), place `fullchain.pem`
   and `privkey.pem` under `docker/nginx/certs/`, then:
   * uncomment the `certs` volume in `docker/docker-compose.yml`,
   * uncomment the HTTPS (443) server block in `docker/nginx/conf.d/default.conf`
     (and set `server_name` to your domain).
4. Build the frontend with `VITE_API_BASE_URL=https://your-domain.example/chronogram`.
5. Start the stack:
   ```bash
   docker compose --env-file .env -f docker/docker-compose.yml up --build -d
   ```

Only ports **80/443** (nginx) need to be exposed publicly. Keep MySQL (3306) and the
backend (8080) private — remove their `ports:` mappings in production if the host is
internet-facing.

---

## 🛡️ Admin area

There are **no seeded user accounts**: participants register themselves from the app.
The only exception is a single built-in administrator, provisioned from the
environment on first boot.

Set these in `.env` **before** starting the stack for the first time:

```env
ADMIN_EMAIL=admin@your-domain.example
ADMIN_INITIAL_PASSWORD=a-long-random-password   # min 8 chars
```

On boot the backend creates the account with role `ADMIN` and flags it
*must change password*. Sign in from the normal login screen: the app forces a
password change, then lands on `/admin`.

The account is deliberately constrained:

* it **cannot be deleted** and its role cannot be changed (`user_auth.is_system`);
* only its **email and password** can be edited, from the admin page itself;
* it is excluded from the participant counts and from the users CSV.

`ADMIN_INITIAL_PASSWORD` is only used at creation time — once the password is
changed, the value in `.env` is inert and the account is never re-provisioned.

### Dashboard

| Metric | Definition |
| --- | --- |
| Registered users | accounts excluding administrators |
| Active | at least one sign-in in the last `STATS_ACTIVE_WINDOW_DAYS` days (default 10) |
| Regular | signed in on **every one** of the last `STATS_REGULAR_WINDOW_DAYS` days (default 7) |
| Activities collected | all activity records, plus a per-day distribution chart |

Activity and regularity are computed from the `login_event` table introduced in
`V3`, so the figures start accumulating from the moment that migration is applied —
earlier sign-ins were never recorded individually.

### Export

Two CSV files, downloadable from the dashboard:

* `chronogram-activities.csv` — **pseudonymised**: a numeric `user_id`, never an email;
* `chronogram-users.csv` — account and profile data; join on `user_id`.

Both are UTF-8 with a BOM so Excel on Windows reads accented characters correctly.
The users file contains personal data: treat it as such.

---

## 🗃️ Inspect the database

```bash
docker exec -it chronogram-mysql mysql -u chronouser -p chronogram
```

---

## 👥 Contributors

| Name                 | Role                             | GitHub                                             |
| -------------------- | -------------------------------- | -------------------------------------------------- |
| Prof. Sergio Nisticò | Committente                      | —                                                  |
| Prof. Mario Molinara | Sviluppatore e coordinatore      | [@mariomolinara](https://github.com/mariomolinara) |
| Giuseppe Alfieri     | Progettista e sviluppatore       | [@giusalfieri](https://github.com/giusalfieri)     |
| Paolo Simeone        | Progettista e sviluppatore       | [@bonoboprog](https://github.com/bonoboprog)       |
| Violeta Perez        | Sviluppatore prototipo iniziale  | [@violetapd](https://github.com/violetapd)         |
