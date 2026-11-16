package com.carebridge.notification;

import com.carebridge.outbox.EventService.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AppointmentConsumer {
  private final NotificationService service;
  private final ObjectMapper mapper;

  public AppointmentConsumer(NotificationService service, ObjectMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @KafkaListener(topics = "appointment-events")
  public void consume(String payload) throws JsonProcessingException {
    var event = mapper.readValue(payload, Event.class);
    try (var ignored =
        org.slf4j.MDC.putCloseable(
            "requestId",
            com.carebridge.outbox.EventService.correlation(
                event.correlationId(), event.eventId()))) {
      service.process(event);
    }
  }
}
