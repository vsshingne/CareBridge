# CareBridge 2.0 implementation plan

## Final phase disposition — 16 November 2026

Phases 1–10 and 14 are complete for the scope and limits in ENGINEERING_REPORT.md. Phase 11 has validated setup/configuration and real native-service tests; Docker builds/runtime remain NOT VERIFIED. Phase 12 has test-gated CI and release/deployment configuration, but hosted execution remains NOT VERIFIED. Phase 13 deployment preparation is complete; no cloud deployment was performed. Java 21 is retained.

Final evidence: 18 unit + 25 integration tests passed, followed by the final dashboard-focused regression; seven browser tests, frontend lint/build, three deployment verifier tests, clean V1–V5 migrations, native readiness and both Compose syntax checks passed. See TESTING.md for exact scope and logs.

Remaining release work: execute Docker/Compose and hosted CI, configure authorized production services/domains/secrets, verify security/load/backup restore, then deploy and validate the matching revision. These external gates are not application implementation completion claims. The ordered plan and checkpoints below are historical; final status takes precedence. Resolved gap alerts now reopen when the configured condition recurs.


Authoritative scope: attached master request, 15 November 2026. Preserve existing files and data; no deployment or paid services authorized. Historical reports describe CareBridge 1.0, not completion of 2.0.

## Ordered phases and verification gates

1. Audit: inventory source, migrations, security, UI, infrastructure and tests; execute fresh baseline before application edits. COMPLETE (integration result recorded in PROJECT_STATUS).
2. Architecture: retain Java 21/Spring Boot modular monolith, PostgreSQL, existing outbox/Kafka and React. COMPLETE. New modules use additive Flyway migrations and explicit DTOs. No external clinical decisions.
3. Database/backend: COMPLETE for care foundation. V2 additive migration, DTOs, scoped transactional JDBC services alongside existing JPA; PostgreSQL verify passed.
4. Closed-loop care: transactional goals/activities, referral lifecycle, reassignment and appointment verification; paginated, authorized journey. Verify isolation, transitions, rollback and concurrent updates.
5. Recovery: waiting preferences, cancellation-driven idempotent offers, expiry, atomic claim through existing doctor locking and exclusion constraint. Verify competing claims and retries.
6. Care gaps: explicitly configured rules only, deduplicated alerts, completion resolution, retryable scheduling and scoped dashboard.
7. FHIR: select R4, evaluate HAPI, implement typed mappings and validation tests; document supported subset and unsupported interactions.
8. Events: extend existing outbox with versioned care/recovery events, durable deduplication, correlation and DLT tests.
9. Frontend: extend current portals with care, referrals, journey, recovery and gaps; no replacement or mock production flows.
10. Security/testing: all new endpoints require role and resource checks; meaningful PostgreSQL integration/concurrency, browser journeys and regression verification.
11. Local Docker/setup: validate both run modes, preserve volumes and environment; document runtime blockers.
12. CI/CD: extend tests and release gates; hosted execution remains NOT VERIFIED until run.
13. Deployment preparation: independent frontend/backend configuration, practical hosting options; no external provisioning without authorization.
14. Final audit: update all requested documents, actual test outcomes and PASS/FAIL/NOT VERIFIED launch gates.

## Design decisions

- Existing appointment locks and PostgreSQL exclusion constraint remain booking authority.
- Patient care reads are scoped to self; doctors see only plans they own or referrals explicitly assigned to them. An unrelated appointment does not grant blanket access to all patient records.
- A treating appointment establishes permission to initiate a plan. Responsible provider controls plan activities; receiving provider controls referral execution. Creation or scheduling never implies completion.
- Concurrent edits supply expected version and serialize on plan/referral rows; history and outbox commit with the mutation.
- Keep technical and clinical data out of event payloads/logs. Clinical text stays in protected database records.
- Local verification uses a new synthetic database on 127.0.0.1:55433, Redis on 56380 and the existing embedded Kafka integration test. Existing data directories are untouched.

## Resume instructions

Read latest PROJECT_STATUS.md section first. Inspect actual files and test reports before proceeding. Execute phases in order and update this document with exact outstanding work if a session ends. Do not treat planned phases as implemented or historical tests as current evidence.

## Historical checkpoints

- Baseline full verify: 15 unit + 13 integration tests passed.
- Phase 3/4 backend: care plans, goals, activities, referrals, reassignment, scoped paginated history and version conflict protection implemented. Verify: 15 unit + 16 integration tests passed (`.tools/v2-care-verify.log`). UI remains phase 9. Journey currently includes care history and current appointment state; notification inclusion and full appointment history remain phase 8 work.
- Phase 5: V3 waiting entries/offers/notification table, cancellation consumer in independent Kafka group, expiry job, atomic claim with idempotency key implemented. Regression + new competing-claim/expiry tests running (`.tools/v2-recovery-verify.log`).
- Two additional care state unit tests added after phase 4 run; included in phase 5 verification.
- Phase 5 verification: 17 unit tests and 16 PlatformIT checks passed; Kafka initially failed its old single-DLT-record assumption because each independent group correctly dead-letters malformed input. Updated test expects both groups; targeted Kafka verify passed. Redis passed in the initial recovery run.
- Phase 6: V4 configured care-gap rules, transactional/deduplicated alerts and in-app notices, scoped rule administration and alert reads implemented. Targeted verify: 17 unit + 17 PlatformIT checks passed, including no rules/no alerts, provider scope, deduplication, completion resolution and patient isolation. Rule revisions are explicit; once per rule revision/entity prevents repeated alerts. No clinical rules seeded.
- Phase 7: R4 export implementation and all-eight-resource integration test added; dependency download/verification in progress. FHIR_INTEGRATION.md records supported mapping and limitations.
- Phase 7 verification passed: 17 unit tests plus focused PostgreSQL FHIR integration. Initial startup failures identified missing HAPI cache provider; explicit local validation chain and matching caffeine provider fixed it. All eight resource mappings, invalid required fields and patient export isolation passed (`.tools/v2-fhir-cache.log`). Editor autobuild was disabled in workspace settings after it overwrote Maven classes with unresolved imports; clean Maven rebuild restored correctness.
- Phase 8: transactional care/registration events, independent care consumer with rollback-safe deduplication, unified notifications and appointment-event journey added. Full regression is running. Concurrent external edits changed pom Java target from 21 to 25; user was asked which to keep. Test command currently overrides `-Djava.version=21` without overwriting that edit.
