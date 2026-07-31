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
