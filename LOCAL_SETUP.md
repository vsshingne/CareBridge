# Local setup

Run commands from the repository root unless stated otherwise. Requirements: Java 25, Maven 3.9.11+, Node 22.19.0 (or supported Node 24+), npm, Docker Engine/Desktop with Linux containers and Compose v2. PostgreSQL 17.6, Redis 7.4.5 and Kafka 3.9.1 are supplied by Compose.

## Verify tools (PowerShell and Linux/macOS)

```sh
java -version
mvn -version
node --version
npm --version
docker version
docker compose version
```

`docker version` must show a working server. A Compose executable alone is insufficient. Windows Docker Desktop needs its Linux container/WSL2 or Hyper-V backend running.

## Environment

PowerShell, without overwriting an existing environment:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
# Generate a signing secret; copy the output into JWT_SECRET in .env.
$bytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Linux/macOS:

```sh
test -f .env || cp .env.example .env
openssl rand -base64 48
# Edit .env in your preferred editor.
```

Replace every CHANGE_ME value. Use separate random database, admin and demo passwords (12–72 UTF-8 bytes for account passwords). Set `SECURE_COOKIES=false` for local HTTP. Keep both localhost origins from the example. Admin values are optional if both are blank, but required for demo seeding. Never use development credentials in production.

## A. Entire application in Docker

Identical in PowerShell and Linux/macOS:

```sh
docker compose config --quiet
docker compose up --build --wait --wait-timeout 300
docker compose ps
```

Frontend: http://localhost:3000. Backend: http://localhost:8080. Swagger: http://localhost:8080/swagger-ui/index.html. OpenAPI: http://localhost:8080/v3/api-docs.

PowerShell health checks:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/actuator/health/liveness
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
```

Linux/macOS:

```sh
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/actuator/health/liveness
curl --fail http://localhost:8080/actuator/health/readiness
```

## B. Native backend/frontend, Docker infrastructure

```sh
docker compose up -d --wait postgres redis kafka
```

Spring does not load `.env` automatically. In the backend terminal, load it before starting Maven. The example file uses simple KEY=value entries.

PowerShell:

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
  }
}
mvn -f backend/pom.xml spring-boot:run
```

Linux/macOS (only source your trusted, shell-compatible local file):

```sh
set -a
. ./.env
set +a
mvn -f backend/pom.xml spring-boot:run
```

In a second terminal (both platforms):

```sh
cd frontend
npm ci
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` to http://localhost:8080. Optional `frontend/.env.local` can set `API_PROXY_TARGET` and `VITE_API_BASE_URL`; retain `/api/v1` unless you deliberately configure a different same-site API deployment. Vite variables are public build-time values, never secrets.

## Database and demo data

Flyway runs `backend/src/main/resources/db/migration` automatically when the backend starts, then Hibernate validates the schema. Startup is the migration command: `mvn -f backend/pom.xml spring-boot:run`, or `docker compose up -d --build backend`. PostgreSQL must permit the `btree_gist` extension. Never edit an applied migration or use schema auto-update. Back up an existing database before upgrades; do not baseline an unknown schema automatically.

After startup, from the root:

```sh
node scripts/seed-demo.mjs
```

This creates fictional development accounts and real appointments through authorized APIs. It reads root `.env`; the patient is `patient@carebridge.example`, doctors are `ananya@carebridge.example`, `arjun@carebridge.example`, `maya@carebridge.example`; their password is your `DEMO_PASSWORD`. Admin uses your configured credentials. Do not seed production.

## Tests

```sh
mvn -B -f backend/pom.xml verify
cd frontend
npm ci
npm run lint
npm run build
npm audit
npx playwright install chromium
npm run test:e2e
```

The backend uses disposable Testcontainers PostgreSQL/Redis and embedded Kafka. Docker is required by default. `mvn -B -f backend/pom.xml test` runs unit/API-advice tests only. Optional external-service variables: `PG_TEST_URL`, `PG_TEST_USER`, `PG_TEST_PASSWORD`, `REDIS_TEST_HOST`, `REDIS_TEST_PORT`. **Only point these at disposable test services: PlatformIT truncates tables and RedisIT clears catalog caches.** Never use your development or production database. Browser tests expect the seeded native app on port 5173. For Compose set `$env:E2E_BASE_URL='http://localhost:3000'` (PowerShell) or `export E2E_BASE_URL=http://localhost:3000` (Linux/macOS) before running them.

## Shutdown and troubleshooting

Stop native servers with Ctrl+C. `docker compose stop` pauses containers; `docker compose down` removes containers while preserving named database/Kafka volumes. Do not add `-v` for data you want to retain.

- 403 during login: use localhost, not 127.0.0.1, in the browser; ensure its exact origin is allowed. API clients must send an allowed `Origin` header.
- Session lost on reload: local HTTP requires `SECURE_COOKIES=false`; restart backend after changing it.
- 503 readiness: inspect `docker compose ps` and `docker compose logs --tail 100 backend postgres redis kafka`; Kafka startup can take time.
- Port conflict: stop the conflicting local service or adjust Compose host ports and matching native connection variables.
- Migration checksum error: restore the original applied migration; use a new numbered migration for changes.
- Native boot cannot find credentials: import .env into that terminal; restarting a different shell does not inherit them.
- Empty directory: register a doctor, set availability, and verify the doctor as admin, or run the demo seed.
- Build/test process cannot replace JAR on Windows: stop the Java process using that JAR, then rerun Maven.

Host verification limits are recorded in LAUNCH_CHECKLIST.md. Local runtime artifacts under `.tools` are ignored conveniences, not required installation paths.
