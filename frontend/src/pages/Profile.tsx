import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, get } from "../api";
import { useAuth } from "../auth";
import { Empty, ErrorBox, Field, Heading } from "../components/UI";
import type { Doctor, Patient, Window } from "../types";

const patientSchema = z.object({
  fullName: z.string().min(1).max(120),
  phoneNumber: z.string().max(30),
  dateOfBirth: z.string().nullable(),
  gender: z.string().max(40),
  address: z.string().max(500),
  emergencyContact: z.string().max(120),
});
const doctorSchema = z.object({
  fullName: z.string().min(1).max(120),
  specialization: z.string().min(1).max(120),
  qualification: z.string().min(1).max(200),
  experienceYears: z.coerce.number<number>().int().min(0).max(80),
  consultationFee: z.coerce.number<number>().min(0).max(9999999999.99),
  clinicAddress: z.string().min(1).max(500),
  biography: z.string().max(2000),
  timezone: z
    .string()
    .min(1)
    .refine((v) => {
      try {
        new Intl.DateTimeFormat("en", { timeZone: v });
        return true;
      } catch {
        return false;
      }
    }, "Enter a valid IANA timezone"),
});
export function Profile() {
  const { user } = useAuth();
  const doctor = user?.role === "DOCTOR";
  const query = useQuery<Doctor | Patient>({
    queryKey: ["profile", user?.role],
    queryFn: () =>
      get<Doctor | Patient>(doctor ? "/doctors/me" : "/patients/me"),
  });
  return (
    <section className="page narrow">
      <Heading eyebrow="THE DETAILS THAT MATTER" title="Your profile" />
      <p className="intro">
        Keep your contact and profile information up to date.
      </p>
      <ErrorBox error={query.error} />
      {query.isPending ? (
        <div className="loading">Loading profile…</div>
      ) : (
        query.data &&
        (doctor ? (
          <DoctorForm data={query.data as Doctor} />
        ) : (
          <PatientForm data={query.data as Patient} />
        ))
      )}
    </section>
  );
}
function PatientForm({ data }: { data: Patient }) {
  const form = useForm<z.infer<typeof patientSchema>>({
    resolver: zodResolver(patientSchema),
    defaultValues: {
      fullName: data.user.fullName,
      phoneNumber: data.user.phoneNumber ?? "",
      dateOfBirth: data.dateOfBirth ?? "",
      gender: data.gender ?? "",
      address: data.address ?? "",
      emergencyContact: data.emergencyContact ?? "",
    },
  });
  const mutation = useMutation({
    mutationFn: (r: z.infer<typeof patientSchema>) =>
      api.put("/patients/me", { ...r, dateOfBirth: r.dateOfBirth || null }),
  });
  return (
    <form
      className="panel form-grid"
      onSubmit={form.handleSubmit((r) => mutation.mutate(r))}
    >
      <div className="span-2">
        <ErrorBox error={mutation.error} />
        {mutation.isSuccess && <p className="success">Profile saved.</p>}
      </div>
      <Field label="Full name" error={form.formState.errors.fullName?.message}>
        <input autoComplete="name" {...form.register("fullName")} />
      </Field>
      <Field label="Phone" error={form.formState.errors.phoneNumber?.message}>
        <input type="tel" {...form.register("phoneNumber")} />
      </Field>
      <Field
        label="Date of birth (optional)"
        error={form.formState.errors.dateOfBirth?.message}
      >
        <input
          type="date"
          max={new Date(Date.now() - 86400000).toISOString().slice(0, 10)}
          {...form.register("dateOfBirth")}
        />
      </Field>
      <Field
        label="Gender (optional)"
        error={form.formState.errors.gender?.message}
      >
        <select {...form.register("gender")}>
          <option value="">Prefer not to say</option>
          <option>Female</option>
          <option>Male</option>
          <option>Non-binary</option>
          <option>Self-described</option>
        </select>
      </Field>
      <Field
        label="Address (optional)"
        error={form.formState.errors.address?.message}
      >
        <textarea {...form.register("address")} />
      </Field>
      <Field
        label="Emergency contact (optional)"
        error={form.formState.errors.emergencyContact?.message}
      >
        <input {...form.register("emergencyContact")} />
      </Field>
      <div className="span-2">
        <button className="btn" disabled={mutation.isPending}>
          {mutation.isPending ? "Saving…" : "Save profile"}
        </button>
      </div>
    </form>
  );
}
function DoctorForm({ data }: { data: Doctor }) {
  const { id: _id, verified: _verified, ...defaults } = data;
  void _id;
  void _verified;
  const form = useForm<z.infer<typeof doctorSchema>>({
    resolver: zodResolver(doctorSchema),
    defaultValues: { ...defaults, biography: data.biography ?? "" },
  });
  const cache = useQueryClient();
  const mutation = useMutation({
    mutationFn: (r: z.infer<typeof doctorSchema>) => api.put("/doctors/me", r),
    onSuccess: () => {
      cache.invalidateQueries({ queryKey: ["doctors"] });
      cache.invalidateQueries({ queryKey: ["profile"] });
    },
  });
  return (
    <>
      <p className={data.verified ? "success" : "notice"}>
        {data.verified
          ? "Your profile is verified."
          : "Your profile is awaiting administrator verification."}{" "}
        Changes to specialization or qualification require verification again.
      </p>
      <form
        className="panel form-grid"
        onSubmit={form.handleSubmit((r) => mutation.mutate(r))}
      >
        <div className="span-2">
          <ErrorBox error={mutation.error} />
          {mutation.isSuccess && <p className="success">Profile saved.</p>}
        </div>
        {(
          [
            ["fullName", "Full name"],
            ["specialization", "Specialization"],
            ["qualification", "Qualification"],
            ["experienceYears", "Years of experience"],
            ["consultationFee", "Consultation fee (INR)"],
            ["timezone", "Clinic timezone"],
          ] as const
        ).map(([name, label]) => (
          <Field
            key={name}
            label={label}
            error={form.formState.errors[name]?.message}
          >
            <input
              type={
                name === "experienceYears" || name === "consultationFee"
                  ? "number"
                  : "text"
              }
              step={name === "consultationFee" ? "0.01" : undefined}
              {...form.register(name)}
            />
          </Field>
        ))}
        <div className="span-2">
          <Field
            label="Clinic address"
            error={form.formState.errors.clinicAddress?.message}
          >
            <textarea {...form.register("clinicAddress")} />
          </Field>
          <Field
            label="Biography"
            error={form.formState.errors.biography?.message}
          >
            <textarea rows={5} {...form.register("biography")} />
          </Field>
          <button className="btn" disabled={mutation.isPending}>
            {mutation.isPending ? "Saving…" : "Save profile"}
          </button>
        </div>
      </form>
    </>
  );
}
const windowSchema = z
  .object({
    dayOfWeek: z.coerce.number<number>().int().min(1).max(7),
    startTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/),
    endTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/),
    slotDurationMinutes: z.coerce.number<number>().int().min(5).max(240),
    active: z.boolean(),
  })
  .refine((r) => r.endTime > r.startTime, {
    message: "End time must be after start time",
    path: ["endTime"],
  });
