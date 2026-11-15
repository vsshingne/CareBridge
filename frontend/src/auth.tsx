import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { acceptSession, api, refreshSession, setSessionListener } from "./api";
import type { Role, Session, User } from "./types";
const AuthContext = createContext<{
  user: User | null;
  loading: boolean;
  signIn: (s: Session) => void;
  signOut: () => Promise<void>;
}>({ user: null, loading: true, signIn: () => {}, signOut: async () => {} });
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const cache = useQueryClient();
  useEffect(() => {
    setSessionListener((s) => {
      setUser(s?.user ?? null);
      if (!s) cache.clear();
    });
    refreshSession()
      .catch(() => {})
      .finally(() => setLoading(false));
    return () => setSessionListener(() => {});
  }, [cache]);
  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        signIn: (s) => {
          cache.clear();
          acceptSession(s);
        },
        signOut: async () => {
          await api.post("/auth/logout");
          acceptSession(null);
          cache.clear();
        },
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
export const useAuth = () => useContext(AuthContext);
export function Protected({
  children,
  roles,
}: {
  children: ReactNode;
  roles?: Role[];
}) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="loading">Restoring your session…</div>;
  if (!user)
    return (
      <Navigate
        to="/login"
        state={{ from: location.pathname + location.search }}
        replace
      />
    );
  if (roles && !roles.includes(user.role))
    return (
      <div className="empty">
        <h2>Access restricted</h2>
        <p>
          This page is available to {roles.join(" or ").toLowerCase()} accounts.
        </p>
      </div>
    );
  return children;
}
