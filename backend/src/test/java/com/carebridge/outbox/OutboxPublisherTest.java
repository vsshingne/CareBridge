package com.carebridge.outbox;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxPublisherTest {
  OutboxRepository repository = mock(OutboxRepository.class);

  @SuppressWarnings("unchecked")
  KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

  OutboxPublisher publisher = new OutboxPublisher(repository, kafka);

  OutboxEvent event() {
    OutboxEvent e = new OutboxEvent();
    e.aggregateId = UUID.randomUUID();
    e.topic = "appointment-events";
    e.payload = "{}";
    when(repository.lockDue()).thenReturn(List.of(e));
    return e;
  }

  @Test
  void marksPublishedOnlyAfterAcknowledgement() {
    var e = event();
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    publisher.publishBatch();
    assertEquals("PUBLISHED", e.status);
    assertNotNull(e.publishedAt);
  }

  @Test
  void retriesFailureWithBackoffThenParks() {
    var e = event();
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));
    publisher.publishBatch();
    assertEquals("PENDING", e.status);
    assertEquals(1, e.retryCount);
    assertNull(e.publishedAt);
    assertTrue(e.nextAttemptAt.isAfter(e.createdAt));
    e.retryCount = 11;
    publisher.publishBatch();
    assertEquals("FAILED", e.status);
  }
}
