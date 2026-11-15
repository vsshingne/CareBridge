# CareBridge implementation status

> CareBridge 2.0 scope supersedes historical completion claims below. The original application is an appointment platform; the four requested 2.0 differentiators were absent at the start of this session. See the latest audit section and IMPLEMENTATION_PLAN.md.

## CareBridge 2.0 baseline audit — 15 November 2026

Repository inspected before application edits: Java services/controllers/entities/repositories, V1 SQL, security/JWT/session handling, outbox/Kafka/Redis, React routes/forms/API client, tests, Docker/Compose, CI and deployment docs. No AGENTS.md found. All application files were already untracked; no clean tracked baseline exists. Preserve original files and local secrets.

| Capability | Baseline status | Priority | Finding |
| --- | --- | --- | --- |
| Registration, profiles, doctor verification, authentication | COMPLETE | P0 | Existing BCrypt/JWT, refresh rotation and database-enabled checks |
| Appointment scheduling and concurrency protection | COMPLETE | P0 | Doctor row lock plus PostgreSQL exclusion constraint |
| Care plans, goals, activities and history | NOT IMPLEMENTED | P0 | No persistence, endpoints or UI |
| Closed-loop referrals, investigations, follow-ups | NOT IMPLEMENTED | P0 | No lifecycle model |
| Unified authorized care journey | NOT IMPLEMENTED | P1 | Appointment history only |
| Waiting list, recovery offers, expiry, idempotent claim | NOT IMPLEMENTED | P0 | Cancellation frees slots but no recovery engine |
| Configurable care-gap rules and dashboard | NOT IMPLEMENTED | P1 | No rule engine or scheduler |
| FHIR mapping/validation | NOT IMPLEMENTED | P1 | No FHIR dependency or resources |
| Outbox/Kafka/notification inbox | PARTIALLY COMPLETE | P1 | Appointment events only, no care/recovery events |
| Patient/doctor/admin UI | PARTIALLY COMPLETE | P1 | Original workflows implemented, 2.0 workflows absent |
| Resource authorization | PARTIALLY COMPLETE | P0 | Existing appointment ownership; new care policies needed |
| Audit | PARTIALLY COMPLETE | P1 | Existing mutations/login; care reads/changes absent |
| Automated tests | PARTIALLY COMPLETE | P0 | Original regression suite; no 2.0 coverage |
| Docker and CI | PARTIALLY COMPLETE | P1 | Files present; no Docker executable/daemon available here |
| Production deployment | PARTIALLY COMPLETE | P1 | Configuration/runbook exists; no live deployment verified |
| Documentation | NEEDS IMPROVEMENT | P1 | Historical scope claims do not cover 2.0; several required docs absent |
| Performance | NEEDS IMPROVEMENT | P2 | Per-appointment DTO queries; no clinical query indexes yet |

Fresh baseline: Maven unit tests **15 passed**, frontend ESLint and TypeScript/Vite production build passed. Vite reports two harmless dependency annotation warnings. Full Maven verify is running against a newly initialized synthetic PostgreSQL database and separate Redis; no existing data changed. Logs: `.tools/v2-baseline-*.log`. Existing secrets were not printed or changed. No deployment attempted.

Fresh full baseline subsequently passed: **15 unit + 13 integration**, zero failures/errors/skips. Care foundation verification passed: **15 unit + 16 integration**, zero failures/errors/skips (`.tools/v2-care-verify.log`). New tests verify cross-patient/provider isolation, provider-owned activity execution, non-empty completion outcome, genuine referral completion, history filtering/pagination and concurrent version conflicts. Files added: V2 migration and `care/{CareController,CareDtos,CareService,CareStates}.java`; tests extend PlatformIT. No existing appointment API was changed. Care backend is PARTIALLY COMPLETE/P0 until event/history refinements and UI are verified. Recovery implementation is currently under test; remaining phases are not complete.

## Architecture and plan
One Java 25 / Spring Boot 3.5 modular monolith, a React TypeScript SPA, PostgreSQL as the transactional source of truth, Kafka for asynchronous events, Redis for public catalog caches. No cloud deployment is configured.

