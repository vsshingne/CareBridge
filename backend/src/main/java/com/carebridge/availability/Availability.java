package com.carebridge.availability;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "availability")
public class Availability {
  @Id public UUID id = UUID.randomUUID();
  public UUID doctorId;
  public int dayOfWeek;
  public LocalTime startTime;
  public LocalTime endTime;
  public int slotDurationMinutes;
  public boolean active = true;
}
