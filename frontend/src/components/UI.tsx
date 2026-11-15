import { cloneElement, isValidElement, useId, type ReactNode } from "react";
import { message } from "../api";
import type { Page } from "../types";
export function ErrorBox({ error }: { error: unknown }) {
  return error ? (
    <div role="alert" className="error">
      {message(error)}
    </div>
  ) : null;
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty-icon">✧</span>
      <h3>{title}</h3>
      {children && <p>{children}</p>}
    </div>
  );
}
export function Pager({
  data,
  onPage,
}: {
  data?: Pick<Page<unknown>, "number" | "totalPages" | "totalElements">;
  onPage: (page: number) => void;
}) {
  if (!data || data.totalPages < 2) return null;
  return (
    <div className="pager">
      <button
        className="btn secondary"
        disabled={data.number === 0}
        onClick={() => onPage(data.number - 1)}
      >
        Previous
      </button>
      <span>
        Page {data.number + 1} of {data.totalPages}
      </span>
      <button
        className="btn secondary"
        disabled={data.number + 1 >= data.totalPages}
        onClick={() => onPage(data.number + 1)}
      >
        Next
      </button>
    </div>
  );
}
export function Heading({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
      </div>
      {children}
    </div>
  );
}
export function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: ReactNode;
}) {
  const id = useId();
  const control = isValidElement<{
    id?: string;
    "aria-describedby"?: string;
    "aria-invalid"?: boolean;
  }>(children)
    ? cloneElement(children, {
        id,
        "aria-describedby": error ? `${id}-error` : undefined,
        "aria-invalid": !!error,
      })
    : children;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {control}
      {error && (
        <small id={`${id}-error`} className="field-error" role="alert">
          {error}
        </small>
      )}
    </div>
  );
}
export const dateTime = (value: string) =>
  new Date(value).toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  });
export const money = (value: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(value);
export const initials = (name: string) =>
  name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((s) => s[0])
    .join("");
