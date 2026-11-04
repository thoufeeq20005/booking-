# Local Development Setup

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 17+ | `JAVA_HOME` **must** point at it. The build fails fast with a clear message on older JDKs. |
| Node.js | 22+ | Frontend tooling |
| MySQL | 8.0+ | Local service on port 3306 |

Maven is not required – use the wrapper (`./mvnw` / `mvnw.cmd`).

> **This machine:** the system `JAVA_HOME` currently points at JDK 8 (`C:\Program Files\Java\jdk1.8.0_301`).
> Either change it to `C:\Program Files\Java\jdk-17` in *System Properties → Environment Variables*,
> or set it per terminal:
> - PowerShell: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'`
> - Git Bash: `export JAVA_HOME="/c/Program Files/Java/jdk-17"`

## 1. Configure

```bash
cp .env.example .env
```

Minimum to fill in: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (`openssl rand -base64 64`) and
`DATA_ENCRYPTION_KEY` (`openssl rand -base64 32`). With no email/SMS provider configured, the dev profile writes
verification links and OTP codes to the backend log (`[DEV EMAIL]`, `[DEV SMS]`), so every sign-in flow works offline.

Recommended: a dedicated MySQL user rather than `root`:

```sql
CREATE USER 'bookeasy_app'@'localhost' IDENTIFIED BY '<strong password>';
GRANT ALL PRIVILEGES ON bookeasy.* TO 'bookeasy_app'@'localhost';
GRANT ALL PRIVILEGES ON bookeasy_test.* TO 'bookeasy_app'@'localhost';
```

The databases `bookeasy` and `bookeasy_test` are created automatically on first run.
`bookeasy_test` is **wiped on every test run**.

## 2. Backend

```bash
cd backend
./mvnw spring-boot:run        # http://localhost:8080
./mvnw test                   # unit + MySQL integration tests
```

- Open the app at http://localhost:5173. Vite proxies `/api` to the backend, so cookies are same-origin.
- Health: http://localhost:8080/api/v1/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- Flyway applies migrations from `src/main/resources/db/migration` at startup.

## 3. Frontend

```bash
cd frontend
npm install
npm run dev                   # http://localhost:5173
npm test                      # Vitest
npm run build                 # type-check + production bundle
```

The frontend reads `VITE_*` variables from the root `.env`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `class, interface, or enum expected` / enforcer "needs JDK 17" | `JAVA_HOME` points at an old JDK |
| `Access denied for user` | check `DB_USERNAME` / `DB_PASSWORD` in `.env` |
| Browser shows CORS error | add the page origin to `CORS_ALLOWED_ORIGINS` |
| `Found non-empty schema(s) without schema history table` | `DB_NAME` points at a database not created by Flyway (e.g. the old `booking_db`); use a fresh name |
