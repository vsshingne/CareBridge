import { useState } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowUpRight,
  MapPin,
  Search,
  ShieldCheck,
  Clock3,
} from "lucide-react";
import { api, get } from "../api";
import { useAuth } from "../auth";
import {
  Empty,
  ErrorBox,
  Field,
  Heading,
  Pager,
  initials,
  money,
} from "../components/UI";
import type { Doctor, Page, Slot, Window } from "../types";

export function Directory() {
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [specialization, setSpecialization] = useState("");
  const [sort, setSort] = useState("experienceYears,desc");
  const [page, setPage] = useState(0);
  const doctors = useQuery({
    queryKey: ["doctors", search, specialization, sort, page],
    queryFn: () =>
      get<Page<Doctor>>("/doctors", {
        q: search,
        specialization,
        sort,
        page,
        size: 9,
      }),
  });
  const specialties = useQuery({
    queryKey: ["specializations"],
    queryFn: () => get<string[]>("/doctors/specializations"),
  });
  return (
    <section className="page">
      <Heading
        eyebrow="FIND YOUR CARE TEAM"
        title="The right care, closer to you."
      >
        <span className="subtle-tag">
          <ShieldCheck size={15} />
          Verified professionals
        </span>
      </Heading>
      <p className="intro">
        A doctor who understands your needs. A time that fits your life.
      </p>
      <form
        className="search-bar"
        onSubmit={(e) => {
          e.preventDefault();
          setSearch(q);
          setPage(0);
        }}
      >
        <div className="search-input">
          <Search size={20} />
          <input
            aria-label="Search doctor by name"
            placeholder="Search by doctor’s name"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>
        <select
          aria-label="Specialization"
          value={specialization}
          onChange={(e) => {
            setSpecialization(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All specialties</option>
          {specialties.data?.map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
        <button className="btn">Search doctors</button>
      </form>
      <div className="results-heading">
        <span>
          {doctors.data
            ? `${doctors.data.totalElements} doctors available`
            : "Explore doctors"}
        </span>
        <label>
          Sort by{" "}
          <select
            value={sort}
            onChange={(e) => {
              setSort(e.target.value);
              setPage(0);
            }}
          >
            <option value="experienceYears,desc">Experience</option>
            <option value="consultationFee,asc">Fee: low to high</option>
            <option value="consultationFee,desc">Fee: high to low</option>
          </select>
        </label>
      </div>
      <ErrorBox error={doctors.error || specialties.error} />
      {doctors.isPending ? (
        <div className="loading">Finding your care team…</div>
      ) : doctors.data?.content.length === 0 ? (
        <Empty title="No doctors found">
          Try another name or specialty. Newly registered doctors appear after
          verification.
        </Empty>
      ) : (
        <div className="doctor-grid">
          {doctors.data?.content.map((d, i) => (
            <article className="doctor-card" key={d.id}>
              <div className="doctor-card-top">
                <div className={`avatar avatar-${i % 3}`}>
                  {initials(d.fullName)}
                </div>
                <span className="verified">
                  <ShieldCheck size={14} />
                  Verified
                </span>
              </div>
              <span className="specialty">{d.specialization}</span>
              <h2>{d.fullName}</h2>
              <p className="qualification">{d.qualification}</p>
              <p className="doctor-meta">
                <Clock3 size={15} />
                {d.experienceYears} years of experience
              </p>
              <p className="doctor-meta">
                <MapPin size={15} />
                {d.clinicAddress || "Clinic details being updated"}
              </p>
              <div className="doctor-card-bottom">
                <div>
                  <strong>{money(d.consultationFee)}</strong>
                  <small>per consultation</small>
                </div>
                <Link to={`/doctors/${d.id}`} className="btn secondary small">
                  View availability <ArrowUpRight size={15} />
                </Link>
              </div>
            </article>
          ))}
        </div>
      )}
      <Pager data={doctors.data} onPage={setPage} />
    </section>
  );
}
const today = () => new Date().toLocaleDateString("en-CA");
export function DoctorPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const reschedule = params.get("reschedule");
  const { user } = useAuth();
  const navigate = useNavigate();
  const cache = useQueryClient();
  const [date, setDate] = useState(today);
  const [selected, setSelected] = useState<Slot | null>(null);
  const [reason, setReason] = useState("");
  const profile = useQuery({
    queryKey: ["doctor", id],
    queryFn: () => get<Doctor>(`/doctors/${id}`),
  });
  const availability = useQuery({
    queryKey: ["availability", id],
    queryFn: () => get<Window[]>(`/doctors/${id}/availability`),
  });
  const slots = useQuery({
    queryKey: ["slots", id, date],
    queryFn: () => get<Slot[]>(`/doctors/${id}/slots`, { date }),
    enabled: !!date,
  });
  const booking = useMutation({
    mutationFn: async () => {
      if (!selected) throw new Error("Choose an available time");
      return reschedule
        ? api.patch(`/appointments/${reschedule}/reschedule`, selected)
        : api.post("/appointments", { doctorId: id, ...selected, reason });
    },
    onSuccess: () => {
      cache.invalidateQueries({ queryKey: ["appointments"] });
      cache.invalidateQueries({ queryKey: ["slots"] });
      navigate("/appointments", {
        state: {
          success: reschedule
            ? "Your appointment was rescheduled and is awaiting confirmation."
            : "Your request is in. Your doctor will confirm your appointment.",
        },
      });
    },
    onError: () => {
      cache.invalidateQueries({ queryKey: ["slots", id] });
      setSelected(null);
    },
  });
  if (profile.isPending)
    return <div className="loading">Loading doctor profile…</div>;
  if (!profile.data)
    return (
      <section className="page">
        <ErrorBox error={profile.error} />
      </section>
    );
  const d = profile.data;
  return (
    <section className="page">
      <Link className="back-link" to="/doctors">
        ← All doctors
      </Link>
      <div className="profile-layout">
        <div>
          <article className="panel doctor-profile">
            <div className="avatar large">{initials(d.fullName)}</div>
            <span className="verified">
              <ShieldCheck size={14} />
              Verified doctor
            </span>
            <h1>{d.fullName}</h1>
            <p className="specialty">{d.specialization}</p>
            <p>
              {d.qualification} · {d.experienceYears} years of experience
            </p>
            <hr />
            <h3>About your doctor</h3>
            <p className="body-copy">
              {d.biography || "This doctor has not added a biography yet."}
            </p>
            <h3>Clinic</h3>
            <p className="doctor-meta">
              <MapPin size={17} />
              {d.clinicAddress || "Contact your doctor for clinic details."}
            </p>
            <h3>Weekly hours</h3>
            <ErrorBox error={availability.error} />
            <p className="muted">Clinic timezone: {d.timezone}</p>
            {availability.data?.length === 0 ? (
              <p>No weekly hours have been published.</p>
            ) : (
              availability.data?.map((w) => (
                <div className="hours-row" key={w.id}>
                  <span>
                    {
                      [
                        "",
                        "Monday",
                        "Tuesday",
                        "Wednesday",
                        "Thursday",
                        "Friday",
                        "Saturday",
                        "Sunday",
                      ][w.dayOfWeek]
                    }
                  </span>
                  <span>
                    {w.startTime.slice(0, 5)} – {w.endTime.slice(0, 5)}
                  </span>
                </div>
              ))
            )}
          </article>
        </div>
        <aside className="panel booking-panel">
          <p className="eyebrow">MAKE TIME FOR YOUR HEALTH</p>
          <h2>
            {reschedule ? "Reschedule appointment" : "Choose your appointment"}
          </h2>
          <div className="booking-fee">
            <strong>{money(d.consultationFee)}</strong>
            <span>per consultation · paid at clinic</span>
          </div>
          <Field label={`Appointment date (${d.timezone})`}>
            <input
              type="date"
              value={date}
              min={today()}
              onChange={(e) => {
                setDate(e.target.value);
                setSelected(null);
              }}
            />
          </Field>
          <p className="muted small-text">
            Times below are shown in{" "}
            {Intl.DateTimeFormat().resolvedOptions().timeZone}.
          </p>
          <ErrorBox error={slots.error} />
          {slots.isFetching ? (
            <p className="loading">Checking availability…</p>
          ) : slots.data?.length === 0 ? (
            <Empty title="No open slots">
              Choose another date to see more options.
            </Empty>
          ) : (
            <div className="slot-grid">
              {slots.data?.map((s) => (
                <button
                  key={s.startTime}
                  className={`slot ${selected?.startTime === s.startTime ? "selected" : ""}`}
                  aria-pressed={selected?.startTime === s.startTime}
                  onClick={() => setSelected(s)}
                >
                  {new Date(s.startTime).toLocaleTimeString([], {
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </button>
              ))}
            </div>
          )}
          {!reschedule && (
            <Field label="Reason for visit (optional)">
              <textarea
                maxLength={500}
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Keep this brief. Avoid sharing sensitive medical details."
              />
            </Field>
          )}
          <ErrorBox error={booking.error} />
          {!user ? (
            <Link
              to="/login"
              state={{ from: `/doctors/${id}` }}
              className="btn full"
            >
              Sign in to book
            </Link>
          ) : user.role === "PATIENT" ? (
            <button
              className="btn full"
              disabled={!selected || booking.isPending}
              onClick={() => booking.mutate()}
            >
              {booking.isPending
                ? "Sending request…"
                : reschedule
                  ? "Confirm new time"
                  : "Request appointment"}
              <ArrowUpRight size={17} />
            </button>
          ) : (
            <p className="notice">
              Booking is available with a patient account.
            </p>
          )}
          <p className="booking-note">
            Your appointment is confirmed once the doctor accepts your request.
            You can manage changes from your dashboard.
          </p>
        </aside>
      </div>
    </section>
  );
}