Controllers accept validated DTOs, services own transactions and authorization, Spring Data repositories own persistence. Flyway owns the schema. A doctor row lock serializes scheduling changes; a PostgreSQL partial GiST exclusion constraint independently prevents active appointment overlap. UTC appointment instants are matched against weekly availability in the doctor's IANA timezone. Rescheduling preserves the appointment ID and returns it to PENDING.

Access JWTs live in browser memory. Random refresh tokens are stored hashed in PostgreSQL, rotated under a row lock, and delivered in HttpOnly cookies. Cookie endpoints require an explicit allowed Origin. Reuse revokes the token family. Account enabled status is checked on every authenticated request.

Appointment, audit and outbox writes commit together. Publishers lock batches with SKIP LOCKED, wait for Kafka acknowledgements, and retry with backoff. Consumers commit deduplication, notifications and a notification outbox event together. Failed consumption retries then reaches a dead-letter topic.

## Folder structure
```
backend/
  pom.xml, Dockerfile
  src/main/java/com/carebridge/
    config/ common/ auth/ user/ patient/ doctor/ availability/
    appointment/ audit/ outbox/ notification/ admin/
  src/main/resources/application.yml, db/migration/
  src/test/java/com/carebridge/
frontend/
  src/api.ts, App.tsx, pages/, components/, styles.css
  package.json, vite.config.ts, Dockerfile, nginx.conf
docs/architecture.md
.github/workflows/ci.yml
docker-compose.yml, .env.example, README.md
```

## Roadmap and verification gates
1. Foundation: pinned dependencies, schema, configuration, Compose, errors and health; compile.
2. Authentication: user/profile entities, JWT, rotated refresh tokens, provisioning; authentication tests.
3. Doctors: discovery, profiles, recurring availability, ownership; validation tests.
4. Appointments: locking, exclusion constraint, transitions, rescheduling; PostgreSQL integration/concurrency tests.
5. Events: transactional outbox, Kafka retry/DLT, idempotent inbox; publisher and consumer tests.
6. Redis/admin: public caches, invalidation, verification, accounts, analytics, audit; integration tests.
7. Frontend: real API flows for all roles, typed forms, query state; lint and production build.
8. Readiness: images, CI, seed mechanism, diagrams, security review, complete regression.

## Dependencies
Backend: Spring Boot BOM 3.5.16 (Web, Security, OAuth2 resource server/Jose, JPA, Validation, Actuator, Redis, Cache, Kafka, Flyway, PostgreSQL); springdoc 2.8.17; Testcontainers BOM 1.21.4; JUnit/Mockito/MockMvc via Boot test starter. Java 25, Maven 3.9.11.
Frontend versions will be pinned in package.json and package-lock.json. Infrastructure: PostgreSQL 17.6, Redis 7.4.5, Apache Kafka 3.9.1 (single-node KRaft for local use).

## Risks and decisions
- Docker is currently absent from PATH; real Testcontainers and Compose verification require a working Docker daemon. Do not substitute H2 for concurrency tests.
- At-least-once Kafka delivery requires durable consumer deduplication. A send/mark crash may publish twice.
- Cache entries are advisory, never booking authority; cache writes happen after database commit.
- Weekly local schedules encounter DST; nonexistent or ambiguous times are excluded from slot generation.
- Local infrastructure is single-node; TLS, managed secrets, backup/restore, rate limiting and production capacity planning remain deployment responsibilities.
- Notifications are an authenticated database inbox, not external email or SMS.

## Phase outcomes
1. Foundation: implemented. Maven compilation, Flyway migration and Hibernate validation passed against PostgreSQL 17.6.
2. Authentication: implemented. BCrypt, JWT validation, role checks, refresh rotation/replay, cookie Origin protection and secure admin provisioning tested.
3. Doctor/availability: implemented. Public discovery, profile editing, recurring windows, slot generation and DST rules tested.
4. Appointments: implemented. State transitions, ownership, rollback, direct exclusion-constraint enforcement and eight-way concurrent HTTP booking tested.
5. Events: implemented. Transactional outbox, acknowledgement, retry/parking, inbox deduplication, Kafka consumer retries and DLT verified.
6. Redis/admin: implemented. Real Redis cache miss/hit/post-commit invalidation verified; account disabling, verification, analytics, audit, outbox recovery APIs implemented.
7. Frontend: implemented. All three role workspaces use real APIs. Lint/build and browser journeys passed; desktop/mobile screenshots inspected.
8. Readiness: Dockerfiles, Compose, CI including smoke tests, OpenAPI, structured logging, correlation IDs, seed script and README implemented. Docker runtime/hosted CI verification remains pending.

