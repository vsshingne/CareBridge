import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { HeartHandshake, ArrowRight } from "lucide-react";
import { api } from "../api";
import { useAuth } from "../auth";
import { ErrorBox, Field } from "../components/UI";
import type { Session } from "../types";

export function AuthPage() {
  const { kind } = useParams();
  const registering = !!kind;
  const doctor = kind === "doctor";
  const navigate = useNavigate();
  const location = useLocation();
  const { signIn } = useAuth();
  const schema = z.object({
    email: z.string().email(),
    password: z
      .string()
      .min(registering ? 12 : 1)
      .max(72),
    fullName: registering ? z.string().min(1).max(120) : z.string().optional(),
    phoneNumber: z.string().max(30).optional(),
    specialization: doctor ? z.string().min(1).max(120) : z.string().optional(),
    qualification: doctor ? z.string().min(1).max(200) : z.string().optional(),
    timezone: z.string().optional(),
  });
  type Form = z.infer<typeof schema>;
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: {
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    },
  });
  const mutation = useMutation({
    mutationFn: async (data: Form) =>
      (
        await api.post<Session>(
          registering
            ? `/auth/register/${doctor ? "doctor" : "patient"}`
            : "/auth/login",
          registering ? data : { email: data.email, password: data.password },
        )
      ).data,
    onSuccess: (session) => {
      signIn(session);
      const from = location.state?.from;
      navigate(
        typeof from === "string" &&
          from.startsWith("/") &&
          !from.startsWith("//")
          ? from
          : "/dashboard",
        { replace: true },
      );
    },
  });
  return (
    <section className="auth-layout">
      <aside className="auth-aside">
        <HeartHandshake size={48} strokeWidth={1.2} />
        <h1>
          A healthier journey
          <br />
          starts here.
        </h1>
        <p>
          One place to find your doctor, make your appointment, and stay
          connected to your care.
        </p>
        <div className="auth-aside-bottom">CARE, CONNECTED.</div>
      </aside>
      <div className="auth-form">
        <p className="eyebrow">
          {registering ? "WELCOME TO CAREBRIDGE" : "GOOD TO SEE YOU AGAIN"}
        </p>
        <h1>
          {registering
            ? `Create your ${doctor ? "doctor" : "patient"} account`
            : "Welcome back"}
        </h1>
        <p className="muted">
          {registering
            ? doctor
              ? "Set up your profile. An administrator will verify it before patients can book."
              : "Make a little more room for your wellbeing."
            : "Sign in to manage your appointments and profile."}
        </p>
        <form onSubmit={handleSubmit((data) => mutation.mutate(data))}>
          <ErrorBox error={mutation.error} />
          {registering && (
            <Field label="Full name" error={errors.fullName?.message}>
              <input autoComplete="name" {...register("fullName")} />
            </Field>
          )}
          <Field label="Email address" error={errors.email?.message}>
            <input type="email" autoComplete="email" {...register("email")} />
          </Field>
          <Field label="Password" error={errors.password?.message}>
            <input
              type="password"
              autoComplete={registering ? "new-password" : "current-password"}
              {...register("password")}
            />
          </Field>
          {registering && (
            <Field
              label="Phone number (optional)"
              error={errors.phoneNumber?.message}
            >
              <input
                type="tel"
                autoComplete="tel"
                {...register("phoneNumber")}
              />
            </Field>
          )}
          {doctor && (
            <>
              <Field
                label="Specialization"
                error={errors.specialization?.message}
              >
                <input
                  placeholder="e.g. Cardiology"
                  {...register("specialization")}
                />
              </Field>
              <Field
                label="Qualification"
                error={errors.qualification?.message}
              >
                <input
                  placeholder="e.g. MD, Internal Medicine"
                  {...register("qualification")}
                />
              </Field>
              <Field label="Clinic timezone" error={errors.timezone?.message}>
                <input {...register("timezone")} />
              </Field>
            </>
          )}
          <button className="btn full" disabled={mutation.isPending}>
            {mutation.isPending
              ? "Please wait…"
              : registering
                ? "Create account"
                : "Sign in"}
            <ArrowRight size={18} />
          </button>
        </form>
        <p className="auth-switch">
          {registering ? (
            <>
              Already have an account? <Link to="/login">Sign in</Link>
            </>
          ) : (
            <>
              New to CareBridge?{" "}
              <Link to="/register/patient">Create an account</Link>
            </>
          )}
        </p>
        {!registering && (
          <Link className="text-link" to="/register/doctor">
            Register as a doctor →
          </Link>
        )}
      </div>
    </section>
  );
}
