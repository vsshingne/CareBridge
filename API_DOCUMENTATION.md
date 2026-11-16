# API documentation

## CareBridge 2.0 additions

Paths are relative to `/api/v1` and require bearer authentication. Patients read their own care; clinical changes require the assigned provider. Care and gap pages accept `page` (zero-based) and `size` (1–100), ordered newest first. Stale versions and invalid transitions return 409. Input errors return 400; unauthorized resources return 403/404.

| Method | Path | Input / behavior |
| --- | --- | --- |
| GET | `/care/dashboard` | Scoped pending/overdue activities, referrals, unscheduled referrals, seven-day follow-ups and completed plans |
| GET, POST | `/care/plans` | List; create using confirmed/completed `appointmentId`, `title`, `goals` |
| GET, PUT | `/care/plans/{id}` | Read; update `version`, `title`, `goals` |
| PATCH | `/care/plans/{id}/status` | `version`, COMPLETED/CANCELLED `status`; all activities/referrals must be closed |
| GET, POST | `/care/plans/{id}/activities` | Paged list; create `type`, `title`, future `dueAt` |
| PUT | `/care/plans/{planId}/activities/{id}` | `version`, `title`, future `dueAt` |
| PATCH | `/care/plans/{planId}/activities/{id}/status` | `version`, `status`, `outcome` required on completion |
| POST | `/care/plans/{id}/referrals` | `receivingProviderId`, `clinicalReason`, ROUTINE/URGENT `priority`, future `dueAt` |
| GET | `/care/referrals`, `/care/referrals/{id}` | Scoped tracking; receiver sees the referral, not the full source plan |
| PATCH | `/care/referrals/{id}/status` | `version`, `status`, `appointmentId` on scheduling, `outcome` on completion |
| PATCH | `/care/referrals/{id}/assignment` | Referring provider: `version`, `receivingProviderId` |
| GET | `/care/journey/{patientId}` | `type`, `status`, `from` inclusive, `to` exclusive, `page`, `size` |
| GET, POST | `/recovery/waiting-list` | Own latest 100 entries (array); create provider OR specialty preference, availability, notification preference |
| DELETE | `/recovery/waiting-list/{id}` | Remove own entry; 204 |
| PATCH | `/recovery/waiting-list/{id}/priority` | Admin: `priority` 0–10; 204 |
| GET | `/recovery/offers` | Own latest 100 offers (array), effective expiry/inactive status |
| POST | `/recovery/offers/{id}/accept` | UUID `idempotencyKey`; returns appointment DTO; successful retry returns same booking |
| GET | `/care/gaps` | Scoped alerts; `resolved=false|true`, `page`, `size` |
| GET, POST | `/care/gaps/rules` | Provider's own/admin global rules (latest 100 array); create explicit rule |
| PATCH | `/care/gaps/rules/{id}` | Owner/admin: `version`, `enabled`; starts new revision, resolves prior alerts; 204 |
| POST | `/care/gaps/evaluate` | Admin scan; 204; scheduler also runs every minute |
| GET | `/fhir/r4/care-plans/{id}/bundle` | Validated `application/fhir+json`; same care-plan authorization |

`/notifications` now combines both inboxes. Fields: `id`, nullable `appointmentId`, `entityId`, `eventType`, `createdAt`. Clients must handle care notices without appointment IDs. `/actuator/metrics/**` requires ADMIN; public `/actuator/info` exposes configured release revision only.

Synthetic request examples, with UUIDs replaced by actual API results:

```http
POST /api/v1/care/plans
Authorization: Bearer <doctor-token>
Content-Type: application/json

{"appointmentId":"00000000-0000-0000-0000-000000000001","title":"Synthetic review plan","goals":"Complete the provider-defined review."}
```

The 201 plan DTO contains IDs, patient/provider display names, title, goals, `status: "ACTIVE"`, `version: 0`, creation time and activity/referral progress counts. Creation requires the acting doctor's confirmed/completed visit, not an arbitrary patient UUID.

```json
{"type":"FOLLOW_UP","title":"Provider review","dueAt":"2026-12-01T09:00:00Z"}
```

Activities follow NOT_STARTED → SCHEDULED/IN_PROGRESS → COMPLETED, with cancellation before completion. OVERDUE is derived without destroying lifecycle state. Completion requires a recorded outcome; terminal states cannot reopen.

```json
{"version":1,"status":"COMPLETED","outcome":"Synthetic provider review completed."}
```

Referrals follow CREATED → PENDING_ACCEPTANCE → ACCEPTED → SCHEDULED → IN_PROGRESS → COMPLETED. The sender submits; the receiver accepts/executes/completes. Scheduling verifies patient/provider and confirmed appointment; completion verifies actual appointment completion and an outcome. Kafka reopens scheduled referrals to ACCEPTED after visit cancellation/rejection/rescheduling, clears the link and records system history. Reassignment requires cancellation of a linked active visit.

```json
{"doctorId":"00000000-0000-0000-0000-000000000002","availableFrom":"2026-12-01T09:00:00Z","availableTo":"2026-12-01T12:00:00Z","notifyInApp":true}
```

Keep the same UUID acceptance key across network retries. Offers last up to fifteen minutes, never beyond visit start. Competing, expired or inactive offers return 409; booking revalidates provider availability and database overlap constraints.

```json
{"name":"Provider-defined overdue review","activityType":"FOLLOW_UP","triggerCondition":"OVERDUE","intervalHours":24,"severity":"WARNING","notificationPolicy":"IN_APP","responsibleRole":"DOCTOR"}
```

