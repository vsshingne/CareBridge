package com.carebridge.audit;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
  @Id public UUID id = UUID.randomUUID();
  public UUID actorId;
  public String action;
  public String entityType;
  public UUID entityId;
  public Instant timestamp = Instant.now();
  public String metadata = "{}";
}
