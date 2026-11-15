package com.carebridge.appointment;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class AppointmentDtos {
  private AppointmentDtos() {}

  public record Book(
      @NotNull UUID doctorId,
      @NotNull Instant startTime,
      @NotNull Instant endTime,
      @Size(max = 500) String reason) {}

  public record Reschedule(@NotNull Instant startTime, @NotNull Instant endTime) {}

  public record View(
      UUID id,
      UUID patientId,
      UUID doctorId,
      String patientName,
      String doctorName,
      Instant startTime,
      Instant endTime,
      AppointmentStatus status,
      String reason,
      long version,
      Instant createdAt) {}
}
