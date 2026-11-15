package com.carebridge.appointment;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class Appointment {
  @Id public UUID id = UUID.randomUUID();
  public UUID patientId;
  public UUID doctorId;
  public Instant startTime;
  public Instant endTime;

  @Enumerated(EnumType.STRING)
  public AppointmentStatus status;

  public String reason;
  public Instant createdAt = Instant.now();
  public Instant updatedAt = Instant.now();
  @Version public long version;
}