Rules support FOLLOW_UP/INVESTIGATION/CONSULTATION/REFERRAL, OVERDUE or referral-only UNSCHEDULED, a 0–8760 hour grace period, INFO/WARNING/HIGH severity and IN_APP/NONE notifications. Grace is measured from deadline or acceptance/update time. Only admins route responsibility to ADMIN. Active alerts deduplicate; a resolved alert can reopen for a new scheduling gap, with fresh audit/outbox evidence. No clinical rules are seeded.

## Original appointment platform API

All paths below are relative to `/api/v1`. JSON inputs reject unknown fields. Successful creates return 201, deletes/logout return 204, other successful operations return 200. Paged responses have `content`, `totalElements`, `totalPages`, `number`, `size`. `page` is zero-based; `size` is capped at 100; `sort=field,asc|desc` is allowlisted per resource.

| Method | Path | Access / purpose |
| --- | --- | --- |
| POST | `/auth/register/patient`, `/auth/register/doctor` | Register; allowed Origin required |
| POST | `/auth/login`, `/auth/refresh`, `/auth/logout` | Login/session rotation/revocation; allowed Origin required |
| GET, PUT | `/patients/me` | Own patient profile |
| GET | `/doctors` | Public; `q`, `specialization`, page/sort |
| GET | `/doctors/specializations` | Public catalog |
| GET | `/doctors/{id}` | Public verified profile |
| GET | `/doctors/{id}/availability` | Public weekly windows |
| GET | `/doctors/{id}/slots?date=YYYY-MM-DD` | Live slots, date in clinic timezone |
| GET, PUT | `/doctors/me` | Own doctor profile |
| GET, POST | `/doctors/me/availability` | Own weekly availability |
| PUT, DELETE | `/doctors/me/availability/{id}` | Own window |
| POST | `/appointments` | Patient; `doctorId`, `startTime`, `endTime`, optional `reason` |
| GET | `/appointments/me`, `/doctors/me/appointments` | Own appointments; `status`, `from`, `to`, pagination/sort |
| GET | `/appointments/{id}` | Owning patient/doctor or admin |
| GET | `/appointments/{id}/patient` | Owning doctor only; minimal patient details |
| PATCH | `/appointments/{id}/cancel` | Owning patient/doctor |
| PATCH | `/appointments/{id}/reschedule` | Owning patient; new `startTime`, `endTime` |
| PATCH | `/appointments/{id}/confirm`, `/reject`, `/complete` | Owning doctor |
| GET | `/notifications` | Own persisted inbox |
| GET | `/admin/users`, `/admin/patients`, `/admin/doctors` | Admin; users optionally filtered by `role` |
| PATCH | `/admin/doctors/{id}/verify` | Admin; `{ "verified": true }` |
| PATCH | `/admin/users/{id}/enabled` | Admin; `{ "enabled": false }`; admin accounts excluded |
| GET | `/admin/appointments` | Admin; `status`, `doctorId`, `patientId`, `from`, `to` |
| GET | `/admin/analytics`, `/admin/audit-logs`, `/admin/outbox` | Admin dashboards/operations |
| POST | `/admin/outbox/{id}/retry` | Admin; failed events only |

For appointment range filters, `from` is inclusive and `to` exclusive. Public doctor sorts: `specialization`, `experienceYears`, `consultationFee`, `createdAt`; appointment sorts: `startTime`, `createdAt`, `status`. OpenAPI exposes validated DTO schemas and all endpoint operations.

```json
{
  "timestamp": "2026-11-05T10:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "The selected appointment slot is no longer available",
  "path": "/api/v1/appointments"
}
```

Validation errors use 400, missing/expired authentication 401, ownership/role failures 403, missing records 404, invalid state/concurrent conflicts 409. Responses do not expose stack traces or SQL exception messages. A server-generated `X-Request-ID` appears in structured logs for correlation.


## Request examples and session handling

Local base: http://localhost:8080/api/v1. Browser default: same-origin /api/v1. Swagger schemas: /swagger-ui/index.html and /v3/api-docs in development (disabled in production).

Register a patient:

```http
POST /api/v1/auth/register/patient
Origin: http://localhost:5173
Content-Type: application/json

{"fullName":"Example Patient","email":"patient@example.test","password":"<your-12-to-72-byte-password>"}
```

The response contains accessToken, expiresIn and user; the refresh token is only in a Set-Cookie header. Send `Authorization: Bearer <accessToken>` on protected routes. Doctor registration uses /auth/register/doctor and adds specialization, qualification and timezone (e.g. Asia/Kolkata). Verification and availability are required before booking.

```http
GET /api/v1/doctors?specialization=Cardiology&page=0&size=9

GET /api/v1/doctors/<doctor-uuid>/slots?date=2026-12-01

POST /api/v1/appointments
Authorization: Bearer <accessToken>
Content-Type: application/json

{"doctorId":"<doctor-uuid>","startTime":"<startTime-from-live-slot>","endTime":"<endTime-from-live-slot>","reason":"Consultation"}
```

Use a returned future slot within 90 days. A competing active interval returns 409. Reschedule with PATCH /appointments/<id>/reschedule and a new {startTime,endTime} object; cancel with PATCH /appointments/<id>/cancel and no body. IDs stay stable when rescheduled. Only the owning doctor can confirm/reject/complete; completion requires that end time has passed.

POST /auth/refresh requires the browser cookie and allowed Origin. Each refresh rotates the cookie; do not reuse old cookies. POST /auth/logout revokes the refresh family and clears the cookie; already-issued access tokens expire naturally within 15 minutes. API clients need a cookie jar and explicit Origin. Invalid timezone/missing parameters return 400; unsupported content types return 415 and unsupported methods return 405. Example UUID/time placeholders must be replaced with real API values.
