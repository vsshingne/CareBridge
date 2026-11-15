import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { get } from "../api";
import { ErrorBox } from "./UI";
export function PatientDetails({ appointmentId }: { appointmentId: string }) {
  const [open, setOpen] = useState(false);
  const query = useQuery({
    queryKey: ["patient-details", appointmentId],
    queryFn: () =>
      get<{
        fullName: string;
        phoneNumber: string | null;
        dateOfBirth: string | null;
        gender: string | null;
      }>(`/appointments/${appointmentId}/patient`),
    enabled: open,
  });
  return (
    <div>
      <button
        className="text-button"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
      >
        {open ? "Hide patient details" : "View patient details"}
      </button>
      {open && (
        <>
          <ErrorBox error={query.error} />
          {query.isPending ? (
            <p>Loading patient details…</p>
          ) : (
            query.data && (
              <p>
                {query.data.phoneNumber || "No phone provided"} · Born:{" "}
                {query.data.dateOfBirth || "Not provided"} ·{" "}
                {query.data.gender || "Gender not provided"}
              </p>
            )
          )}
        </>
      )}
    </div>
  );
}
