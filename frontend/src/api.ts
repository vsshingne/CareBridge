import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import type { Session } from "./types";

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api/v1",
  withCredentials: true,
  timeout: 15000,
});
let token: string | null = null;
let refreshPromise: Promise<Session> | null = null;
let sessionListener: (session: Session | null) => void = () => {};
export function setSessionListener(listener: typeof sessionListener) {
  sessionListener = listener;
}
export function acceptSession(session: Session | null) {
  token = session?.accessToken ?? null;
  sessionListener(session);
}
export function refreshSession(): Promise<Session> {
  if (!refreshPromise)
    refreshPromise = api
      .post<Session>("/auth/refresh")
      .then((r) => {
        acceptSession(r.data);
        return r.data;
      })
      .catch((e) => {
        acceptSession(null);
        throw e;
      })
      .finally(() => {
        refreshPromise = null;
      });
  return refreshPromise;
}
api.interceptors.request.use((config) => {
  if (token && !config.url?.startsWith("/auth/"))
    config.headers.Authorization = `Bearer ${token}`;
  return config;
});
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as
      | (InternalAxiosRequestConfig & { retried?: boolean })
      | undefined;
    if (
      error.response?.status === 401 &&
      config &&
      !config.retried &&
      !config.url?.startsWith("/auth/")
    ) {
      config.retried = true;
      await refreshSession();
      return api(config);
    }
    return Promise.reject(error);
  },
);
export async function get<T>(url: string, params?: object): Promise<T> {
  return (await api.get<T>(url, { params })).data;
}
export function message(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const fallback: Record<number, string> = {
      401: "Your session expired. Please sign in.",
      403: "You do not have access to this action.",
      404: "This record could not be found.",
      409: "This slot or record has changed. Refresh and try again.",
    };
    return (
      error.response?.data?.message ||
      fallback[error.response?.status ?? 0] ||
      "Unable to connect. Please try again."
    );
  }
  return error instanceof Error
    ? error.message
    : "Something went wrong. Please try again.";
}
