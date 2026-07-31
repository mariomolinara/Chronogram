# Backend migration: Struts 2 → Spring Boot 3

The backend was rewritten from a Struts 2 / Tomcat WAR application to a
self-contained **Spring Boot 3 (Java 21)** service. The public HTTP contract
(paths, request fields, JSON response shapes) is **unchanged**, so the existing
Ionic/Vue frontend works without modifications.

## Why

| Area | Before | After |
| --- | --- | --- |
| Framework | Struts 2.5 (EOL, history of OGNL RCE CVEs), `devMode=true` | Spring Boot 3.3, actively maintained |
| Persistence | Hand-written JDBC DAOs, `DriverManager` per call, no pool | Spring Data JPA (Hibernate) + HikariCP pool |
| Transactions | Manual `setAutoCommit`/`commit`/`rollback` everywhere | Declarative `@Transactional` |
| Security | Custom `AuthInterceptor` + `CorsFilter` (`Allow-Origin: *` **and** credentials — invalid & unsafe) | Spring Security, JWT filter, strict CORS allowlist |
| Auth token | auth0 java-jwt, key in static initializer | `jjwt` 0.12, enforced ≥256-bit secret, issuer check |
| Validation | Manual null checks | Bean Validation (`@Valid`) |
| Errors | HTTP 200 + `success=false` for every failure | Proper status codes via `@RestControllerAdvice` (login kept 200 for client compat) |
| Schema | Forward-engineered from a `.mwb`, no versioning | Flyway migrations (`db/migration`) |
| Firebase | Firebase Admin SDK initialized at startup (unused by the app) | **Removed** |
| Packaging | `chronogram.war` staged by shell scripts, external Tomcat | Executable `chronogram.jar`, embedded Tomcat, multi-stage Docker build |
| Tests | none | JUnit 5 + Testcontainers ready |
| Java | compiled to Java 8 | Java 21 |

## Architecture

```
it.unicas.chronogram
├── config      ChronogramProperties, SecurityConfig
├── security    JwtService, JwtAuthenticationFilter, AuthPrincipal, entry point
├── common      ApiResponse envelope, exceptions, GlobalExceptionHandler
├── domain      JPA entities (UserAuth, UserProfile, Activity, ActivityType, PasswordResetToken)
├── repository  Spring Data repositories
├── auth        AuthController/Service, PasswordResetService, DTOs
├── activity    ActivityController/Service, DTOs
├── mail        EmailService
└── llm         LlmController/Service, DTOs
```

## Endpoints (unchanged, served under context path `/chronogram`)

| Method | Path | Auth | Response |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | public | `{success, message}` |
| POST | `/api/auth/login` | public | `{success, message, username, token}` |
| POST | `/api/auth/request-reset` | public | `{success, message}` |
| POST | `/api/auth/reset-password` | public | `{success, message}` |
| POST | `/api/llm/prompt` | public | extracted activity object |
| POST | `/api/activities/create` | JWT | `{success, message, data}` |
| POST | `/api/activities/update` | JWT | `{success, message, data}` |
| POST | `/api/activities/delete` | JWT | `{success, message}` |
| POST/GET | `/api/activities/list` | JWT | `{success, message, data:[…]}` |
| POST/GET | `/api/activities/types` | JWT | `{success, message, data:[…]}` |

## Database

Flyway manages the schema. On an **existing** database it baselines automatically
(`baseline-on-migrate=true`) and applies new migrations; on a **fresh** database
it creates all tables (`V1`) and seeds the default activity categories (`V2`).
Hibernate runs in `ddl-auto: validate` mode — it never alters the schema.

## Running

```bash
cp .env.example .env          # then fill in real values (JWT_SECRET_KEY ≥ 32 chars)
scripts/backend-scripts/setup_fresh_backend.sh
# API:    http://localhost:8080/chronogram
# Health: http://localhost:8080/chronogram/actuator/health
```

Rebuild after code changes: `scripts/backend-scripts/refresh_backend.sh`.

Build the jar locally (optional, Docker does this automatically):

```bash
cd backend && mvn clean package
```

## Going live with HTTPS (production)

The stack (`docker/docker-compose.yml`) runs **nginx** as a TLS-terminating
reverse proxy in front of the backend. nginx listens on 80 (redirect → 443) and
443 (TLS), forwarding to `chronogram-backend:8080` with the backend's context
path `/chronogram` preserved. The backend runs the **`prod` profile**
(`application-prod.yml`), which honours the `X-Forwarded-*` headers
(`server.forward-headers-strategy=framework`), so password-reset links and any
absolute URLs come out as `https://…`.

### 1. Prerequisites (do these by hand)

- **DNS**: point an `A`/`AAAA` record for your domain at the host's public IP.
- **Firewall**: open TCP 80 and 443 on the host.
- **Secrets** in `.env` (copied from `.env.example`, never committed):
  - `JWT_SECRET_KEY` — strong, ≥ 32 bytes: `openssl rand -base64 48`
  - `CORS_ALLOWED_ORIGINS` — the real front-end origin(s), e.g.
    `https://app.example.com` (comma-separated, **never** `*`)
  - `APP_CANONICAL_URL` — the public HTTPS URL, e.g. `https://app.example.com`
  - `SPRING_PROFILES_ACTIVE=prod` (already the compose default)
  - DB / LLM / mail secrets as usual

### 2. Set your domain in nginx

Edit `docker/nginx/conf.d/default.conf` and replace `your-domain.example`
(the `server_name` in the 443 block) with your real domain.

### 3. Obtain TLS certificates (Let's Encrypt / certbot)

Certificates are read from `/etc/nginx/certs/{fullchain,privkey}.pem`, mounted
from `docker/nginx/certs/` on the host. That directory is **git-ignored** — never
commit private keys.

Option A — one-shot with the standalone certbot (stop nginx briefly):

```bash
sudo certbot certonly --standalone -d app.example.com
sudo cp /etc/letsencrypt/live/app.example.com/fullchain.pem docker/nginx/certs/
sudo cp /etc/letsencrypt/live/app.example.com/privkey.pem   docker/nginx/certs/
```

Option B — webroot (no downtime; nginx serves the ACME challenge from
`docker/nginx/certbot-webroot/`, already mounted at `/var/www/certbot`):

```bash
sudo certbot certonly --webroot -w docker/nginx/certbot-webroot -d app.example.com
# then copy fullchain.pem / privkey.pem into docker/nginx/certs/ as above
```

Renewal: re-run certbot, refresh the two `.pem` files in `docker/nginx/certs/`,
then `docker compose exec nginx nginx -s reload`.

### 4. Bring the stack up

```bash
cd docker
docker compose config      # validate compose + env interpolation
docker compose up -d --build
docker compose exec nginx nginx -t   # validate the nginx config
```

Verify:

```bash
curl -I  http://app.example.com/                     # 301 → https
curl -sf https://app.example.com/chronogram/actuator/health   # {"status":"UP"}
```

### Notes / residual risks

- **HSTS** is set to 6 months without `includeSubDomains`/`preload`; tighten only
  once every subdomain is HTTPS-only.
- The `/api/llm/**` endpoint is public by design — keep it behind rate limiting
  before exposing it to the internet.
- nginx has no global rate limiting configured; consider `limit_req` for
  public auth/LLM routes.
