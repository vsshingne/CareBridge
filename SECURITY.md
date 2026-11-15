# Security model and operating boundaries

Passwords use BCrypt. Access JWTs expire after fifteen minutes and are kept in browser memory. Refresh tokens are random, hashed at rest, rotated under lock and carried by HttpOnly cookies; replay revokes their family. Logout revokes refresh access; an existing access JWT remains usable until expiration. Disabled accounts are checked on every authenticated request. Refresh cookie endpoints enforce an exact allowed Origin. Production uses Secure cookies and explicit trusted origins, with HTTPS termination.

Care-plan creation requires the acting doctor to own a confirmed or completed appointment for that patient. Only the responsible doctor can change that plan or its activities. Patients can read their own plans and referrals. A receiving doctor can access and execute the assigned referral, but cannot read the referring doctor's full plan. Admins can read care records and configure global care-gap rules; clinical mutation still requires an assigned doctor. Resource checks execute in services, not merely UI routes. FHIR exports reuse care-plan authorization before reading data.

Mutation DTOs validate lengths, enums and times. Database foreign keys, checks, unique keys and transactional locks supplement request validation. Care edits require expected versions. Appointment recovery continues to use PostgreSQL exclusion constraints and doctor-row locking. Retry keys are scoped to patients; the owning patient is checked before a previous response can be returned.

Care history stores protected snapshots so goal edits, reassignment and outcome changes can be traced. No API edits or deletes audit/history records. Restrict database roles and backups operationally; application-level append-only access is not protection against a database superuser. Clinical text is excluded from Kafka events and audit metadata. Error responses are sanitized. Do not enable request-body, bind-parameter or payload logging in production.

Care-gap rules are explicit administrative configuration. There are no default clinical recommendations and no diagnosis engine. Rule alerts are workflow notices, not emergency triage. In-app notifications are persisted database messages, not email/SMS delivery.

Local test services bind to loopback and contain synthetic data. Native test PostgreSQL uses trust authentication only in its isolated loopback cluster; never use this authentication policy in production. Existing `.env` and data volumes are preserved and ignored by Git. Do not commit `.env.production`, database dumps, tokens or patient exports.

Before launch, verify TLS, private database/Kafka/Redis networking, managed secrets, broker ACLs, rate limiting, backups and restore drills, key rotation, retention, monitoring and an incident-response process. Password recovery, MFA and cross-tab refresh coordination remain separate improvements. This engineering design does not certify HIPAA or other legal/operational compliance.

Verification evidence and limitations are in TESTING.md and LAUNCH_CHECKLIST.md. Report security findings privately to the repository owner; do not include patient data or secrets in public issues.
