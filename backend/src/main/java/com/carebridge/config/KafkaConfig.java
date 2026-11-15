package com.carebridge.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
  @Bean NewTopic careTopic() {return TopicBuilder.name("care-events").partitions(3).replicas(1).build();}
  @Bean NewTopic careDeadLetterTopic() {return TopicBuilder.name("care-events.DLT").partitions(3).replicas(1).build();}
  @Bean
  NewTopic appointmentTopic() {
    return TopicBuilder.name("appointment-events").partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic notificationTopic() {
    return TopicBuilder.name("notification-events").partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic deadLetterTopic() {
    return TopicBuilder.name("appointment-events.DLT").partitions(3).replicas(1).build();
  }

  @Bean
  DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, error) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    // A failed DLT send is retried; the original offset must not be committed on failed recovery.
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 3));
  }
}
