export interface CarePlan {
  id: string; patientId: string; providerId: string; patientName: string; providerName: string;
  title: string; goals: string; status: string; version: number; createdAt: string;
  totalActivities: number; completedActivities: number; totalReferrals: number; completedReferrals: number;
}
export interface CareActivity {
  id: string; planId: string; type: string; title: string; dueAt: string; status: string;
  outcome: string | null; version: number; overdue: boolean;
}
export interface Referral {
  id: string; planId: string; patientId: string; referringProviderId: string; receivingProviderId: string;
  patientName: string; referringProviderName: string; receivingProviderName: string; clinicalReason: string;
  priority: string; dueAt: string; appointmentId: string | null; status: string; outcome: string | null; version: number;
}
export interface JourneyEvent {id: string; entityId: string; activityType: string; action: string; status: string; occurredAt: string;}
export interface Gap {id: string; planId: string; entityId: string; patientName: string; ruleName: string; activityType: string; severity: string; createdAt: string; resolvedAt: string | null;}
export interface GapRule {id: string; name: string; activityType: string; triggerCondition: string; intervalHours: number; severity: string; notificationPolicy: string; responsibleRole: string; enabled: boolean; version: number;}
export interface WaitingEntry {id: string; doctorId: string | null; specialty: string | null; availableFrom: string; availableTo: string; priority: number; notifyInApp: boolean; active: boolean;}
export interface Offer {id: string; doctorId: string; providerName: string; startTime: string; endTime: string; expiresAt: string; status: string; acceptedAppointmentId: string | null;}
