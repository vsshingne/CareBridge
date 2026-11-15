package com.carebridge.config;

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
      return Health.up().build();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Health.down().build();
    } catch (Exception e) {
      return Health.down().build();
    }
  }
}
