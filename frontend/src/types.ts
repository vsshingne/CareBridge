export type Role = "PATIENT" | "DOCTOR" | "ADMIN";
export interface User {
  id: string;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  role: Role;
  enabled: boolean;
}
export interface Session {
  accessToken: string;
  expiresIn: number;
  user: User;
}
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
export interface Doctor {
  id: string;
  fullName: string;
  specialization: string;
  qualification: string;
  experienceYears: number;
  consultationFee: number;
  clinicAddress: string;
  biography: string | null;
  timezone: string;
  verified: boolean;
}
export interface Window {
  id: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  slotDurationMinutes: number;
  active: boolean;
}
export interface Slot {
  startTime: string;
  endTime: string;
}
export type Status =
  | "PENDING"
  | "CONFIRMED"
  | "CANCELLED"
  | "COMPLETED"
  | "REJECTED";
export interface Appointment {
  id: string;
  patientId: string;
  doctorId: string;
  patientName: string;
  doctorName: string;
  startTime: string;
  endTime: string;
  status: Status;
  reason: string | null;
  version: number;
  createdAt: string;
}
export interface Patient {
  id: string;
  user: User;
  dateOfBirth: string | null;
  gender: string | null;
  address: string | null;
  emergencyContact: string | null;
}
export interface Notification {
  id: string;
  appointmentId: string | null;
  entityId: string;
  eventType: string;
  createdAt: string;
}
export interface Audit {
  id: string;
  actorId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  timestamp: string;
}
export interface Outbox {
  id: string;
  eventType: string;
  status: string;
  retryCount: number;
  createdAt: string;
}
export interface Analytics {
  patients: number;
  doctors: number;
  unverifiedDoctors: number;
  appointments: number;
  failedOutboxEvents: number;
  appointmentsByStatus: { status: Status; count: number }[];
  appointmentsByDay: { date: string; count: number }[];
}
