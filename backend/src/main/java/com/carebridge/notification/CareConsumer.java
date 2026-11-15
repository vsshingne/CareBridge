package com.carebridge.notification;
import com.carebridge.outbox.EventService.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component public class CareConsumer {
  private final CareNotificationService service;private final ObjectMapper mapper;
  public CareConsumer(CareNotificationService service,ObjectMapper mapper){this.service=service;this.mapper=mapper;}
  @KafkaListener(topics="care-events",groupId="carebridge-care-notifications-v1")
  public void consume(String payload) throws com.fasterxml.jackson.core.JsonProcessingException {service.process(mapper.readValue(payload,DomainEvent.class));}
}
