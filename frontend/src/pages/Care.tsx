import { useState } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api, get } from "../api";
import { useAuth } from "../auth";
import {
  dateTime,
  Empty,
  ErrorBox,
  Field,
  Heading,
  Pager,
} from "../components/UI";
import {
  WorkflowForm,
  formText,
  formInstant,
  localInput,
} from "../components/WorkflowForm";
import type { Appointment, Doctor, Page, Patient } from "../types";
import type {
  CarePlan,
  CareActivity,
  Referral,
  JourneyEvent,
} from "../careTypes";

const pretty = (text: string) => text.toLowerCase().replaceAll("_", " ");
const terminal = (status: string) =>
  ["COMPLETED", "CANCELLED"].includes(status);
function Status({ value }: { value: string }) {
  return (
    <span
      className={`status ${value === "COMPLETED" ? "completed" : value === "CANCELLED" ? "cancelled" : "pending"}`}
    >
      {pretty(value)}
    </span>
  );
}
function ProviderSelect({ name = "receivingProviderId" }: { name?: string }) {
  const [search, setSearch] = useState("");
  const query = useQuery({
    queryKey: ["care", "providers", search],
    queryFn: () => get<Page<Doctor>>("/doctors", { q: search, size: 100 }),
  });
  return (
    <>
      <Field label="Find receiving provider">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by name"
        />
      </Field>
      <Field label="Receiving provider">
        <select name={name} required defaultValue="">
          <option value="">Choose a provider</option>
          {query.data?.content.map((d) => (
            <option key={d.id} value={d.id}>
              {d.fullName} · {d.specialization}
            </option>
          ))}
        </select>
      </Field>
      <ErrorBox error={query.error} />
    </>
  );
}
export function CarePlans() {
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ["care", "plans", page],
    queryFn: () => get<Page<CarePlan>>("/care/plans", { page, size: 12 }),
  });
  const { user } = useAuth();
  return (
    <section className="page">
      <Heading eyebrow="CONNECTED CARE" title="Care plans">
        <Link className="btn secondary" to="/referrals">
          View referrals
        </Link>
      </Heading>
      <p className="intro">
        Goals, next steps, and progress shared with your care team.
      </p>
      {user?.role === "DOCTOR" && (
        <p className="notice">
          Start a plan from a confirmed or completed visit in{" "}
          <Link to="/appointments">Appointments</Link>.
        </p>
      )}
      <ErrorBox error={query.error} />
      {query.isPending ? (
        <p className="loading">Loading care plans…</p>
      ) : query.data?.content.length === 0 ? (
        <Empty title="No care plans yet">
          Plans created by your responsible provider will appear here.
        </Empty>
      ) : (
        <div className="doctor-grid">
          {query.data?.content.map((p) => (
            <article className="panel" key={p.id}>
              <Status value={p.status} />
              <h2>
                <Link to={`/care/${p.id}`}>{p.title}</Link>
              </h2>
              <p>
                {p.patientName} · {p.providerName}
              </p>
              <label className="care-progress">
                Completed care{" "}
                <progress
                  max={Math.max(1, p.totalActivities + p.totalReferrals)}
                  value={p.completedActivities + p.completedReferrals}
                />
              </label>
              <p>
                {p.completedActivities + p.completedReferrals} of{" "}
                {p.totalActivities + p.totalReferrals} activities and referrals
                completed
              </p>
              <Link className="text-link" to={`/care/${p.id}`}>
                Review plan →
              </Link>
            </article>
          ))}
        </div>
      )}
      <Pager data={query.data} onPage={setPage} />
      {user?.role === "PATIENT" && (
        <Link className="btn secondary" to="/journey">
          Your care journey
        </Link>
      )}
    </section>
  );
}
export function NewCarePlan() {
  const [params] = useSearchParams();
  const appointmentId = params.get("appointment");
  const navigate = useNavigate();
  const appointment = useQuery({
    queryKey: ["appointments", appointmentId],
    queryFn: () => get<Appointment>(`/appointments/${appointmentId}`),
    enabled: !!appointmentId,
  });
  return (
    <section className="page">
      <Heading eyebrow="CARE COORDINATION" title="Create a care plan" />
      <ErrorBox error={appointment.error} />
      {!appointmentId ? (
        <Empty title="Choose a patient visit">
          <Link to="/appointments">Open appointments</Link>
        </Empty>
      ) : appointment.isPending ? (
        <p>Loading visit…</p>
      ) : (
        appointment.data && (
          <div className="panel">
            <p>
              For <strong>{appointment.data.patientName}</strong> ·{" "}
              {dateTime(appointment.data.startTime)}
            </p>
            <WorkflowForm
              path="/care/plans"
              label="Create plan"
              body={(d) => ({
                appointmentId,
                title: formText(d, "title"),
                goals: formText(d, "goals"),
              })}
              onSaved={(data) => navigate(`/care/${(data as CarePlan).id}`)}
            >
              <Field label="Plan title">
                <input name="title" required maxLength={200} />
              </Field>
              <Field label="Care goals">
                <textarea name="goals" required maxLength={4000} rows={4} />
              </Field>
            </WorkflowForm>
          </div>
        )
      )}
    </section>
  );
}
export function CarePlanDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [exportError, setExportError] = useState<unknown>(null);
  const [exporting, setExporting] = useState(false);
  const query = useQuery({
    queryKey: ["care", "plan", id],
    queryFn: () => get<CarePlan>(`/care/plans/${id}`),
  });
  const profile = useQuery({
    queryKey: ["care", "my-provider"],
    queryFn: () => get<Doctor>("/doctors/me"),
    enabled: user?.role === "DOCTOR",
  });
  const activities = useQuery({
    queryKey: ["care", "activities", id, page],
    queryFn: () =>
      get<Page<CareActivity>>(`/care/plans/${id}/activities`, {
        page,
        size: 10,
      }),
  });
  if (query.isPending)
    return (
      <section className="page">
        <p>Loading plan…</p>
      </section>
    );
  if (!query.data)
    return (
      <section className="page">
        <ErrorBox error={query.error} />
      </section>
    );
  const p = query.data;
  const writable = p.status === "ACTIVE" && profile.data?.id === p.providerId;
  return (
    <section className="page">
      <Heading eyebrow="CARE PLAN" title={p.title}>
        <Link className="btn secondary" to="/care">
          All plans
        </Link>
      </Heading>
      <div className="panel">
        <Status value={p.status} />
        <p>
          {p.patientName} · Responsible provider: {p.providerName}
        </p>
        <h2>Goals</h2>
        <p className="care-text">{p.goals}</p>
        <p>
          {p.completedActivities} / {p.totalActivities} activities and{" "}
          {p.completedReferrals} / {p.totalReferrals} referrals completed.
        </p>
        <div className="row-actions">
          <Link to={`/journey/${p.patientId}`} className="text-link">
            Care journey
          </Link>
          <Link to="/referrals" className="text-link">
            Referral tracking
          </Link>
          <button
            className="text-button"
            disabled={exporting}
            onClick={async () => {
              setExporting(true);
              setExportError(null);
              try {
                const response = await api.get(
                  `/fhir/r4/care-plans/${p.id}/bundle`,
                  { responseType: "blob" },
                );
                const url = URL.createObjectURL(response.data);
                const a = document.createElement("a");
                a.href = url;
                a.download = `care-plan-${p.id}.fhir.json`;
                a.click();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
              } catch (e) {
                setExportError(e);
              } finally {
                setExporting(false);
              }
            }}
          >
            {exporting ? "Preparing export…" : "Download FHIR R4"}
          </button>
        </div>
        <ErrorBox error={exportError} />
        {writable && (
          <details>
            <summary>Edit goals or close plan</summary>
            <WorkflowForm
              key={p.version}
              path={`/care/plans/${id}`}
              method="put"
              label="Save plan"
              body={(d) => ({
                version: p.version,
                title: formText(d, "title"),
                goals: formText(d, "goals"),
              })}
            >
              <Field label="Plan title">
                <input
                  name="title"
                  required
                  maxLength={200}
                  defaultValue={p.title}
                />
              </Field>
              <Field label="Care goals">
                <textarea
                  name="goals"
                  required
                  maxLength={4000}
                  defaultValue={p.goals}
                />
              </Field>
            </WorkflowForm>
            <p className="muted">
              Complete or cancel every activity and referral before closing the
              plan.
            </p>
            <WorkflowForm
              path={`/care/plans/${id}/status`}
              method="patch"
              label="Close plan"
              body={(d) => ({
                version: p.version,
                status: formText(d, "status"),
              })}
            >
              <Field label="Close as">
                <select name="status">
                  <option>COMPLETED</option>
                  <option>CANCELLED</option>
                </select>
              </Field>
            </WorkflowForm>
          </details>
        )}
      </div>
      <h2>Activities</h2>
      <ErrorBox error={activities.error || profile.error} />
      {activities.isPending ? (
        <p>Loading activities…</p>
      ) : activities.data?.content.length === 0 ? (
        <Empty title="No activities yet" />
      ) : (
        activities.data?.content.map((a) => (
          <ActivityCard
            key={`${a.id}-${a.version}`}
            activity={a}
            writable={writable}
          />
        ))
      )}
      <Pager data={activities.data} onPage={setPage} />
      {writable && (
        <div className="care-columns">
          <div className="panel">
            <h2>Add an activity</h2>
            <WorkflowForm
              path={`/care/plans/${id}/activities`}
              label="Add activity"
              body={(d) => ({
                type: formText(d, "type"),
                title: formText(d, "title"),
                dueAt: formInstant(d, "dueAt"),
              })}
            >
              <Field label="Activity type">
                <select name="type">
                  <option value="FOLLOW_UP">Follow-up</option>
                  <option value="INVESTIGATION">Investigation</option>
                  <option value="CONSULTATION">Consultation</option>
                </select>
              </Field>
              <Field label="Activity title">
                <input name="title" required maxLength={200} />
              </Field>
              <Field label="Activity deadline">
                <input name="dueAt" type="datetime-local" required />
              </Field>
            </WorkflowForm>
          </div>
          <div className="panel">
            <h2>Create a referral</h2>
            <WorkflowForm
              path={`/care/plans/${id}/referrals`}
              label="Create referral"
              body={(d) => ({
                receivingProviderId: formText(d, "receivingProviderId"),
                clinicalReason: formText(d, "clinicalReason"),
                priority: formText(d, "priority"),
                dueAt: formInstant(d, "dueAt"),
              })}
            >
              <ProviderSelect />
              <Field label="Clinical reason">
                <textarea name="clinicalReason" required maxLength={2000} />
              </Field>
              <Field label="Referral priority">
                <select name="priority">
                  <option>ROUTINE</option>
                  <option>URGENT</option>
                </select>
              </Field>
              <Field label="Referral deadline">
                <input type="datetime-local" name="dueAt" required />
              </Field>
            </WorkflowForm>
          </div>
        </div>
      )}
    </section>
  );
}
function ActivityCard({
  activity: a,
  writable,
}: {
  activity: CareActivity;
  writable: boolean;
}) {
  const next: Record<string, string[]> = {
    NOT_STARTED: ["SCHEDULED", "IN_PROGRESS", "CANCELLED"],
    SCHEDULED: ["IN_PROGRESS", "CANCELLED"],
    IN_PROGRESS: ["COMPLETED", "CANCELLED"],
  };
  return (
    <article className="panel">
      <div className="section-heading">
        <h3>{a.title}</h3>
        <Status value={a.status} />
      </div>
      <p>
        {pretty(a.type)} · Due {dateTime(a.dueAt)}{" "}
        {a.overdue && <strong className="care-overdue">Overdue</strong>}
      </p>
      {a.outcome && <p className="care-text">Outcome: {a.outcome}</p>}
      {writable && !terminal(a.status) && (
        <details>
          <summary>Manage activity</summary>
          <WorkflowForm
            path={`/care/plans/${a.planId}/activities/${a.id}/status`}
            method="patch"
            label="Update activity status"
            body={(d) => ({
              version: a.version,
              status: formText(d, "status"),
              outcome: formText(d, "outcome"),
            })}
          >
            <Field label="Next activity status">
              <select name="status">
                {next[a.status]?.map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
            </Field>
            <Field label="Outcome (required for completion)">
              <textarea name="outcome" maxLength={4000} />
            </Field>
          </WorkflowForm>
          <WorkflowForm
            path={`/care/plans/${a.planId}/activities/${a.id}`}
            method="put"
            label="Save activity details"
            body={(d) => ({
              version: a.version,
              title: formText(d, "title"),
              dueAt: formInstant(d, "dueAt"),
            })}
          >
            <Field label="Activity title">
              <input
                name="title"
                defaultValue={a.title}
                required
                maxLength={200}
              />
            </Field>
            <Field label="Activity deadline">
              <input
                type="datetime-local"
                name="dueAt"
                required
                defaultValue={localInput(a.dueAt)}
              />
            </Field>
          </WorkflowForm>
        </details>
      )}
    </article>
  );
}
export function Referrals() {
  const [page, setPage] = useState(0);
  const { user } = useAuth();
  const query = useQuery({
    queryKey: ["referrals", page],
    queryFn: () => get<Page<Referral>>("/care/referrals", { page, size: 10 }),
  });
  const profile = useQuery({
    queryKey: ["care", "my-provider"],
    queryFn: () => get<Doctor>("/doctors/me"),
    enabled: user?.role === "DOCTOR",
  });
  return (
    <section className="page">
      <Heading eyebrow="CLOSED-LOOP CARE" title="Referrals" />
      <p className="intro">
        Follow each referral through acceptance, the visit, and a recorded
        outcome.
      </p>
      <ErrorBox error={query.error || profile.error} />
      {query.isPending ? (
        <p>Loading referrals…</p>
      ) : query.data?.content.length === 0 ? (
        <Empty title="No referrals yet" />
      ) : (
        query.data?.content.map((r) => (
          <ReferralCard
            key={`${r.id}-${r.version}`}
            referral={r}
            provider={profile.data?.id}
          />
        ))
      )}
      <Pager data={query.data} onPage={setPage} />
    </section>
  );
}
function ReferralCard({
  referral: r,
  provider,
}: {
  referral: Referral;
  provider?: string;
}) {
  const sender = provider === r.referringProviderId,
    receiver = provider === r.receivingProviderId;
  const appointments = useQuery({
    queryKey: ["appointments", "referral", r.id],
    queryFn: () =>
      get<Page<Appointment>>("/appointments/me", {
        size: 100,
        sort: "startTime,desc",
      }),
    enabled: receiver && r.status === "ACCEPTED",
  });
  const next: Record<string, string> = {
    CREATED: "PENDING_ACCEPTANCE",
    PENDING_ACCEPTANCE: "ACCEPTED",
    ACCEPTED: "SCHEDULED",
    SCHEDULED: "IN_PROGRESS",
    IN_PROGRESS: "COMPLETED",
  };
  const canAdvance = r.status === "CREATED" ? sender : receiver;
  return (
    <article className="panel">
      <div className="section-heading">
        <h2>{r.patientName}</h2>
        <Status value={r.status} />
      </div>
      <p>
        {r.referringProviderName} → {r.receivingProviderName}
      </p>
      <p>
        {pretty(r.priority)} · Due {dateTime(r.dueAt)}
      </p>
      <p className="care-text">{r.clinicalReason}</p>
      {r.outcome && <p className="care-text">Outcome: {r.outcome}</p>}
      <Link to={`/journey/${r.patientId}`} className="text-link">
        View care journey
      </Link>
      {(sender || receiver) && !terminal(r.status) && (
        <details>
          <summary>Manage referral</summary>
          {canAdvance && next[r.status] && (
            <WorkflowForm
              path={`/care/referrals/${r.id}/status`}
              method="patch"
              label={`Mark ${pretty(next[r.status])}`}
              body={(d) => ({
                version: r.version,
                status: next[r.status],
                ...(r.status === "ACCEPTED"
                  ? { appointmentId: formText(d, "appointmentId") }
                  : {}),
                outcome: formText(d, "outcome"),
              })}
            >
              {r.status === "ACCEPTED" && (
                <>
                  <Field label="Confirmed patient appointment">
                    <select name="appointmentId" required defaultValue="">
                      <option value="">Choose a matching visit</option>
                      {appointments.data?.content
                        .filter(
                          (a) =>
                            a.patientId === r.patientId &&
                            ["CONFIRMED", "COMPLETED"].includes(a.status),
                        )
                        .map((a) => (
                          <option key={a.id} value={a.id}>
                            {dateTime(a.startTime)} · {pretty(a.status)}
                          </option>
                        ))}
                    </select>
                  </Field>
                  <ErrorBox error={appointments.error} />
                  <p className="muted">
                    The patient books with the receiving provider first. Confirm
                    the appointment before linking it here.
                  </p>
                </>
              )}
              {r.status === "IN_PROGRESS" && (
                <Field label="Referral outcome">
                  <textarea name="outcome" required maxLength={4000} />
                </Field>
              )}
            </WorkflowForm>
          )}
          <WorkflowForm
            path={`/care/referrals/${r.id}/status`}
            method="patch"
            label="Cancel referral"
            body={() => ({ version: r.version, status: "CANCELLED" })}
          />
          {sender &&
            !["IN_PROGRESS", "COMPLETED", "CANCELLED"].includes(r.status) && (
              <WorkflowForm
                path={`/care/referrals/${r.id}/assignment`}
                method="patch"
                label="Reassign referral"
                body={(d) => ({
                  version: r.version,
                  receivingProviderId: formText(d, "receivingProviderId"),
                })}
              >
                <ProviderSelect />
                <p className="muted">
                  Cancel any linked visit before reassignment.
                </p>
              </WorkflowForm>
            )}
        </details>
      )}
    </article>
  );
}
export function CareJourney() {
  const { patientId: routePatient } = useParams();
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [type, setType] = useState("");
  const [status, setStatus] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const patient = useQuery({
    queryKey: ["care", "my-patient"],
    queryFn: () => get<Patient>("/patients/me"),
    enabled: user?.role === "PATIENT" && !routePatient,
  });
  const patientId = routePatient ?? patient.data?.id;
  const query = useQuery({
    queryKey: ["journey", patientId, page, type, status, from, to],
    queryFn: () =>
      get<Page<JourneyEvent>>(`/care/journey/${patientId}`, {
        page,
        size: 20,
        type: type || undefined,
        status: status || undefined,
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
      }),
    enabled: !!patientId,
  });
  return (
    <section className="page">
      <Heading eyebrow="YOUR CARE, IN ORDER" title="Care journey" />
      <div className="toolbar care-filters">
        <Field label="Activity filter">
          <select
            value={type}
            onChange={(e) => {
              setType(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All activities</option>
            {[
              "CARE_PLAN",
              "REFERRAL",
              "FOLLOW_UP",
              "INVESTIGATION",
              "CONSULTATION",
              "APPOINTMENT",
              "NOTIFICATION",
            ].map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </Field>
        <Field label="Status filter">
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            {[
              "ACTIVE",
              "NOT_STARTED",
              "PENDING",
              "CONFIRMED",
              "PENDING_ACCEPTANCE",
              "ACCEPTED",
              "SCHEDULED",
              "IN_PROGRESS",
              "COMPLETED",
              "CANCELLED",
              "DELIVERED",
            ].map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </Field>
        <Field label="From">
          <input
            type="datetime-local"
            value={from}
            onChange={(e) => {
              setFrom(e.target.value);
              setPage(0);
            }}
          />
        </Field>
        <Field label="Before">
          <input
            type="datetime-local"
            value={to}
            onChange={(e) => {
              setTo(e.target.value);
              setPage(0);
            }}
          />
        </Field>
      </div>
      <ErrorBox error={query.error || patient.error} />
      {query.isPending ? (
        <p>Loading care journey…</p>
      ) : query.data?.content.length === 0 ? (
        <Empty title="No matching care events" />
      ) : (
        <ol className="care-timeline">
          {query.data?.content.map((e) => (
            <li key={e.id}>
              <time>{dateTime(e.occurredAt)}</time>
              <h3>{pretty(e.action)}</h3>
              <Status value={e.status} />
            </li>
          ))}
        </ol>
      )}
      <Pager data={query.data} onPage={setPage} />
    </section>
  );
}