## Executed verification — 5 November 2026
- Full `mvn verify`: **BUILD SUCCESS**, 12 unit tests + 13 integration tests, zero failures/errors/skips.
- PostgreSQL 17.6 was a real native disposable database, not H2 or a mock. Eight competing requests: exactly one HTTP 201, seven HTTP 409; one appointment, audit record and outbox event.
- KafkaFlowIT used a real embedded broker: acknowledged outbox publishing, duplicate delivery suppression, subsequent message processing and malformed payload dead-letter recovery passed.
- RedisIT used real Redis 7.4.5, a checksum-verified native Windows build from redis-windows/redis-windows. Cache serialization, misses, hits and after-commit invalidation passed. Default test configuration uses the official Redis Docker image.
- Frontend TypeScript/Vite build and ESLint passed. `npm audit` reported zero vulnerabilities after upgrades and a narrowly scoped minimatch override.
- Three Playwright tests passed against the real API: public discovery/mobile layout; patient login, refresh restoration, booking/rescheduling/cancellation/profile update; doctor availability and admin verification/audit review.
- Demo seed executed successfully and was reused by browser tests. It creates real accounts and appointments through authorized API endpoints.
- JAR packaging passed after stopping the preview server that held the file open on Windows.

## Remaining verification and operational limits
- Docker Engine/Desktop and WSL are unavailable on this host. PostgreSQL and Redis Testcontainers paths and full `docker compose up --build` were not run here; equivalent native-service tests passed. CI runs the default container-backed paths and Compose smoke tests on Ubuntu, but hosted GitHub Actions has not been executed in this local session.
- The browser preview disabled Kafka listeners/publishing because it used native services without a persistent Kafka broker. Kafka delivery was separately verified by KafkaFlowIT. Do not mistake preview configuration for the full Compose stack.
- No essential business endpoint is a placeholder. The complete original definition of done still requires successful Docker and hosted CI execution; do not represent those checks as passed.
- Production responsibilities and intentional scope limitations (TLS/private infrastructure, rate limiting, password recovery, retention, external delivery) are detailed in README.md.

## Local artifacts
Generated tools, native database data, logs and test binaries are ignored under `.tools/`; frontend screenshots are ignored under `frontend/test-results/`. `.env` contains generated local-only secrets and is ignored. No remote repository or cloud deployment was created.

## Initial independent audit — 15 November 2026 (before application changes)

Scope: inspected backend modules, SQL migration, frontend routes/forms/API client, Maven/npm manifests, Dockerfiles, Compose, CI, seed script and test sources. No AGENTS.md found in the repository. All project files are untracked; existing files and local environment are preserved. Historical results above are not evidence of execution in this audit.

Actual stack: Java 25, Spring Boot 3.5.16 modular monolith, JPA with PostgreSQL/Flyway, JWT/BCrypt, Redis public cache, Kafka transactional outbox, React 19/TypeScript/Vite/Tailwind. Preserve this architecture; no extra services or clinical features are justified.

