# API documentation

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
