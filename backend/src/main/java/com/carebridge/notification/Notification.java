package com.carebridge.notification;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id public UUID id = UUID.randomUUID();
  public UUID userId;
  public UUID eventId;
  public UUID appointmentId;
  public String eventType;
  public Instant createdAt = Instant.now();
}