| Feature / concern | Initial status | Priority | Evidence / action |
| --- | --- | --- | --- |
| Patient/doctor registration, login, refresh, logout | COMPLETE | P1 | Hashed passwords/tokens, rotation/replay protection, allowed-Origin cookie endpoints |
| Role and ownership authorization | COMPLETE | P0 | Security matcher rules plus service ownership checks; rerun integration tests |
| Patient and doctor profiles/search/verification | COMPLETE | P1 | Real persisted API and forms |
| Availability, booking, reschedule, cancellation/history | COMPLETE | P0 | Doctor row locking, optimistic version and PostgreSQL overlap exclusion; rerun concurrency test |
| Admin users, appointments, audit and failed events | COMPLETE | P1 | Implemented APIs and role workspace |
| Asynchronous inbox notifications | COMPLETE | P1 | Transactional outbox, acknowledged sends, durable deduplication, DLT |
| Invalid doctor timezone input | BROKEN | P1 | DateTimeException is not mapped to HTTP 400; currently generic 500 |
| Required missing query parameters / unsupported content type | BROKEN | P1 | Generic exception handler turns client errors into 500 |
| API base/proxy configuration | PARTIALLY COMPLETE | P1 | Same-origin client is sound; Vite proxy target is fixed; environment configuration missing |
| Database migrations | COMPLETE | P1 | V1 foreign keys, unique indexes, overlap constraint; no destructive changes planned |
| Docker/local infrastructure | PARTIALLY COMPLETE | P1 | Configurations exist; Docker daemon unavailable on host; runtime not verified |
| Production health/readiness | PARTIALLY COMPLETE | P1 | Aggregate health exists; explicit probe groups missing |
| Cloud deployment/runbook | NOT IMPLEMENTED | P1 | No production topology/overlay or standalone deployment guide |
| Required standalone documentation | NOT IMPLEMENTED | P1 | Architecture details and API table exist in README/docs; six requested root deliverables missing |
| CI testing | COMPLETE | P1 | Backend, frontend, Compose and Playwright jobs exist; hosted execution unverified |
| Approved release artifact pipeline | NOT IMPLEMENTED | P1 | No gated publication workflow |
| Frontend component/validation coverage | PARTIALLY COMPLETE | P1 | Three real browser journeys; no isolated component/form validation suite |
| Rate limiting, TLS, backup/restore, managed-service credentials | PARTIALLY COMPLETE | P1 | Deployment responsibilities, no evidence of operational verification |
| Session refresh across tabs | NEEDS IMPROVEMENT | P2 | Concurrent tab rotation may trigger family revocation |
| Retention and query performance | NEEDS IMPROVEMENT | P2 | Unbounded retained events/tokens; per-row DTO queries |
| Email/SMS, recovery, MFA, clinical records | NOT IMPLEMENTED | P3 | Not established application scope; do not invent these workflows |

Baseline executed: Maven unit tests 12 passed, 0 failures/errors/skips; ESLint passed; TypeScript/Vite production build passed (two dependency annotation warnings). Commands used bundled Java 21/Maven 3.9.11/Node 22.19.0: `mvn -B -f backend/pom.xml test`, `npm run lint --prefix frontend`, `npm run build --prefix frontend`. Logs: ignored `.tools/audit-unit.log`. Full integrations/browser checks pending isolated-service setup. Docker is absent from PATH; Compose binary alone does not provide a daemon.

Security review: no plaintext application passwords; JPA parameter binding; private DTOs and ownership checks; secrets remain in ignored .env. Development Redis/Kafka are unauthenticated and must stay private. Strict refresh cookies require a same-site deployment; recommended same-origin reverse proxy preserves this. No external deployment authorized. No claim of production readiness.

Implementation plan: repair client-error mapping with API regression tests; make frontend API/proxy environment-driven while preserving same-origin defaults; expose explicit readiness/liveness; add independent browser validation coverage; prepare production configuration and gated release artifacts; split exact setup/API/architecture/deployment docs; rerun available checks and record launch gates honestly.

## Final implementation and verification — 15 November 2026

Initial audit was appended before application changes. The existing appointment/auth/profile/admin/notification implementation was retained. No migration or existing .env was changed, and no user data was deleted. Integration checks used new isolated databases on loopback port 55432 and Redis on 56379.

### Phase outcomes

