package com.carebridge.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxJob {
  private final OutboxPublisher publisher;

  public OutboxJob(OutboxPublisher publisher) {
    this.publisher = publisher;
  }

  @Scheduled(fixedDelayString = "${app.outbox-delay:1000}")
  public void publish() {
    publisher.publishBatch();
  }
}
