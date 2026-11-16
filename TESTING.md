# Testing and executed evidence

Verification uses synthetic records only. The suite never points at the existing development or production database. Tests are evidence for the scenarios described here, not blanket production or regulatory certification.

## Reproduce

With Java 21, Maven 3.9.11+, Node 22.19.0 and a working Docker daemon:

```sh
mvn -B -f backend/pom.xml verify
npm ci --prefix frontend
npm run lint --prefix frontend
npm run build --prefix frontend
npm audit --prefix frontend
python -m unittest discover -s scripts -p "test_*.py"
```

Backend defaults use Testcontainers PostgreSQL/Redis and embedded Kafka. Native equivalents can be supplied through `PG_TEST_URL`, `PG_TEST_USER`, `PG_TEST_PASSWORD`, `REDIS_TEST_HOST`, `REDIS_TEST_PORT`. **Use disposable services only:** PlatformIT truncates its fixture tables with cascade, and RedisIT clears its test caches. H2 is not substituted for PostgreSQL concurrency behavior.

Start the complete stack, populate the fictional accounts with `node scripts/seed-demo.mjs`, install the pinned browser with `npx --prefix frontend playwright install chromium`, then run `npm run test:e2e --prefix frontend`. Set `E2E_BASE_URL` to the frontend origin (3000 for Compose, 5173 for Vite). Browser requests also need that exact origin in `ALLOWED_ORIGINS`; keep `DEMO_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` in the invoking environment. `CHROME_PATH` optionally selects an installed Chrome executable. Never run these journeys against real patient data.

## Coverage

- Unit/API-advice: authentication hashing/rotation/replay, JWT validation, scheduling/DST, appointment transitions, care/referral terminal transitions, client error mapping, outbox acknowledgement/backoff, retained-event compatibility.
- PostgreSQL: registration/refresh ownership, direct overlap exclusion, eight competing bookings, competing offer claims, duplicate simultaneous acceptance retries, cancellation racing booking, care update conflicts, actual referral completion, cancellation-driven scheduling reopening, configured gap deduplication/reopening/resolution, notification transaction rollback, audit/metrics access, FHIR validation and patient isolation.
- Kafka: real broker acknowledgement, repeated consumption, subsequent processing, retry/DLT delivery for both appointment consumers. The real browser stack additionally exercises cancellation → Kafka → offer → acceptance.
- Redis: real serialization, cache hit/miss and post-commit invalidation.
- Browser: public/mobile discovery; login/session restoration/booking/rescheduling/cancellation/profile changes; provider/admin workflows; real patient registration → provider plan/referral → configured overdue alert → completion/journey; waiting-list offer claim; validation errors; protected navigation.
- Deployment verifier: exact tested commit targeting, matching revision/readiness, rejection of a healthy old deployment, missing-configuration refusal and secret-safe output. Network calls are mocked in these tests; no deployment occurs.

## Results and environment

Baseline: 15 unit and 13 integration tests passed, plus frontend lint/build. Final full verification on 16 November 2026 passed **18 unit and 25 integration tests**, zero failures/errors/skips (`.tools/v2-complete-verify.log`). After the final dashboard adjustment, all 18 unit tests and the focused receiving-provider referral integration test passed (`.tools/v2-dashboard-verify.log`). The full suite preceded that small adjustment; the focused regression verifies it.

Seven Playwright tests passed in **49.2 seconds**, using installed Chrome and the real API, PostgreSQL, Redis and Kafka (`.tools/v2-browser-release.log`). The care journey was visually inspected at 390px width; the test also asserts no page overflow. Frontend production build and lint passed after fixing one test-only `prefer-const` issue. Route splitting removed the oversized initial-chunk warning; two upstream Zod annotation warnings remain. `npm audit` reported **zero vulnerabilities** across production and development packages (`.tools/v2-npm-audit.json`). Three deployment-verifier tests passed locally with Python 3.13; CI selects Python 3.12.

V1–V5 migrations also passed on a newly created empty database, followed by the care-plan isolation/completion integration test (`.tools/v2-clean-migration.log`). Both Compose configurations passed `config --quiet`. Full native readiness returned `UP`; `/actuator/info` reported the expected local revision.

The Windows host has no Docker daemon. Native PostgreSQL/Redis and embedded Kafka paths were executed; container image builds, Compose runtime and default Testcontainers paths remain NOT VERIFIED here. CI is configured to exercise those paths, but hosted GitHub Actions has not been run in this session. Linux/macOS commands are documented from repository configuration, not represented as executed on this Windows host.

## Failures found and resolved

- The recovery consumer created a second expected DLT record for malformed appointment input; the old single-record Kafka assertion was corrected to verify both independent groups.
- HAPI startup required its matching cache-provider module. Explicit local R4 validation support plus caffeine fixed it. The editor had also produced unresolved-import class files in Maven output; workspace autobuild was disabled and Maven rebuilt cleanly.
- A concurrent Java 25 edit was reconciled to Java 21 after the owner's explicit selection; Docker/CI/docs were aligned.
- A second browser run failed recovery delivery after the temporary Windows Kafka broker developed a checkpoint-file lock overnight. Storage errors were preserved in `.tools/v2-kafka.log`; pending outbox rows remained durable. Restarting only the disposable broker restored delivery and all seven browser tests passed. Scheduler threads are separated so broker backoff cannot starve gap/expiry scans; readiness also checks topic leaders/ISR. This does not replace production storage/lag monitoring.

Outstanding operational checks: production data migration/restore, load/soak testing, managed-service TLS/ACLs, a comprehensive backend dependency advisory scan, independent security assessment and hosted deployment verification. A targeted tracked-file check found no tracked local environment files, private keys or recognized live-token literals; this is not a complete secret-history audit.