1. Discovery: inspected source, manifests, schema, infrastructure, CI and tests. Initial feature/priority matrix above records gaps. Baseline unit suite (12), frontend lint and build passed.
2. Architecture: retained the modular monolith, PostgreSQL/Flyway, Kafka outbox, Redis cache and React SPA; added root ARCHITECTURE.md with authentication, data flow and deployment decisions.
3–4. Backend fixes: invalid timezone and missing query parameters now return 400, unsupported media types 415; three MockMvc regression tests added. No booking rewrite was necessary. Explicit liveness/readiness and a production profile were added.
5. Frontend: configurable API base and Vite proxy; new registration-validation/error-display and protected-route tests. Browser testing found unstable accessible field names after validation; shared Field now links stable labels and error descriptions. Existing design and real API flows retained.
6. Infrastructure: standalone managed-service production Compose, production environment example and host nginx TLS/rate-limit template; ignore rules prevent local production config from entering source/build contexts. Both Compose files pass syntax validation. Docker daemon runtime remains unavailable.
7. Verification: final Maven verify passed 15 unit/API-advice tests + 13 real-service integration tests, zero failures/errors/skips. Tests include migrations, ownership, refresh replay, eight-way booking contention, direct overlap exclusion, Kafka acknowledgement/deduplication/DLT and Redis invalidation. Frontend lint/build and npm audit passed (zero vulnerabilities). Browser outcomes are recorded in LAUNCH_CHECKLIST.md; missing pinned Chromium required installed Chrome fallback. One new test initially revealed the accessible-name bug described above.
8. CI: preserved existing three test jobs, added browser artifact upload and SHA-tagged container archive generation on main only after all test jobs pass. No hosted CI or registry publication occurred. Git branch protection remains a repository setting.
9. Deployment: recommended always-on application VM plus managed PostgreSQL/Redis/Kafka and a same-origin HTTPS proxy. Prepared DEPLOYMENT.md, configuration and operations instructions; no provider resources created. Authorization, credentials, region/domain and operating acceptance are outstanding.
10. Launch audit: LAUNCH_CHECKLIST.md distinguishes PASS, FAIL and NOT VERIFIED. The application is not declared production-ready while Docker/hosted-CI/TLS/backup/managed-service checks remain open.

### Files changed in this audit

- Backend: common/Errors.java, resources/application.yml; new common/ErrorsTest.java and resources/application-production.yml.
- Frontend: src/api.ts, src/components/UI.tsx, src/styles.css, vite.config.ts, .dockerignore; new src/vite-env.d.ts, .env.example and e2e/validation.spec.ts.
- Operations: .gitignore, .github/workflows/ci.yml; new compose.production.yml, .env.production.example and deploy/nginx.conf.example.
- Documentation: PROJECT_STATUS.md, README.md; new ARCHITECTURE.md, LOCAL_SETUP.md, DEPLOYMENT.md, API_DOCUMENTATION.md, LAUNCH_CHECKLIST.md.

Commands: Maven test/verify with bundled Java 21 and Maven 3.9.11; npm lint/build/audit and Playwright via Node 22.19.0; Compose v2.39.4 config --quiet for both files. Production syntax check used PRODUCTION_ENV_FILE=.env.production.example, without creating .env.production. Native browser preview disables Kafka consumers/outbox because no persistent broker is running; KafkaFlowIT separately executes a real embedded broker. Liveness returned UP; readiness returned 503 as expected when Kafka is unavailable. Logs are ignored under .tools/audit-*. No commit or push was performed; all original files were already untracked.

### Final feature status changes

HTTP input error handling: COMPLETE/P1. Frontend API configuration: COMPLETE/P1. Accessible validation feedback: COMPLETE/P1. Required documentation: COMPLETE/P1. Production configuration and release artifacts: PARTIALLY COMPLETE/P1 until runtime/hosted verification. Core functional feature statuses remain COMPLETE within documented scope; this is not blanket certification of every edge case. Cross-tab refresh, retention and query optimization remain NEEDS IMPROVEMENT/P2. Separate shallow-rendered component unit tests are not added; actual browser component/form checks complement existing end-to-end journeys.

Final browser result: **5 passed in 11.8 seconds**, using installed Chrome through CHROME_PATH. Real public discovery/mobile layout, patient booking/session restoration/rescheduling/cancellation/profile editing and doctor/admin workflows passed. New component-level browser checks validate registration feedback/API error rendering and protected routing. Browser preview and disposable native services were stopped after verification; data directories remain preserved under .tools.
