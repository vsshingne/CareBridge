import { Link, NavLink, Route, Routes, useNavigate } from "react-router-dom";
import {
  Activity,
  ArrowUpRight,
  CalendarDays,
  HeartHandshake,
  LayoutDashboard,
  LogOut,
  Search,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { lazy, Suspense, useState } from "react";
import { Protected, useAuth } from "./auth";
import { ErrorBox } from "./components/UI";
import { AuthPage } from "./pages/AuthPage";
import { Directory, DoctorPage } from "./pages/Doctors";
import { Appointments } from "./pages/Appointments";
import { Profile, Schedule } from "./pages/Profile";
import { Admin } from "./pages/Admin";
import { Dashboard } from "./pages/Dashboard";
const CarePlans=lazy(()=>import("./pages/Care").then(m=>({default:m.CarePlans})));
const NewCarePlan=lazy(()=>import("./pages/Care").then(m=>({default:m.NewCarePlan})));
const CarePlanDetail=lazy(()=>import("./pages/Care").then(m=>({default:m.CarePlanDetail})));
const Referrals=lazy(()=>import("./pages/Care").then(m=>({default:m.Referrals})));
const CareJourney=lazy(()=>import("./pages/Care").then(m=>({default:m.CareJourney})));
const CareGaps=lazy(()=>import("./pages/CareGaps").then(m=>({default:m.CareGaps})));
const Recovery=lazy(()=>import("./pages/Recovery").then(m=>({default:m.Recovery})));

export default function App() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<unknown>(null);
  return (
    <div className="app-shell">
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="brand-mark">
            <HeartHandshake size={23} />
          </span>
          carebridge<span className="brand-dot">.</span>
        </Link>
        <nav aria-label="Main navigation">
          <NavLink to="/doctors">
            <Search size={16} />
            Find a doctor
          </NavLink>
          {user && (
            <>
              <NavLink to="/dashboard">
                <LayoutDashboard size={16} />
                Overview
              </NavLink>
              <NavLink to="/care">Care plans</NavLink>
              <NavLink to="/gaps">Care gaps</NavLink>
              {user.role === "PATIENT" && <NavLink to="/recovery">Waiting list</NavLink>}
              {user.role !== "ADMIN" && (
                <NavLink to="/appointments">
                  <CalendarDays size={16} />
                  Appointments
                </NavLink>
              )}
              {user.role === "DOCTOR" && (
                <NavLink to="/schedule">Availability</NavLink>
              )}
              {user.role === "ADMIN" && (
                <NavLink to="/admin">
                  <ShieldCheck size={16} />
                  Administration
                </NavLink>
              )}
              {user.role !== "ADMIN" && (
                <NavLink to="/profile">
                  <UserRound size={16} />
                  Profile
                </NavLink>
              )}
            </>
          )}
        </nav>
        <div className="header-account">
          {user ? (
            <>
              <span className="account-name">
                {user.fullName.split(" ")[0]}
                <small>{user.role.toLowerCase()}</small>
              </span>
              <button
                className="icon-btn"
                aria-label="Sign out"
                onClick={async () => {
                  try {
                    await signOut();
                    navigate("/");
                  } catch (e) {
                    setError(e);
                  }
                }}
              >
                <LogOut size={18} />
              </button>
            </>
          ) : (
            <>
              <Link className="login-link" to="/login">
                Sign in
              </Link>
              <Link className="btn small" to="/register/patient">
                Get started <ArrowUpRight size={15} />
              </Link>
            </>
          )}
        </div>
      </header>
      <ErrorBox error={error} />
      <main>
        <Suspense fallback={<p className="loading">Loading workspace…</p>}><Routes>
          <Route path="/care" element={<Protected><CarePlans/></Protected>}/>
          <Route path="/care/new" element={<Protected roles={["DOCTOR"]}><NewCarePlan/></Protected>}/>
          <Route path="/care/:id" element={<Protected><CarePlanDetail/></Protected>}/>
          <Route path="/referrals" element={<Protected><Referrals/></Protected>}/>
          <Route path="/journey" element={<Protected roles={["PATIENT"]}><CareJourney/></Protected>}/>
          <Route path="/journey/:patientId" element={<Protected><CareJourney/></Protected>}/>
          <Route path="/gaps" element={<Protected><CareGaps/></Protected>}/>
          <Route path="/recovery" element={<Protected roles={["PATIENT"]}><Recovery/></Protected>}/>
          <Route path="/" element={<Landing />} />
          <Route path="/login" element={<AuthPage />} />
          <Route path="/register/:kind" element={<AuthPage />} />
          <Route path="/doctors" element={<Directory />} />
          <Route path="/doctors/:id" element={<DoctorPage />} />
          <Route
            path="/dashboard"
            element={
              <Protected>
                <Dashboard />
              </Protected>
            }
          />
          <Route
            path="/appointments"
            element={
              <Protected roles={["PATIENT", "DOCTOR"]}>
                <Appointments />
              </Protected>
            }
          />
          <Route
            path="/profile"
            element={
              <Protected roles={["PATIENT", "DOCTOR"]}>
                <Profile />
              </Protected>
            }
          />
          <Route
            path="/schedule"
            element={
              <Protected roles={["DOCTOR"]}>
                <Schedule />
              </Protected>
            }
          />
          <Route
            path="/admin"
            element={
              <Protected roles={["ADMIN"]}>
                <Admin />
              </Protected>
            }
          />
          <Route
            path="*"
            element={
              <section className="empty">
                <h1>Page not found</h1>
                <Link className="btn" to="/">
                  Back to home
                </Link>
              </section>
            }
          />
        </Routes></Suspense>
      </main>
      <footer>
        <Link to="/" className="brand">
          carebridge.
        </Link>
        <span>Care, connected. One appointment at a time.</span>
        <span>For scheduled care. Not an emergency service.</span>
      </footer>
    </div>
  );
}
function Landing() {
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <span className="pill">
            <span className="live-dot" /> A little less admin. A little more
            care.
          </span>
          <h1>
            Your next step
            <br />
            to feeling <em>better.</em>
          </h1>
          <p>
            Find the right doctor, choose a time that works, and keep your care
            in one place. We’ll help you make the connection.
          </p>
          <div className="hero-actions">
            <Link to="/doctors" className="btn">
              Find your doctor <ArrowUpRight size={19} />
            </Link>
            <Link to="/register/doctor" className="text-link">
              Are you a doctor? Join us →
            </Link>
          </div>
          <div className="hero-notes">
            <span>
              <ShieldCheck size={17} />
              Verified doctor profiles
            </span>
            <span>
              <CalendarDays size={17} />
              Live appointment slots
            </span>
          </div>
        </div>
        <div className="care-visual" aria-hidden="true">
          <div className="orbit orbit-one" />
          <div className="orbit orbit-two" />
          <div className="care-symbol">
            <HeartHandshake strokeWidth={1.1} />
          </div>
          <div className="visual-card card-top">
            <span className="visual-icon">
              <CalendarDays />
            </span>
            <div>
              Your time matters<small>Appointments that fit your day</small>
            </div>
          </div>
          <div className="visual-card card-bottom">
            <span className="visual-icon coral">
              <Activity />
            </span>
            <div>
              A clearer care journey<small>From booking to follow-up</small>
            </div>
          </div>
          <span className="visual-label">MAKING ROOM FOR BETTER CARE</span>
        </div>
      </section>
      <section className="steps-section">
        <div>
          <p className="eyebrow">A SIMPLE WAY FORWARD</p>
          <h2>
            Good care starts
            <br />
            with a connection.
          </h2>
        </div>
        <article>
          <span className="step-number">01</span>
          <h3>Find your fit</h3>
          <p>
            Explore verified doctors by specialty, experience, and consultation
            fee.
          </p>
        </article>
        <article>
          <span className="step-number">02</span>
          <h3>Make time for you</h3>
          <p>
            Choose an available slot and send an appointment request to your
            doctor.
          </p>
        </article>
        <article>
          <span className="step-number">03</span>
          <h3>Stay in the loop</h3>
          <p>
            Track confirmations, manage changes, and find your appointment
            history.
          </p>
        </article>
      </section>
      <section className="doctor-banner">
        <div>
          <p className="eyebrow">FOR HEALTHCARE PROFESSIONALS</p>
          <h2>
            More time with patients.
            <br />
            Less time arranging it.
          </h2>
          <p>
            Set your weekly availability and manage appointment requests in one
            workspace.
          </p>
        </div>
        <Link className="btn light" to="/register/doctor">
          Create a doctor account <ArrowUpRight size={18} />
        </Link>
      </section>
    </>
  );
}
