package com.carebridge.outbox;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventCompatibilityTest {
  @Test
  void retainedSchemaOneEventsWithoutCorrelationRemainReadable() throws Exception {
    UUID id = UUID.randomUUID();
    String retained =
        "{\"schemaVersion\":1,\"eventId\":\""
            + id
            + "\",\"appointmentId\":\""
            + UUID.randomUUID()
            + "\",\"patientId\":\""
            + UUID.randomUUID()
            + "\",\"doctorId\":\""
            + UUID.randomUUID()
            + "\",\"eventType\":\"APPOINTMENT_CANCELLED\",\"timestamp\":\"2026-11-01T00:00:00Z\"}";
    var event =
        new ObjectMapper().findAndRegisterModules().readValue(retained, EventService.Event.class);
    assertNull(event.correlationId());
    assertEquals(id.toString(), EventService.correlation(event.correlationId(), event.eventId()));
    assertEquals(id.toString(), EventService.correlation("invalid\ntrace", event.eventId()));
  }
}
