import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Activity, CalendarDays, ShieldCheck, Users } from "lucide-react";
import { api, get } from "../api";
import { dateTime, Empty, ErrorBox, Heading, Pager } from "../components/UI";
import type {
  Analytics,
  Appointment,
  Audit,
  Doctor,
  Outbox,
  Page,
  User,
} from "../types";

export function Overview() {
  const query = useQuery({
    queryKey: ["admin", "analytics"],
    queryFn: () => get<Analytics>("/admin/analytics"),
  });
  if (query.isPending)
    return <p className="loading">Loading platform overview…</p>;
  if (!query.data) return <ErrorBox error={query.error} />;
  const a = query.data;
  const max = Math.max(1, ...a.appointmentsByStatus.map((x) => x.count));
  return (
    <>
      <div className="stats-grid">
        {[
          { label: "Patients", value: a.patients, icon: Users },
          { label: "Doctors", value: a.doctors, icon: ShieldCheck },
          { label: "Appointments", value: a.appointments, icon: CalendarDays },
          {
            label: "Awaiting verification",
            value: a.unverifiedDoctors,
            icon: Activity,
          },
        ].map((s) => (
          <article className="stat-card" key={s.label}>
            <s.icon size={21} />
            <strong>{s.value}</strong>
            <span>{s.label}</span>
          </article>
        ))}
      </div>
      <div className="panel">
        <h2>Appointment activity</h2>
        {a.appointmentsByStatus.length === 0 ? (
          <Empty title="Your platform is ready">
            Appointment activity will appear as patients begin booking.
          </Empty>
        ) : (
          a.appointmentsByStatus.map((s) => (
            <div className="analytics-row" key={s.status}>
              <span>{s.status.toLowerCase()}</span>
              <meter min={0} max={max} value={s.count} aria-label={s.status} />
              <strong>{s.count}</strong>
            </div>
          ))
        )}
        {a.failedOutboxEvents > 0 && (
          <p className="notice">
            {a.failedOutboxEvents} outbox events need attention. Review the
            event queue.
          </p>
        )}
      </div>
    </>
  );
}
export function Admin() {
  const [tab, setTab] = useState("overview");
  return (
    <section className="page">
      <Heading
        eyebrow="PLATFORM ADMINISTRATION"
        title="A clear view of your care network"
      />
      <div className="tabs admin-tabs">
        {[
          "overview",
          "doctors",
          "users",
          "appointments",
          "audit",
          "events",
        ].map((t) => (
          <button
            key={t}
            className={tab === t ? "active" : ""}
            onClick={() => setTab(t)}
          >
            {t === "audit"
              ? "Audit logs"
              : t === "events"
                ? "Event queue"
                : t[0].toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>
      {tab === "overview" ? <Overview /> : <AdminTable key={tab} kind={tab} />}
    </section>
  );
}
function AdminTable({ kind }: { kind: string }) {
  const [page, setPage] = useState(0);
  const [role, setRole] = useState("");
  const [status, setStatus] = useState("");
  const [doctorId, setDoctorId] = useState("");
  const [patientId, setPatientId] = useState("");
  const [date, setDate] = useState("");
  const [success, setSuccess] = useState("");
  const cache = useQueryClient();
  const path =
    kind === "audit" ? "audit-logs" : kind === "events" ? "outbox" : kind;
  const params = {
    page,
    size: 15,
    role: role || undefined,
    status: status || undefined,
    doctorId: doctorId || undefined,
    patientId: patientId || undefined,
    from: date ? new Date(`${date}T00:00:00Z`).toISOString() : undefined,
    to: date
      ? new Date(+new Date(`${date}T00:00:00Z`) + 86400000).toISOString()
      : undefined,
    sort:
      kind === "audit"
        ? "timestamp,desc"
        : kind === "appointments"
          ? "startTime,desc"
          : "createdAt,desc",
  };
  const query = useQuery({
    queryKey: ["admin", path, params],
    queryFn: () =>
      get<Page<User | Doctor | Appointment | Audit | Outbox>>(
        `/admin/${path}`,
        params,
      ),
  });
  const mutation = useMutation({
    mutationFn: ({
      url,
      data,
      post,
    }: {
      url: string;
      data?: object;
      post?: boolean;
    }) => (post ? api.post(url) : api.patch(url, data)),
    onSuccess: () => {
      cache.invalidateQueries({ queryKey: ["admin"] });
      cache.invalidateQueries({ queryKey: ["doctors"] });
      setSuccess("Changes saved.");
    },
  });
  const userAction = (u: User) => {
    if (
      window.confirm(
        `${u.enabled ? "Disable" : "Enable"} ${u.fullName}'s account?`,
      )
    )
      mutation.mutate({
        url: `/admin/users/${u.id}/enabled`,
        data: { enabled: !u.enabled },
      });
  };
  return (
    <>
      <div className="toolbar">
        <h2>
          {kind === "events"
            ? "Outbox delivery"
            : kind === "audit"
              ? "Audit trail"
              : kind[0].toUpperCase() + kind.slice(1)}
        </h2>
        {kind === "users" && (
          <select
            aria-label="Filter user role"
            value={role}
            onChange={(e) => {
              setRole(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All roles</option>
            <option>PATIENT</option>
            <option>DOCTOR</option>
            <option>ADMIN</option>
          </select>
        )}
        {kind === "appointments" && (
          <div className="filter-fields">
            <select
              aria-label="Status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All statuses</option>
              {[
                "PENDING",
                "CONFIRMED",
                "COMPLETED",
                "CANCELLED",
                "REJECTED",
              ].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
            <input
              type="date"
              aria-label="Appointment date UTC"
              value={date}
              onChange={(e) => {
                setDate(e.target.value);
                setPage(0);
              }}
            />
            <input
              aria-label="Doctor ID filter"
              placeholder="Doctor UUID"
              value={doctorId}
              onChange={(e) => {
                setDoctorId(e.target.value);
                setPage(0);
              }}
            />
            <input
              aria-label="Patient ID filter"
              placeholder="Patient UUID"
              value={patientId}
              onChange={(e) => {
                setPatientId(e.target.value);
                setPage(0);
              }}
            />
          </div>
        )}
      </div>
      <ErrorBox error={query.error || mutation.error} />
      {success && (
        <p className="success" role="status">
          {success}
        </p>
      )}
      {query.isPending ? (
        <p className="loading">Loading records…</p>
      ) : query.data?.content.length === 0 ? (
        <Empty title="No records here yet">
          Try another filter or check back as your platform grows.
        </Empty>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                {(kind === "doctors"
                  ? ["Doctor", "Specialty", "Verification", "Action"]
                  : kind === "users"
                    ? ["Name", "Email", "Role", "Account", "Action"]
                    : kind === "appointments"
                      ? ["Patient", "Doctor", "Time", "Status"]
                      : kind === "audit"
                        ? ["Time", "Action", "Entity", "Actor"]
                        : ["Event", "Created", "Delivery", "Retries", "Action"]
                ).map((h) => (
                  <th key={h}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {query.data?.content.map((record) => {
                if (kind === "doctors") {
                  const d = record as Doctor;
                  return (
                    <tr key={d.id}>
                      <td>
                        <strong>{d.fullName}</strong>
                        <small>{d.qualification}</small>
                        <small>{d.id}</small>
                      </td>
                      <td>{d.specialization}</td>
                      <td>
                        <span
                          className={`status ${d.verified ? "confirmed" : "pending"}`}
                        >
                          {d.verified ? "Verified" : "Pending review"}
                        </span>
                      </td>
                      <td>
                        <button
                          className="btn secondary small"
                          disabled={mutation.isPending}
                          onClick={() =>
                            mutation.mutate({
                              url: `/admin/doctors/${d.id}/verify`,
                              data: { verified: !d.verified },
                            })
                          }
                        >
                          {d.verified ? "Remove verification" : "Verify doctor"}
                        </button>
                      </td>
                    </tr>
                  );
                }
                if (kind === "users") {
                  const u = record as User;
                  return (
                    <tr key={u.id}>
                      <td>
                        <strong>{u.fullName}</strong>
                        <small>{u.id}</small>
                      </td>
                      <td>{u.email}</td>
                      <td>{u.role.toLowerCase()}</td>
                      <td>
                        <span
                          className={`status ${u.enabled ? "confirmed" : "cancelled"}`}
                        >
                          {u.enabled ? "Enabled" : "Disabled"}
                        </span>
                      </td>
                      <td>
                        {u.role !== "ADMIN" && (
                          <button
                            className="text-button"
                            disabled={mutation.isPending}
                            onClick={() => userAction(u)}
                          >
                            {u.enabled ? "Disable" : "Enable"}
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                }
                if (kind === "appointments") {
                  const a = record as Appointment;
                  return (
                    <tr key={a.id}>
                      <td>
                        {a.patientName}
                        <small>{a.patientId}</small>
                      </td>
                      <td>
                        {a.doctorName}
                        <small>{a.doctorId}</small>
                      </td>
                      <td>{dateTime(a.startTime)}</td>
                      <td>
                        <span className={`status ${a.status.toLowerCase()}`}>
                          {a.status.toLowerCase()}
                        </span>
                      </td>
                    </tr>
                  );
                }
                if (kind === "audit") {
                  const a = record as Audit;
                  return (
                    <tr key={a.id}>
                      <td>{dateTime(a.timestamp)}</td>
                      <td>{a.action.toLowerCase().replaceAll("_", " ")}</td>
                      <td>
                        {a.entityType}
                        <small>{a.entityId}</small>
                      </td>
                      <td>
                        <small>
                          {a.actorId ?? "Anonymous authentication attempt"}
                        </small>
                      </td>
                    </tr>
                  );
                }
                const e = record as Outbox;
                return (
                  <tr key={e.id}>
                    <td>
                      {e.eventType.toLowerCase().replaceAll("_", " ")}
                      <small>{e.id}</small>
                    </td>
                    <td>{dateTime(e.createdAt)}</td>
                    <td>
                      <span
                        className={`status ${e.status === "PUBLISHED" ? "confirmed" : e.status === "FAILED" ? "rejected" : "pending"}`}
                      >
                        {e.status.toLowerCase()}
                      </span>
                    </td>
                    <td>{e.retryCount}</td>
                    <td>
                      {e.status === "FAILED" && (
                        <button
                          className="btn secondary small"
                          disabled={mutation.isPending}
                          onClick={() =>
                            mutation.mutate({
                              url: `/admin/outbox/${e.id}/retry`,
                              post: true,
                            })
                          }
                        >
                          Retry delivery
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      <Pager data={query.data} onPage={setPage} />
    </>
  );
}
