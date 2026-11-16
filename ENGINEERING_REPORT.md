# CareBridge 2.0 engineering report — 16 November 2026

## Original state

The repository contained a Java/Spring modular monolith, React SPA, PostgreSQL scheduling, JWT/refresh authentication, doctor verification, admin tools, Redis catalog caching and an appointment Kafka outbox/notification pipeline. The four requested 2.0 differentiators were absent. Historical completion claims described the original appointment scope. The initial fresh baseline passed 15 unit and 13 real-service integration tests plus frontend lint/build.

## Delivered implementation

- Care plans, goals, assigned patients/providers, typed investigations/follow-ups/consultations, deadline/progress tracking, versioned updates and protected history snapshots.
- Closed-loop referrals with sender/receiver permissions, explicit transitions, appointment validation, outcome-based completion, cancellation/reassignment and automatic scheduling reopening when linked visits are cancelled or moved.
- Scoped chronological journey with care events, retained appointment events and the requesting user's notifications; type/date/status filters and pagination.
- Waiting preferences, provider/specialty matching, administrator-controlled priority, expiring offers, in-app preferences, independent Kafka recovery consumption, atomic acceptance and patient-scoped retry keys.
- Explicit care-gap rules, scheduled/admin-triggered execution, cluster scan coordination, duplicate protection, resolution/reopening and audit/outbox-backed notices.
- HAPI FHIR R4 exports for Patient, Practitioner, Appointment, Encounter, CarePlan, ServiceRequest, Observation and Task, with strict parsing/instance validation and documented limits.
- New patient/provider/admin-facing care pages, dashboard counts, referral management, journey filters, recovery and rule/alert views, preserving the existing design and workflows.
- Versioned identifier-only events, durable consumer deduplication, retry/DLT handling, correlation propagation, unified notifications, login/logout/authorization audit and admin-only operational metrics.
- Java 21 alignment, lazy frontend routes, schema indexes, isolated scheduled-job threads, stronger Kafka readiness, Vercel/Render configuration and a manual, test-gated exact-commit deployment verifier.

## Architectural/database changes

The modular monolith is retained. Transactional JDBC modules share PostgreSQL/Spring transactions with existing JPA code; no microservices or separate FHIR server were introduced. V1 remains unchanged. Additive V2–V5 migrations create care/recovery/gap persistence and permit system-actor history for asynchronous reconciliation. Existing user configuration and database directories were preserved.

The original doctor-row lock and PostgreSQL partial GiST exclusion constraint remain authoritative for booking. Care updates lock the owning plan and validate expected versions. FHIR is an authorized export boundary, not a claim of full interoperability or regulatory conformance.

## Files and validation

New backend packages: `care`, `recovery`, `gap`, `fhir`; extensions to `auth`, `audit`, `notification`, `outbox`, `config`, `common`. New migrations: `V2__care_coordination.sql` through `V5__system_care_history.sql`. Tests extend PlatformIT/KafkaFlowIT and add care-state/event-compatibility tests. Frontend additions: `careTypes.ts`, `WorkflowForm.tsx`, `Care.tsx`, `CareGaps.tsx`, `Recovery.tsx`, coordination browser journeys; App, Dashboard, Appointments and notifications are integrated.

Operations: Java 21 Dockerfile/CI alignment, `frontend/vercel.json`, `render.yaml`, deployment script/tests, production environment example, workspace autobuild setting and ignore rules. Documentation: README, status/plan/architecture, local/deployment/API/FHIR/events/security/testing/launch guides and this report.

Actual command outcomes and final test counts are in TESTING.md and PROJECT_STATUS.md. Successful evidence includes real database concurrency, FHIR validation, all seven browser journeys, npm audit, deployment-script unit tests, clean and incremental migrations, native readiness and Compose syntax. Docker runtime, hosted CI and cloud deployment are not claimed as executed.

## Execution and release

Use LOCAL_SETUP.md for full Compose or native-app/Docker-infrastructure modes. Start full local Compose with `docker compose up --build --wait`, after filling the ignored `.env`; open `http://localhost:3000`. The demo seed contains fictional patients/providers and uses the real API.

DEPLOYMENT.md describes Vercel plus an always-on Render backend/managed services, same-site custom-domain cookie requirements, and the VM alternative. Cloud creation/deployment requires owner authorization, provider credentials, region/domain selection, production environment secrets and operating acceptance. The prepared workflow does not deploy automatically on push.

## Remaining limits

- Docker/hosted-CI/managed-service/runtime launch gates remain unverified, and load/restore/security reviews remain required.
- FHIR is JSON export only; no SMART/import/search or regional implementation-guide certification. Investigation outcomes are recorded text, not a complete laboratory information system.
- Notifications are in-app only. Waiting entries/offers/rule lists return the latest 100; clinical journey/plans/referrals/alerts paginate.
- Cross-tab refresh coordination, password recovery/MFA, retention automation, batched original appointment DTO reads and deployment-specific rate limiting/monitoring remain improvements.
- Broker storage failure was reproduced on the temporary native Windows test broker. Use the supported Linux-container/managed deployment topology, and verify storage/consumer lag as well as health endpoints.

No real patient data, paid infrastructure, external accounts, push or deployment was used by this implementation session. Repository commits observed during continuation were pre-existing/external; the agent did not create a commit or overwrite user history.
