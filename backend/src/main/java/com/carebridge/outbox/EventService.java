package com.carebridge.outbox;

import com.carebridge.appointment.Appointment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class EventService {
  public record Event(
      int schemaVersion,
      UUID eventId,
      UUID appointmentId,
      UUID patientId,
      UUID doctorId,
      String eventType,
      Instant timestamp,
      String correlationId) {
    public Event(
        int schemaVersion,
        UUID eventId,
        UUID appointmentId,
        UUID patientId,
        UUID doctorId,
        String eventType,
        Instant timestamp) {
      this(
          schemaVersion,
          eventId,
          appointmentId,
          patientId,
          doctorId,
          eventType,
          timestamp,
          org.slf4j.MDC.get("requestId") == null
              ? String.valueOf(eventId)
              : org.slf4j.MDC.get("requestId"));
    }
  }

  public static String correlation(String value, UUID eventId) {
    return value != null && value.matches("[a-zA-Z0-9-]{1,80}") ? value : String.valueOf(eventId);
  }

  private final OutboxRepository outbox;
  private final ObjectMapper mapper;

  public EventService(OutboxRepository outbox, ObjectMapper mapper) {
    this.outbox = outbox;
    this.mapper = mapper;
  }

  public record DomainEvent(
      int schemaVersion,
      UUID eventId,
      UUID aggregateId,
      String aggregateType,
      String eventType,
      UUID patientId,
      UUID providerId,
      String correlationId,
      Instant timestamp) {}

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID domain(
      UUID aggregateId, String aggregateType, String eventType, UUID patientId, UUID providerId) {
    UUID id = UUID.randomUUID();
    String correlation = org.slf4j.MDC.get("requestId");
    var event =
        new DomainEvent(
            1,
            id,
            aggregateId,
            aggregateType,
            eventType,
            patientId,
            providerId,
            correlation == null ? id.toString() : correlation,
            Instant.now());
    OutboxEvent row = new OutboxEvent();
    row.id = id;
    row.aggregateId = aggregateId;
    row.aggregateType = aggregateType;
    row.eventType = eventType;
    row.topic = "care-events";
    try {
      row.payload = mapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Event serialization failed", e);
    }
    outbox.save(row);
    return id;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void appointment(Appointment appointment, String type) {
    enqueue(
        new Event(
            1,
            UUID.randomUUID(),
            appointment.id,
            appointment.patientId,
            appointment.doctorId,
            type,
            Instant.now()),
        "appointment-events");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(Event event, String topic) {
    OutboxEvent row = new OutboxEvent();
    row.id = event.eventId();
    row.aggregateType = "APPOINTMENT";
    row.aggregateId = event.appointmentId();
    row.eventType = event.eventType();
    row.topic = topic;
    try {
      row.payload = mapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Event serialization failed", e);
    }
    outbox.save(row);
  }
}