const days = [
  "",
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday",
];
export function Schedule() {
  const cache = useQueryClient();
  const [editing, setEditing] = useState<string | null>(null);
  const [success, setSuccess] = useState("");
  const query = useQuery({
    queryKey: ["my-availability"],
    queryFn: () => get<Window[]>("/doctors/me/availability"),
  });
  const profile = useQuery({
    queryKey: ["profile", "DOCTOR"],
    queryFn: () => get<Doctor>("/doctors/me"),
  });
  const form = useForm<z.infer<typeof windowSchema>>({
    resolver: zodResolver(windowSchema),
    defaultValues: {
      dayOfWeek: 1,
      startTime: "09:00",
      endTime: "17:00",
      slotDurationMinutes: 30,
      active: true,
    },
  });
  const refresh = () => {
    cache.invalidateQueries({ queryKey: ["my-availability"] });
    cache.invalidateQueries({ queryKey: ["availability"] });
    cache.invalidateQueries({ queryKey: ["slots"] });
  };
  const save = useMutation({
    mutationFn: (r: z.infer<typeof windowSchema>) =>
      editing
        ? api.put(`/doctors/me/availability/${editing}`, r)
        : api.post("/doctors/me/availability", r),
    onSuccess: () => {
      refresh();
      setEditing(null);
      setSuccess("Availability saved.");
      form.reset();
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.delete(`/doctors/me/availability/${id}`),
    onSuccess: () => {
      refresh();
      setSuccess("Availability removed.");
    },
  });
  return (
    <section className="page">
      <Heading
        eyebrow="MAKE ROOM FOR YOUR PATIENTS"
        title="Weekly availability"
      />
      <p className="intro">
        Set recurring clinic hours in{" "}
        {profile.data?.timezone ?? "your clinic timezone"}. Existing
        appointments are protected when you edit your schedule.
      </p>
      {success && (
        <p role="status" className="success">
          {success}
        </p>
      )}
      <div className="schedule-layout">
        <section className="panel">
          <h2>Your weekly hours</h2>
          <ErrorBox error={query.error || remove.error} />
          {query.isPending ? (
            <p>Loading schedule…</p>
          ) : query.data?.length === 0 ? (
            <Empty title="Your week is a blank canvas">
              Add a window to start accepting appointment requests.
            </Empty>
          ) : (
            query.data?.map((w) => (
              <div className="schedule-row" key={w.id}>
                <div>
                  <strong>{days[w.dayOfWeek]}</strong>
                  <p>
                    {w.startTime.slice(0, 5)} – {w.endTime.slice(0, 5)} ·{" "}
                    {w.slotDurationMinutes} min
                  </p>
                  <span
                    className={`status ${w.active ? "confirmed" : "cancelled"}`}
                  >
                    {w.active ? "Active" : "Inactive"}
                  </span>
                </div>
                <div className="row-actions">
                  <button
                    className="text-button"
                    onClick={() => {
                      setEditing(w.id);
                      form.reset({
                        dayOfWeek: w.dayOfWeek,
                        startTime: w.startTime.slice(0, 5),
                        endTime: w.endTime.slice(0, 5),
                        slotDurationMinutes: w.slotDurationMinutes,
                        active: w.active,
                      });
                    }}
                  >
                    Edit
                  </button>
                  <button
                    className="text-button danger"
                    disabled={remove.isPending}
                    onClick={() => {
                      if (window.confirm("Remove this availability window?"))
                        remove.mutate(w.id);
                    }}
                  >
                    Remove
                  </button>
                </div>
              </div>
            ))
          )}
        </section>
        <form
          className="panel"
          onSubmit={form.handleSubmit((r) => save.mutate(r))}
        >
          <h2>{editing ? "Edit window" : "Add availability"}</h2>
          <ErrorBox error={save.error} />
          <Field
            label="Day of week"
            error={form.formState.errors.dayOfWeek?.message}
          >
            <select {...form.register("dayOfWeek")}>
              {days.slice(1).map((day, i) => (
                <option value={i + 1} key={day}>
                  {day}
                </option>
              ))}
            </select>
          </Field>
          <div className="form-grid">
            <Field
              label="Start time"
              error={form.formState.errors.startTime?.message}
            >
              <input type="time" {...form.register("startTime")} />
            </Field>
            <Field
              label="End time"
              error={form.formState.errors.endTime?.message}
            >
              <input type="time" {...form.register("endTime")} />
            </Field>
          </div>
          <Field
            label="Appointment duration (minutes)"
            error={form.formState.errors.slotDurationMinutes?.message}
          >
            <input type="number" {...form.register("slotDurationMinutes")} />
          </Field>
          <label className="checkbox">
            <input type="checkbox" {...form.register("active")} />
            Accept bookings in this window
          </label>
          <div className="row-actions">
            <button className="btn" disabled={save.isPending}>
              {save.isPending ? "Saving…" : "Save availability"}
            </button>
            {editing && (
              <button
                type="button"
                className="text-button"
                onClick={() => {
                  setEditing(null);
                  form.reset();
                }}
              >
                Cancel edit
              </button>
            )}
          </div>
        </form>
      </div>
    </section>
  );
}
