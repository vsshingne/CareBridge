package com.carebridge.outbox;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {
  private final OutboxRepository repository;
  private final KafkaTemplate<String, String> kafka;

  public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafka) {
    this.repository = repository;
    this.kafka = kafka;
  }

  @Transactional
  public void publishBatch() {
    for (OutboxEvent event : repository.lockDue()) {
      try {
        kafka
            .send(event.topic, event.aggregateId.toString(), event.payload)
            .get(12, TimeUnit.SECONDS);
        event.status = "PUBLISHED";
        event.publishedAt = Instant.now();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Outbox publisher interrupted", e);
      } catch (Exception e) {
        event.retryCount++;
        event.nextAttemptAt =
            Instant.now().plusSeconds(Math.min(3600, 1L << Math.min(event.retryCount, 12)));
        if (event.retryCount >= 12) event.status = "FAILED";
        LoggerFactory.getLogger(OutboxPublisher.class)
            .warn(
                "Outbox send failed eventId={} attempt={} state={}",
                event.id,
                event.retryCount,
                event.status);
      }
      repository.save(event);
    }
  }
}
