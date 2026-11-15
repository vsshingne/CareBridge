package com.carebridge;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.carebridge.doctor.*;
import com.carebridge.outbox.*;
import com.carebridge.patient.*;
import com.carebridge.user.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.*;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
    properties = {
      "app.jobs-enabled=false",
      "app.admin-email=",
      "app.admin-password=",
      "spring.kafka.consumer.auto-offset-reset=earliest"
    })
@EmbeddedKafka(
    partitions = 3,
    topics = {"appointment-events", "notification-events", "appointment-events.DLT"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(PlatformIT.TestCaches.class)
@DirtiesContext
class KafkaFlowIT {
  static PostgreSQLContainer<?> postgres;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    String url = System.getenv("PG_TEST_URL");
    if (url == null) {
      postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
      postgres.start();
      r.add("spring.datasource.url", postgres::getJdbcUrl);
      r.add("spring.datasource.username", postgres::getUsername);
      r.add("spring.datasource.password", postgres::getPassword);
    } else {
      r.add("spring.datasource.url", () -> url);
      r.add(
          "spring.datasource.username",
          () -> System.getenv().getOrDefault("PG_TEST_USER", "carebridge"));
      r.add(
          "spring.datasource.password", () -> System.getenv().getOrDefault("PG_TEST_PASSWORD", ""));
    }
    r.add("app.jwt-secret", () -> Base64.getEncoder().encodeToString(new byte[48]));
  }

  @Autowired JdbcTemplate db;
  @Autowired UserRepository users;
  @Autowired PatientRepository patients;
  @Autowired DoctorRepository doctors;
  @Autowired EventService events;
  @Autowired OutboxPublisher publisher;
  @Autowired OutboxRepository outbox;
  @Autowired PlatformTransactionManager tx;
  @Autowired ObjectMapper mapper;
  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired EmbeddedKafkaBroker broker;

  @Test
  void acknowledgedOutboxDeliveryIsIdempotentAndMalformedEventReachesDlt() throws Exception {
    var event =
        new TransactionTemplate(tx)
            .execute(
                status -> {
                  UserAccount p = new UserAccount();
                  p.fullName = "Kafka Patient";
                  p.email = UUID.randomUUID() + "@example.test";
                  p.passwordHash = "fixture";
                  p.role = Role.PATIENT;
                  users.save(p);
                  Patient patient = new Patient();
                  patient.userId = p.id;
                  patients.save(patient);
                  UserAccount d = new UserAccount();
                  d.fullName = "Kafka Doctor";
                  d.email = UUID.randomUUID() + "@example.test";
                  d.passwordHash = "fixture";
                  d.role = Role.DOCTOR;
                  users.save(d);
                  Doctor doctor = new Doctor();
                  doctor.userId = d.id;
                  doctor.specialization = "General";
                  doctor.qualification = "MD";
                  doctor.consultationFee = BigDecimal.ZERO;
                  doctor.clinicAddress = "Clinic";
                  doctor.timezone = "UTC";
                  doctors.save(doctor);
                  var e =
                      new EventService.Event(
                          1,
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          patient.id,
                          doctor.id,
                          "APPOINTMENT_BOOKED",
                          Instant.now());
                  events.enqueue(e, "appointment-events");
                  return e;
                });
    assertNotNull(event);
    publisher.publishBatch();
    assertEquals("PUBLISHED", outbox.findById(event.eventId()).orElseThrow().status);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertEquals(
                    2,
                    db.queryForObject(
                        "select count(*) from notifications where event_id=?",
                        Integer.class,
                        event.eventId())));
    kafka
        .send(
            "appointment-events",
            event.appointmentId().toString(),
            mapper.writeValueAsString(event))
        .get(15, TimeUnit.SECONDS);
    // A following event in the same partition proves the duplicate was consumed before the
    // assertion.
    var next =
        new EventService.Event(
            1,
            UUID.randomUUID(),
            event.appointmentId(),
            event.patientId(),
            event.doctorId(),
            "APPOINTMENT_CONFIRMED",
            Instant.now());
    kafka
        .send(
            "appointment-events", event.appointmentId().toString(), mapper.writeValueAsString(next))
        .get(15, TimeUnit.SECONDS);
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertEquals(
                    2,
                    db.queryForObject(
                        "select count(*) from notifications where event_id=?",
                        Integer.class,
                        next.eventId())));
    assertEquals(
        2,
        db.queryForObject(
            "select count(*) from notifications where event_id=?", Integer.class, event.eventId()));
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from processed_events where event_id=?",
            Integer.class,
            event.eventId()));
    var props = KafkaTestUtils.consumerProps("dlt-test-" + UUID.randomUUID(), "false", broker);
    try (Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
            .createConsumer()) {
      broker.consumeFromAnEmbeddedTopic(consumer, "appointment-events.DLT");
      kafka.send("appointment-events", "malformed", "{not-json").get(15, TimeUnit.SECONDS);
      // Independent notification and recovery groups each dead-letter malformed input.
      var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(30), 2);
      assertEquals(2, records.count());
      for (var record : records) assertEquals("{not-json", record.value());
    }
  }
}
