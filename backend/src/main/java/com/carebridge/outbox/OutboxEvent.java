package com.carebridge.outbox;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
  @Id public UUID id = UUID.randomUUID();
  public String aggregateType;
  public UUID aggregateId;
  public String eventType;
  public String topic;

  @Column(columnDefinition = "text")
  public String payload;

  public Instant createdAt = Instant.now();
  public Instant publishedAt;
  public String status = "PENDING";
  public int retryCount;
  public Instant nextAttemptAt = Instant.now();
}
