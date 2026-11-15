import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowUpRight,
  Bell,
  CalendarDays,
  Search,
  UserRound,
} from "lucide-react";
import { useAuth } from "../auth";
import { get } from "../api";
import { dateTime, Empty, ErrorBox, Heading } from "../components/UI";
import type { Appointment, Notification, Page } from "../types";
import { Overview } from "./Admin";
export function Dashboard() {
  const { user } = useAuth();
  const appointments = useQuery({
    queryKey: ["appointments", "dashboard"],
    queryFn: () =>
      get<Page<Appointment>>("/appointments/me", {
        from: new Date().toISOString(),
        sort: "startTime,asc",
        size: 5,
      }),
    enabled: user?.role !== "ADMIN",
  });
  const notifications = useQuery({
    queryKey: ["notifications"],
    queryFn: () =>
      get<Page<Notification>>("/notifications", {
        sort: "createdAt,desc",
        size: 8,
      }),
    refetchInterval: 15000,
  });
  return (
    <section className="page">
      <Heading
        eyebrow={new Date().toLocaleDateString(undefined, {
          weekday: "long",
          month: "long",
          day: "numeric",
        })}
        title={`Hello, ${user?.fullName.split(" ")[0]}.`}
      >
        <span className="subtle-tag">Your care workspace</span>
      </Heading>
      <p className="intro">
        {user?.role === "DOCTOR"
          ? "Here’s what’s ahead for you and your patients."
          : user?.role === "ADMIN"
            ? "A clear view of your platform and the people who rely on it."
            : "It’s a good day to take care of yourself."}
      </p>
      {user?.role === "ADMIN" ? (
        <Overview />
      ) : (
        <>
          <div className="quick-actions">
            <Link to={user?.role === "DOCTOR" ? "/schedule" : "/doctors"}>
              <span className="action-icon">
                <Search />
              </span>
              <div>
                <h3>
                  {user?.role === "DOCTOR"
                    ? "Manage availability"
                    : "Find your next doctor"}
                </h3>
                <p>
                  {user?.role === "DOCTOR"
                    ? "Make your week work for you"
                    : "Care that fits your needs"}
                </p>
              </div>
              <ArrowUpRight />
            </Link>
            <Link to="/appointments">
              <span className="action-icon">
                <CalendarDays />
              </span>
              <div>
                <h3>Your appointments</h3>
                <p>Every visit, in one place</p>
              </div>
              <ArrowUpRight />
            </Link>
            <Link to="/profile">
              <span className="action-icon">
                <UserRound />
              </span>
              <div>
                <h3>Keep in touch</h3>
                <p>Review your profile details</p>
              </div>
              <ArrowUpRight />
            </Link>
          </div>
          <section className="panel">
            <div className="section-heading">
              <h2>Coming up next</h2>
              <Link to="/appointments" className="text-link">
                View all →
              </Link>
            </div>
            <ErrorBox error={appointments.error} />
            {appointments.isPending ? (
              <p>Loading appointments…</p>
            ) : appointments.data?.content.length === 0 ? (
              <Empty title="A little breathing room">
                You don’t have any upcoming appointments yet.
              </Empty>
            ) : (
              appointments.data?.content.map((a) => (
                <div className="dashboard-appointment" key={a.id}>
                  <span className="action-icon">
                    <CalendarDays />
                  </span>
                  <div>
                    <strong>
                      {user?.role === "DOCTOR" ? a.patientName : a.doctorName}
                    </strong>
                    <p>{dateTime(a.startTime)}</p>
                  </div>
                  <span className={`status ${a.status.toLowerCase()}`}>
                    {a.status.toLowerCase()}
                  </span>
                </div>
              ))
            )}
          </section>
        </>
      )}
      <section className="panel notification-panel">
        <div className="section-heading">
          <h2>
            <Bell size={19} />
            Your updates
          </h2>
          <span className="muted small-text">
            Updates arrive asynchronously
          </span>
        </div>
        <ErrorBox error={notifications.error} />
        {notifications.isPending ? (
          <p>Loading updates…</p>
        ) : notifications.data?.content.length === 0 ? (
          <Empty title="You’re all caught up">
            Appointment updates will appear here.
          </Empty>
        ) : (
          notifications.data?.content.map((n) => (
            <div className="notification-row" key={n.id}>
              <span className="notification-dot" />
              <div>
                <strong>
                  {n.eventType.toLowerCase().replaceAll("_", " ")}
                </strong>
                <p>{n.appointmentId ? `Appointment #${n.appointmentId.slice(0, 8)}` : `Care update #${n.entityId.slice(0, 8)}`}</p>
              </div>
              <time>{dateTime(n.createdAt)}</time>
            </div>
          ))
        )}
      </section>
    </section>
  );
}
