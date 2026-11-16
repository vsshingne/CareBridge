import { useState, type ReactNode } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../api";
import { ErrorBox } from "./UI";

export function WorkflowForm({
  path,
  method = "post",
  body,
  children,
  label,
  onSaved,
}: {
  path: string;
  method?: "post" | "put" | "patch" | "delete";
  body?: (data: FormData) => unknown;
  children?: ReactNode;
  label: string;
  onSaved?: (data: unknown) => void;
}) {
  const cache = useQueryClient();
  const [saved, setSaved] = useState(false);
  const mutation = useMutation({
    mutationFn: async (data: FormData) =>
      (await api.request({ url: path, method, data: body?.(data) })).data,
    onSuccess: (data) => {
      setSaved(true);
      for (const key of [
        "care",
        "referrals",
        "journey",
        "gaps",
        "rules",
        "recovery",
        "appointments",
        "notifications",
      ])
        cache.invalidateQueries({ queryKey: [key] });
      onSaved?.(data);
    },
  });
  return (
    <form
      className="workflow-form"
      onSubmit={(e) => {
        e.preventDefault();
        setSaved(false);
        mutation.mutate(new FormData(e.currentTarget));
      }}
    >
      {children}
      <ErrorBox error={mutation.error} />
      {saved && (
        <p className="success" role="status">
          Changes saved.
        </p>
      )}
      <button className="btn secondary small" disabled={mutation.isPending}>
        {mutation.isPending ? "Saving…" : label}
      </button>
    </form>
  );
}
export const formText = (data: FormData, key: string) =>
  String(data.get(key) ?? "");
export const formInstant = (data: FormData, key: string) =>
  new Date(formText(data, key)).toISOString();
export const localInput = (instant: string) => {
  const date = new Date(instant);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
};
