# Deployment runbook

Status: configuration prepared, **not deployed or production-certified**. External account access, paid resources and deployment require the owner's explicit authorization. See LAUNCH_CHECKLIST.md for outstanding gates.

## Recommended topology

For the requested initial managed deployment, use **Vercel for the React SPA, an always-on Render Docker service for Spring Boot, and managed PostgreSQL/Redis/Kafka**. `frontend/vercel.json` supplies SPA deep-link routing; `render.yaml` prepares the backend with production profile, readiness and secret inputs. Neither configuration has been applied to an account. Budget for continuously running compute, database, Redis, Kafka, backups, egress and domains; choose actual capacity after load testing, especially FHIR validation memory. See current [Render pricing](https://render.com/pricing) and [Vercel pricing](https://vercel.com/pricing), not a fixed total inferred from one advertised service price.

Use HTTPS custom domains under the same registrable domain, for example `app.example.org` and `api.example.org`. Vercel's project root is `frontend`; set `VITE_API_BASE_URL=https://api.example.org/api/v1` before building. Backend `ALLOWED_ORIGINS` must include exactly `https://app.example.org`. This preserves SameSite=Strict cookies for cross-origin, same-site API calls. Unrelated `vercel.app`/`onrender.com` origins are cross-site and are not a working refresh-cookie topology. Alternatively configure a same-origin API proxy and ensure it never caches private responses.

Render setup after authorization: connect the repository, apply/review the blueprint, choose a suitable always-on plan/region, configure every `sync: false` secret, provision a verified managed PostgreSQL JDBC URL and private Redis/Kafka credentials, and configure a custom API domain/TLS. Supply `ADMIN_EMAIL` and `ADMIN_PASSWORD` once, then remove them. Set `APP_FHIR_BASE_URL` to a stable identifier namespace. Disable automatic deployment until test gates and operating checks are accepted. `RENDER_GIT_COMMIT` is exposed as a release identifier through `/actuator/info` for exact-commit verification.

The existing VM deployment remains an alternative:

An always-on Linux VM can run the two application containers, a host HTTPS reverse proxy, and private managed PostgreSQL, Redis and Kafka. This preserves same-origin refresh cookies and continuously running Kafka consumer/outbox jobs. PostgreSQL is the durable source of truth. Do not use the local development Compose stack as an Internet-facing deployment.

Planning estimate, not measured capacity: start application hosting around 2 vCPU / 4 GiB RAM, managed PostgreSQL around 2 GiB RAM with automated backups, Redis around 256–512 MiB, and a managed Kafka plan with three partitions for each configured topic. Load-test before setting limits. Kafka is likely the largest fixed operational expense for a small installation. Budget separately for storage, backup retention, egress, monitoring and a domain. Prices depend on region/provider; no paid resources have been selected or created.

Alternatives evaluated:

| Platform | Fit / tradeoff |
| --- | --- |
| VM/VPS + managed services | Recommended for predictable always-on background work and same-origin routing; owner maintains host patches and TLS |
| Render Docker service | Viable always-on application container with managed/external infrastructure; configure frontend proxy hostname for its service topology |
| Railway | Potential container host; validate private networking, persistent workloads and Kafka connectivity before selection |
| Google Cloud Run | Requires deliberate always-on CPU/minimum-instance decisions for the in-process consumers and scheduler; more operational configuration |
| AWS | ECS/EC2 plus managed data services is viable, with more networking/IAM and operating overhead |
| Vercel frontend | Static SPA only; proxy `/api` to separately hosted Spring Boot, then SPA fallback; never host this Java process as a Vercel frontend |

These are architecture assessments, not verified deployments. Official sources: [Docker production Compose](https://docs.docker.com/compose/how-tos/production/), [Render image deployments](https://render.com/docs/deploying-an-image), [Render private services](https://render.com/docs/private-services), [Vercel external rewrites](https://vercel.com/docs/routing/rewrites). Vercel proxying must preserve cookies and disable caching of private API responses. A direct cross-site API URL does not work with the current SameSite=Strict refresh-cookie policy.

## Provisioning prerequisites

After authorization, provision a host, DNS name, trusted TLS certificate, managed data services in the chosen region, private networking/firewall rules, and secret storage. Only HTTPS/HTTP redirect ports should be public. Do not publicly expose PostgreSQL, Redis, Kafka or backend management ports. Managed PostgreSQL must support `btree_gist`; create the extension with a provisioning role if the runtime/migration account cannot. Configure encrypted transport, credentials, backup retention and restore tests.

Copy `.env.production.example` to `.env.production` without overwriting an existing file. Fill every required value using the provider's secret store or a restricted host file (`chmod 600 .env.production` on Linux). Never paste secrets into CI logs. `DATABASE_URL` must be a JDBC URL, e.g. `jdbc:postgresql://<private-host>:5432/<database>?sslmode=verify-full`; configure the trusted CA as required by the provider. Database credentials belong in separate variables. JWT_SECRET is at least 32 cryptographically random bytes encoded as base64. `ALLOWED_ORIGINS` is the exact public HTTPS origin, no trailing slash.

Redis TLS/username/password map to Spring's standard environment properties. For Kafka configure the provider's security protocol, SASL mechanism, JAAS and TLS properties. Precreate `appointment-events`, `notification-events`, `appointment-events.DLT`, `care-events`, `care-events.DLT` with three partitions and appropriate replication. Disable topic auto-creation with `SPRING_KAFKA_ADMIN_AUTO_CREATE=false` where required. Grant producers and `carebridge-notifications-v1`, `carebridge-recovery-v1`, `carebridge-care-notifications-v1` consumer groups least-privilege access, including DLT publication. Readiness checks cluster access and topic leaders/ISR; monitor consumer lag, storage and outbox backlog independently.

## Prepared manual release gate

The existing CI workflow builds/tests Java 21, lints/builds/audits the SPA, runs real-service integration tests, executes deployment-verification unit tests, and runs Compose plus all browser journeys. Main-branch push builds container archives only after all jobs pass. No registry or cloud account is accessed automatically.

For an authorized Render deployment, configure a GitHub `production` environment with required reviewers, secret `RENDER_DEPLOY_HOOK` and variable `PRODUCTION_API_ORIGIN`. Manually dispatch **CareBridge CI** on main with `deploy_render=true`. The deploy job depends on all test jobs, targets the exact tested Git SHA, waits for readiness and checks that `/actuator/info` reports that SHA. A healthy old deployment does not count as success. A timeout fails the job and requires operator inspection; the script never claims a successful deployment from hook acceptance alone. The Vercel project is deployed independently after its frontend build passes; review its provider integration and approval settings before enabling automatic releases.

Provider references: [Render deploy hooks and commit targeting](https://render.com/docs/deploy-hooks), [Render blueprint specification](https://render.com/docs/blueprint-spec), [Vercel Vite deployment](https://vercel.com/docs/frameworks/frontend/vite). The local unit tests use simulated HTTP responses to test the deployment verifier; no real deployment was executed.

`ADMIN_EMAIL` and `ADMIN_PASSWORD` provision one administrator. Remove both after first successful startup; never assume changing these rotates an existing administrator's password. `SECURE_COOKIES=true` is forced by production Compose. Swagger is disabled in the production profile.

## Build and start (authorized host)

```sh
docker compose -f compose.production.yml config --quiet
docker compose -f compose.production.yml build
docker compose -f compose.production.yml up -d --wait --wait-timeout 300
docker compose -f compose.production.yml ps
docker compose -f compose.production.yml exec backend wget -qO- http://localhost:8080/actuator/health/readiness
```

The production file is standalone, not an override of the local file. It starts only application containers; managed infrastructure must already exist. The frontend binds only host loopback port 3000. Install `deploy/nginx.conf.example` into the host nginx HTTP configuration, replace its domain/certificate paths, run `nginx -t`, and reload nginx. The sample enables HTTPS, HTTP redirect, request-body limits and per-IP authentication throttling. Validate proxy behavior and tune limits for shared networks. Provision/renew certificates with your host's certificate tooling before enabling traffic.

Browser requests stay on the public origin, through host nginx → frontend nginx → backend. Keep `VITE_API_BASE_URL=/api/v1`; no frontend secret is needed. Test registration, refresh after reload, logout, doctor verification, booking/conflict/cancellation, and asynchronous inbox delivery through the public TLS URL before launch.

## Migrations, rollback and backups

Take an encrypted PostgreSQL backup and verify a restore into an isolated database before each schema-changing release. Flyway applies pending migrations during backend startup; Hibernate then validates mappings. The migration identity needs DDL privileges. For tighter privilege separation, run a controlled migration startup using a migration credential, then restart with a runtime credential after schema validation. Do not run Flyway clean, automatic baselining, or `ddl-auto=update` against existing data.

Keep migrations backward-compatible with the previous application release. Rollback means restore the previous immutable application images after verifying schema compatibility; otherwise use a tested forward repair or explicitly approved database recovery. Do not delete volumes or silently restore over live patient data. Managed PostgreSQL needs point-in-time recovery and periodic restore drills; Kafka retention and outbox/audit/refresh-token archival require an owner-approved retention policy.

## CI and release authorization

`.github/workflows/ci.yml` runs Maven verify, npm lint/build/audit, full Compose startup, API health, seed and Playwright. Successful pushes to `main` produce a downloadable container-image archive tagged with the commit SHA only after all three jobs pass. No registry publication or deployment occurs. Protect `main` with required backend/frontend/compose checks and review requirements in repository settings; YAML cannot enforce branch protection by itself.

After release approval, download the `carebridge-images-<SHA>` artifact, verify its provenance/SHA, and `docker load -i carebridge-images.tar.gz` on the selected host. Add an image-only Compose override referencing the loaded SHA tags when deploying those artifacts. Automated registry publication/deployment remains blocked pending the repository/registry/host selection, credentials and explicit authorization. Configure a protected production environment before adding a deployment job.

## Operations and acceptance

Liveness (`/actuator/health/liveness`) tests process health. Readiness (`/actuator/health/readiness`) includes database, Redis and Kafka; broker downtime can make readiness unavailable even while booking transactions remain durable. Alert on readiness, HTTP errors/latency, connection pools, database storage, Kafka consumer lag, DLT growth and failed outbox rows. Use admin event retry only after fixing the delivery cause. Audit/notification data is retained until an explicit retention implementation is approved. Do not log request bodies, credentials or clinical reasons.

Before launch: verify image vulnerability scans, branch protection, TLS/cookie behavior, rate limiting, backup restoration, managed-service failover, load/capacity, and organizational data-handling requirements. None of these external operational checks is established by local tests.
