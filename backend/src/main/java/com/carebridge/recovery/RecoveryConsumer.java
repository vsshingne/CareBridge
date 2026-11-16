package com.carebridge.recovery;

import com.carebridge.outbox.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RecoveryConsumer {
  private final RecoveryService service;
  private final ObjectMapper mapper;
  private final com.carebridge.care.ReferralAppointmentService referrals;

  public RecoveryConsumer(
      RecoveryService service,
      ObjectMapper mapper,
      com.carebridge.care.ReferralAppointmentService referrals) {
    this.service = service;
    this.mapper = mapper;
    this.referrals = referrals;
  }

  @KafkaListener(topics = "appointment-events", groupId = "carebridge-recovery-v1")
  public void consume(String payload) throws com.fasterxml.jackson.core.JsonProcessingException {
    var event = mapper.readValue(payload, EventService.Event.class);
    try (var ignored =
        org.slf4j.MDC.putCloseable(
            "requestId", EventService.correlation(event.correlationId(), event.eventId()))) {
      referrals.process(event);
      service.process(event);
    }
  }
}
