package com.carebridge.care;

import static com.carebridge.care.CareStates.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class CareDtos {
  private CareDtos() {}
  public record CreatePlan(@NotNull UUID appointmentId, @NotBlank @Size(max=200) String title,
      @NotBlank @Size(max=4000) String goals) {}
  public record EditPlan(@Min(0) long version, @NotBlank @Size(max=200) String title,
      @NotBlank @Size(max=4000) String goals) {}
  public record FinishPlan(@Min(0) long version, @NotNull PlanStatus status) {}
  public record Plan(UUID id, UUID patientId, UUID providerId, String patientName, String providerName,
      String title, String goals, PlanStatus status, long version, Instant createdAt,
      long totalActivities, long completedActivities, long totalReferrals, long completedReferrals) {}
  public record AddActivity(@NotNull ActivityType type, @NotBlank @Size(max=200) String title,
      @NotNull @Future Instant dueAt) {}
  public record EditActivity(@Min(0) long version, @NotBlank @Size(max=200) String title,
      @NotNull @Future Instant dueAt) {}
  public record ActivityChange(@Min(0) long version, @NotNull ActivityStatus status,
      @Size(max=4000) String outcome) {}
  public record Activity(UUID id, UUID planId, ActivityType type, String title, Instant dueAt,
      ActivityStatus status, String outcome, long version, boolean overdue) {}
  public record CreateReferral(@NotNull UUID receivingProviderId,
      @NotBlank @Size(max=2000) String clinicalReason, @NotNull Priority priority,
      @NotNull @Future Instant dueAt) {}
  public record ReferralChange(@Min(0) long version, @NotNull ReferralStatus status,
      UUID appointmentId, @Size(max=4000) String outcome) {}
  public record Reassign(@Min(0) long version, @NotNull UUID receivingProviderId) {}
  public record Referral(UUID id, UUID planId, UUID patientId, UUID referringProviderId,
      UUID receivingProviderId, String patientName, String referringProviderName,
      String receivingProviderName, String clinicalReason, Priority priority, Instant dueAt,
      UUID appointmentId, ReferralStatus status, String outcome, long version, Instant createdAt) {}
  public record Journey(UUID id, UUID entityId, String activityType, String action,
      String status, Instant occurredAt, Long version) {}
}
