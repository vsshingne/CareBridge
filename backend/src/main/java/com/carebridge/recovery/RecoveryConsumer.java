package com.carebridge.recovery;
import com.carebridge.outbox.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component public class RecoveryConsumer {
  private final RecoveryService service;private final ObjectMapper mapper;
  public RecoveryConsumer(RecoveryService service,ObjectMapper mapper){this.service=service;this.mapper=mapper;}
  @KafkaListener(topics="appointment-events",groupId="carebridge-recovery-v1")
  public void consume(String payload) throws com.fasterxml.jackson.core.JsonProcessingException {
    service.process(mapper.readValue(payload,EventService.Event.class));
  }
}
