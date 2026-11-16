import { PatientDetails } from "../components/PatientDetails";
import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, ArrowUpRight } from "lucide-react";
import { api, get } from "../api";
import { useAuth } from "../auth";
import { dateTime, Empty, ErrorBox, Heading, Pager } from "../components/UI";
import type { Appointment, Page, Status } from "../types";

export function Appointments() {
  const { user } = useAuth();
  const [tab, setTab] = useState("upcoming");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const cache = useQueryClient();
  const location = useLocation();
  const [boundary] = useState(() => new Date().toISOString());
  const query = useQuery({
    queryKey: ["appointments", tab, status, page],
    queryFn: () =>
      get<Page<Appointment>>("/appointments/me", {
        page,
        size: 10,
        status: status || undefined,
        from: tab === "upcoming" ? boundary : undefined,
        to: tab === "history" ? boundary : undefined,
        sort: tab === "history" ? "startTime,desc" : "startTime,asc",
      }),
  });
  const [success, setSuccess] = useState<string>(location.state?.success ?? "");
  const change = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) =>
      api.patch(`/appointments/${id}/${action}`),
    onSuccess: () => {
      setSuccess("Appointment updated.");
      cache.invalidateQueries({ queryKey: ["appointments"] });
      cache.invalidateQueries({ queryKey: ["slots"] });
    },
  });
  return (
    <section className="page">
      <Heading eyebrow="YOUR CARE, ORGANIZED" title="Appointments">
        <Link className="btn" to="/doctors">
          Find a doctor <ArrowUpRight size={17} />
        </Link>
      </Heading>
      <p className="intro">
        {user?.role === "DOCTOR"
          ? "Review requests and keep your care schedule up to date."
          : "A little clarity for every step of your care journey."}
      </p>
      {success && (
        <div role="status" className="success">
          {success}
        </div>
      )}
      <div className="toolbar">
        <div className="tabs">
          {["upcoming", "history", "all"].map((t) => (
            <button
              key={t}
              className={tab === t ? "active" : ""}
              onClick={() => {
                setTab(t);
                setPage(0);
              }}
            >
              {t === "all"
                ? "All appointments"
                : t[0].toUpperCase() + t.slice(1)}
            </button>
          ))}
        </div>
        <select
          aria-label="Filter appointment status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All statuses</option>
          {(
            [
              "PENDING",
              "CONFIRMED",
              "CANCELLED",
              "COMPLETED",
              "REJECTED",
            ] as Status[]
          ).map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
      </div>
      <ErrorBox error={query.error || change.error} />
      {query.isPending ? (
        <div className="loading">Loading appointments…</div>
      ) : query.data?.content.length === 0 ? (
        <Empty title="Nothing on the calendar here">
          Your appointments will appear here. Try another filter or find a
          doctor to get started.
        </Empty>
      ) : (
        <div className="appointment-list">
          {query.data?.content.map((a) => {
            const active = ["PENDING", "CONFIRMED"].includes(a.status);
            const future = new Date(a.startTime) > new Date();
            return (
              <article key={a.id} className="appointment-card">
                <div className="calendar-tile">
                  <CalendarDays size={22} />
                  <strong>{new Date(a.startTime).getDate()}</strong>
                  <span>
                    {new Date(a.startTime).toLocaleDateString(undefined, {
                      month: "short",
                    })}
                  </span>
                </div>
                <div className="appointment-info">
                  <span className={`status ${a.status.toLowerCase()}`}>
                    {a.status.toLowerCase()}
                  </span>
                  <h3>
                    {user?.role === "DOCTOR" ? a.patientName : a.doctorName}
                  </h3>
                  <p>
                    {dateTime(a.startTime)} ·{" "}
                    {Math.round(
                      (+new Date(a.endTime) - +new Date(a.startTime)) / 60000,
                    )}{" "}
                    minutes
                  </p>
                  {user?.role === "DOCTOR" && (
                    <PatientDetails appointmentId={a.id} />
                  )}{" "}
                  {a.reason && <p className="reason">{a.reason}</p>}
                  <small className="muted">
                    Appointment #{a.id.slice(0, 8)}
                  </small>
                </div>
                <div className="appointment-actions">
                  {user?.role === "DOCTOR" &&
                    ["CONFIRMED", "COMPLETED"].includes(a.status) && (
                      <Link
                        className="btn secondary small"
                        to={`/care/new?appointment=${a.id}`}
                      >
                        Create care plan
                      </Link>
                    )}
                  {user?.role === "PATIENT" && active && future && (
                    <Link
                      className="btn secondary small"
                      to={`/doctors/${a.doctorId}?reschedule=${a.id}`}
                    >
                      Reschedule
                    </Link>
                  )}
                  {user?.role === "DOCTOR" &&
                    a.status === "PENDING" &&
                    future && (
                      <>
                        <button
                          className="btn small"
                          disabled={change.isPending}
                          onClick={() =>
                            change.mutate({ id: a.id, action: "confirm" })
                          }
                        >
                          Confirm
                        </button>
                        <button
                          className="btn secondary small"
                          disabled={change.isPending}
                          onClick={() =>
                            change.mutate({ id: a.id, action: "reject" })
                          }
                        >
                          Reject
                        </button>
                      </>
                    )}
                  {user?.role === "DOCTOR" &&
                    a.status === "CONFIRMED" &&
                    new Date(a.endTime) <= new Date() && (
                      <button
                        className="btn small"
                        disabled={change.isPending}
                        onClick={() =>
                          change.mutate({ id: a.id, action: "complete" })
                        }
                      >
                        Mark completed
                      </button>
                    )}
                  {active && future && (
                    <button
                      className="text-button danger"
                      disabled={change.isPending}
                      onClick={() => {
                        if (
                          window.confirm(
                            "Cancel this appointment? The slot will become available to other patients.",
                          )
                        )
                          change.mutate({ id: a.id, action: "cancel" });
                      }}
                    >
                      Cancel appointment
                    </button>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
      <Pager data={query.data} onPage={setPage} />
      <p className="muted small-text">
        All appointment times are displayed in{" "}
        {Intl.DateTimeFormat().resolvedOptions().timeZone}.
      </p>
    </section>
  );
}
