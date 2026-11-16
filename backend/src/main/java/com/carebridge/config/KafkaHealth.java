package com.carebridge.config;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.actuate.health.*;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

@Component("kafka")
public class KafkaHealth implements HealthIndicator {
  private final KafkaAdmin kafka;

  public KafkaHealth(KafkaAdmin kafka) {
    this.kafka = kafka;
  }

  @Override
  public Health health() {
    try (AdminClient client = AdminClient.create(kafka.getConfigurationProperties())) {
      client.describeCluster().clusterId().get(3, TimeUnit.SECONDS);
      var topics =
          client
              .describeTopics(
                  List.of(
                      "appointment-events",
                      "notification-events",
                      "appointment-events.DLT",
                      "care-events",
                      "care-events.DLT"))
              .allTopicNames()
              .get(3, TimeUnit.SECONDS);
      if (topics.values().stream()
          .flatMap(t -> t.partitions().stream())
          .anyMatch(p -> p.leader() == null || p.leader().id() < 0 || p.isr().isEmpty()))
        return Health.down().build();
      return Health.up().build();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Health.down().build();
    } catch (Exception e) {
      return Health.down().build();
    }
  }
}
