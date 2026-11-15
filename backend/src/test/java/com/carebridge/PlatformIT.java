package com.carebridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.carebridge.appointment.*;
import com.carebridge.auth.*;
import com.carebridge.availability.*;
import com.carebridge.doctor.*;
import com.carebridge.patient.*;
import com.carebridge.user.*;
import com.carebridge.user.Role;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
    properties = {
      "app.jobs-enabled=false",
      "spring.kafka.listener.auto-startup=false",
      "spring.kafka.admin.auto-create=false",
      "app.admin-email=",
      "app.admin-password=",
      "app.secure-cookies=false"
    })
@AutoConfigureMockMvc
@Import(PlatformIT.TestCaches.class)
class PlatformIT {
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

  @TestConfiguration
  static class TestCaches {
    @Bean
    @Primary
    CacheManager testCacheManager() {
      return new ConcurrentMapCacheManager("doctorProfiles", "availability", "specializations");
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate db;
  @Autowired UserRepository users;
  @Autowired DoctorRepository doctors;
  @Autowired PatientRepository patients;
  @Autowired AvailabilityRepository windows;
  @Autowired JwtService jwt;
  @Autowired PlatformTransactionManager tx;
  @Autowired com.carebridge.notification.NotificationService notifications;
  @Autowired com.carebridge.outbox.OutboxRepository outbox;
  @Autowired com.carebridge.recovery.RecoveryService recovery;
  @Autowired com.carebridge.gap.CareGapService gaps;
  @Autowired com.carebridge.fhir.FhirExportService fhir;
  @Autowired com.carebridge.notification.CareNotificationService careNotifications;
  UUID doctorId;
  String patientToken, otherPatientToken, doctorToken, otherDoctorToken, adminToken;
  Instant start;

  @BeforeEach
  void setup() {
    db.execute(
        "TRUNCATE notifications, processed_events, outbox_events, audit_logs, refresh_tokens,"
            + " appointments, availability, doctors, patients, users CASCADE");
    new TransactionTemplate(tx)
        .executeWithoutResult(
            s -> {
              UserAccount d = user(Role.DOCTOR);
              doctorToken = jwt.issue(d);
              Doctor doctor = new Doctor();
              doctor.userId = d.id;
              doctor.specialization = "Cardiology";
              doctor.qualification = "MD";
              doctor.consultationFee = BigDecimal.TEN;
              doctor.clinicAddress = "Clinic";
              doctor.timezone = "UTC";
              doctor.verified = true;
              doctors.save(doctor);
              doctorId = doctor.id;
              UserAccount od = user(Role.DOCTOR);
              otherDoctorToken = jwt.issue(od);
              Doctor other = new Doctor();
              other.userId = od.id;
              other.specialization = "General";
              other.qualification = "MD";
              other.consultationFee = BigDecimal.TEN;
              other.clinicAddress = "Clinic";
              other.timezone = "UTC";
              doctors.save(other);
              patientToken = patient();
              otherPatientToken = patient();
              adminToken = jwt.issue(user(Role.ADMIN));
              start =
                  LocalDate.now(ZoneOffset.UTC).plusDays(2).atTime(9, 0).toInstant(ZoneOffset.UTC);
              Availability w = new Availability();
              w.doctorId = doctorId;
              w.dayOfWeek = start.atZone(ZoneOffset.UTC).getDayOfWeek().getValue();
              w.startTime = LocalTime.of(9, 0);
              w.endTime = LocalTime.of(12, 0);
              w.slotDurationMinutes = 30;
              windows.save(w);
            });
  }

  UserAccount user(Role role) {
    UserAccount u = new UserAccount();
    u.fullName = role + " User";
    u.email = UUID.randomUUID() + "@example.test";
    u.passwordHash = "unused-fixture-hash";
    u.role = role;
    return users.save(u);
  }

  String patient() {
    UserAccount u = user(Role.PATIENT);
    Patient p = new Patient();
    p.userId = u.id;
    patients.save(p);
    return jwt.issue(u);
  }

  MvcResult book(String token, Instant time) throws Exception {
    return mvc.perform(
            post("/api/v1/appointments")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    mapper.writeValueAsString(
                        new AppointmentDtos.Book(
                            doctorId, time, time.plusSeconds(1800), "Consultation"))))
        .andReturn();
  }

  UUID booked() throws Exception {
    MvcResult result = book(patientToken, start);
    assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    return UUID.fromString(
        mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  int action(String token, UUID id, String action) throws Exception {
    return mvc.perform(
            patch("/api/v1/appointments/" + id + "/" + action)
                .header("Authorization", "Bearer " + token))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void exactlyOneConcurrentHttpBookingCommits() throws Exception {
    int count = 8;
    ExecutorService pool = Executors.newFixedThreadPool(count);
    CountDownLatch ready = new CountDownLatch(count), go = new CountDownLatch(1);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        String token = new TransactionTemplate(tx).execute(s -> patient());
        results.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return book(token, start).getResponse().getStatus();
                }));
      }
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      go.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (var f : results) statuses.add(f.get(30, TimeUnit.SECONDS));
      assertEquals(1, statuses.stream().filter(s -> s == 201).count(), statuses.toString());
      assertEquals(count - 1, statuses.stream().filter(s -> s == 409).count(), statuses.toString());
      assertEquals(1, db.queryForObject("select count(*) from appointments", Integer.class));
      assertEquals(1, db.queryForObject("select count(*) from outbox_events", Integer.class));
      assertEquals(
          1,
          db.queryForObject(
              "select count(*) from audit_logs where action='APPOINTMENT_BOOKED'", Integer.class));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void databaseRejectsIntervalsWithDifferentStartTimes() throws Exception {
    UUID id = booked();
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            db.update(
                "insert into"
                    + " appointments(id,patient_id,doctor_id,start_time,end_time,status,created_at,updated_at,version)"
                    + " select ?,patient_id,doctor_id,start_time+interval '10"
                    + " minutes',end_time+interval '10 minutes','PENDING',now(),now(),0 from"
                    + " appointments where id=?",
                UUID.randomUUID(),
                id));
  }

  @Test
  void bookingValidationAndRollback() throws Exception {
    assertEquals(
        400, book(patientToken, start.minus(Duration.ofDays(3))).getResponse().getStatus());
    assertEquals(400, book(patientToken, start.minusSeconds(3600)).getResponse().getStatus());
    assertEquals(400, book(patientToken, start.plusSeconds(60)).getResponse().getStatus());
    booked();
    assertEquals(409, book(otherPatientToken, start).getResponse().getStatus());
    assertEquals(1, db.queryForObject("select count(*) from outbox_events", Integer.class));
  }

  @Test
  void ownershipAndTransitions() throws Exception {
    UUID id = booked();
    assertEquals(
        403,
        mvc.perform(
                get("/api/v1/appointments/" + id)
                    .header("Authorization", "Bearer " + otherPatientToken))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(403, action(otherDoctorToken, id, "confirm"));
    assertEquals(403, action(patientToken, id, "confirm"));
    assertEquals(
        200,
        mvc.perform(
                get("/api/v1/appointments/" + id + "/patient")
                    .header("Authorization", "Bearer " + doctorToken))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(
        403,
        mvc.perform(
                get("/api/v1/appointments/" + id + "/patient")
                    .header("Authorization", "Bearer " + otherDoctorToken))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(200, action(doctorToken, id, "confirm"));
    assertEquals(409, action(doctorToken, id, "complete"));
    assertEquals(200, action(patientToken, id, "cancel"));
    assertEquals(409, action(doctorToken, id, "complete"));
    assertEquals(201, book(otherPatientToken, start).getResponse().getStatus());
  }

  @Test
  void rescheduleConflictPreservesOriginalSlot() throws Exception {
    UUID id = booked();
    assertEquals(201, book(otherPatientToken, start.plusSeconds(1800)).getResponse().getStatus());
    var request = new AppointmentDtos.Reschedule(start.plusSeconds(1800), start.plusSeconds(3600));
    assertEquals(
        409,
        mvc.perform(
                patch("/api/v1/appointments/" + id + "/reschedule")
                    .header("Authorization", "Bearer " + patientToken)
                    .contentType("application/json")
                    .content(mapper.writeValueAsString(request)))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(
        start,
        db.queryForObject(
            "select start_time from appointments where id=?",
            (r, n) -> r.getTimestamp(1).toInstant(),
            id));
    var good = new AppointmentDtos.Reschedule(start.plusSeconds(3600), start.plusSeconds(5400));
    assertEquals(
        200,
        mvc.perform(
                patch("/api/v1/appointments/" + id + "/reschedule")
                    .header("Authorization", "Bearer " + patientToken)
                    .contentType("application/json")
                    .content(mapper.writeValueAsString(good)))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void adminAndProfileRoutesRejectWrongRole() throws Exception {
    assertEquals(
        403,
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + patientToken))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(
        403,
        mvc.perform(get("/api/v1/doctors/me").header("Authorization", "Bearer " + patientToken))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(
        401, mvc.perform(get("/api/v1/appointments/me")).andReturn().getResponse().getStatus());
  }

  @Test
  void duplicateEventsCreateOneNotificationPerRecipient() throws Exception {
    booked();
    var row = outbox.findAll().getFirst();
    var event = mapper.readValue(row.payload, com.carebridge.outbox.EventService.Event.class);
    notifications.process(event);
    notifications.process(event);
    assertEquals(1, db.queryForObject("select count(*) from processed_events", Integer.class));
    assertEquals(2, db.queryForObject("select count(*) from notifications", Integer.class));
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from outbox_events where topic='notification-events'", Integer.class));
  }

  @Test
  void invalidConsumerWorkRollsBackDeduplication() {
    var event =
        new com.carebridge.outbox.EventService.Event(
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            doctorId,
            "APPOINTMENT_BOOKED",
            Instant.now());
    assertThrows(com.carebridge.common.ApiException.class, () -> notifications.process(event));
    assertEquals(0, db.queryForObject("select count(*) from processed_events", Integer.class));
  }

  @Test
  void adminVerificationAccountDisableAndAnalytics() throws Exception {
    assertEquals(
        200,
        mvc.perform(get("/api/v1/admin/analytics").header("Authorization", "Bearer " + adminToken))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(
        200,
        mvc.perform(
                patch("/api/v1/admin/doctors/" + doctorId + "/verify")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json")
                    .content("{\"verified\":false}"))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(409, book(patientToken, start).getResponse().getStatus());
    UUID user = doctors.findById(doctorId).orElseThrow().userId;
    assertEquals(
        200,
        mvc.perform(
                patch("/api/v1/admin/users/" + user + "/enabled")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json")
                    .content("{\"enabled\":false}"))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(
        401,
        mvc.perform(get("/api/v1/doctors/me").header("Authorization", "Bearer " + doctorToken))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void scheduleCannotRemoveBookedWindow() throws Exception {
    booked();
    UUID window = windows.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId).getFirst().id;
    assertEquals(
        409,
        mvc.perform(
                delete("/api/v1/doctors/me/availability/" + window)
                    .header("Authorization", "Bearer " + doctorToken))
            .andReturn()
            .getResponse()
            .getStatus());
    assertTrue(windows.existsById(window));
  }

  @Test
  void realRegistrationLoginRotationAndReplay() throws Exception {
    String body =
        mapper.writeValueAsString(
            new AuthDtos.Register(
                "Test Patient", "new@example.test", "long-password-123", null, null, null, null));
    var registration =
        mvc.perform(
                post("/api/v1/auth/register/patient")
                    .header("Origin", "http://localhost:3000")
                    .contentType("application/json")
                    .content(body))
            .andReturn();
    assertEquals(
        201,
        registration.getResponse().getStatus(),
        registration.getResponse().getContentAsString());
    assertEquals(
        409,
        mvc.perform(
                post("/api/v1/auth/register/patient")
                    .header("Origin", "http://localhost:3000")
                    .contentType("application/json")
                    .content(body))
            .andReturn()
            .getResponse()
            .getStatus());
    var cookie = registration.getResponse().getCookie("refresh_token");
    assertNotNull(cookie);
    assertTrue(cookie.isHttpOnly());
    var rotated =
        mvc.perform(
                post("/api/v1/auth/refresh")
                    .header("Origin", "http://localhost:3000")
                    .cookie(cookie))
            .andReturn();
    assertEquals(200, rotated.getResponse().getStatus());
    assertEquals(
        401,
        mvc.perform(
                post("/api/v1/auth/refresh")
                    .header("Origin", "http://localhost:3000")
                    .cookie(cookie))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(
        401,
        mvc.perform(
                post("/api/v1/auth/refresh")
                    .header("Origin", "http://localhost:3000")
                    .cookie(rotated.getResponse().getCookie("refresh_token")))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(
        403,
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
            .andReturn()
            .getResponse()
            .getStatus());
    String login =
        mapper.writeValueAsString(new AuthDtos.Login("new@example.test", "long-password-123"));
    assertEquals(
        200,
        mvc.perform(
                post("/api/v1/auth/login")
                    .header("Origin", "http://localhost:3000")
                    .contentType("application/json")
                    .content(login))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  JsonNode careRequest(String token, String method, String path, Object body, int expected) throws Exception {
    var builder = switch(method) {
      case "POST" -> post("/api/v1/care"+path);
      case "PUT" -> put("/api/v1/care"+path);
      case "PATCH" -> patch("/api/v1/care"+path);
      default -> get("/api/v1/care"+path);
    };
    builder.header("Authorization", "Bearer "+token);
    if(body!=null) builder.contentType("application/json").content(mapper.writeValueAsString(body));
    var response=mvc.perform(builder).andReturn().getResponse();
    assertEquals(expected,response.getStatus(),response.getContentAsString());
    return mapper.readTree(response.getContentAsString());
  }

  String carePlan() throws Exception {
    UUID appointment=booked();
    assertEquals(200,action(doctorToken,appointment,"confirm"));
    return careRequest(doctorToken,"POST","/plans",Map.of("appointmentId",appointment,"title","Synthetic follow-up plan","goals","Complete the provider-defined activities"),201).get("id").asText();
  }

  UUID waitForSlot(String token) throws Exception {
    var response=mvc.perform(post("/api/v1/recovery/waiting-list").header("Authorization","Bearer "+token)
        .contentType("application/json").content(mapper.writeValueAsString(Map.of("doctorId",doctorId,
            "availableFrom",start.minusSeconds(3600),"availableTo",start.plusSeconds(3600),"notifyInApp",true)))).andReturn().getResponse();
    assertEquals(201,response.getStatus(),response.getContentAsString());
    return UUID.fromString(mapper.readTree(response.getContentAsString()).get("id").asText());
  }

  @Test void recoveryDeduplicatesCancellationAndOnlyOnePatientClaimsSlot() throws Exception {
    String third=new TransactionTemplate(tx).execute(s->patient());
    UUID firstEntry=waitForSlot(otherPatientToken),secondEntry=waitForSlot(third);
    UUID cancelled=booked();assertEquals(200,action(patientToken,cancelled,"cancel"));
    var row=outbox.findAll().stream().filter(e->e.eventType.equals("APPOINTMENT_CANCELLED")).findFirst().orElseThrow();
    var event=mapper.readValue(row.payload,com.carebridge.outbox.EventService.Event.class);
    recovery.process(event);recovery.process(event);
    assertEquals(2,db.queryForObject("select count(*) from appointment_offers",Integer.class));
    assertEquals(2,db.queryForObject("select count(*) from care_notifications",Integer.class));
    UUID offer1=db.queryForObject("select id from appointment_offers where entry_id=?",UUID.class,firstEntry);
    UUID offer2=db.queryForObject("select id from appointment_offers where entry_id=?",UUID.class,secondEntry);
    var ids=List.of(offer1,offer2);var tokens=List.of(otherPatientToken,third);
    var keys=List.of(UUID.randomUUID(),UUID.randomUUID());
    ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
    try {
      List<Future<Integer>> futures=new ArrayList<>();
      for(int i=0;i<2;i++){ final int index=i;futures.add(pool.submit(()->{go.await();return acceptOffer(tokens.get(index),ids.get(index),keys.get(index)).getResponse().getStatus();})); }
      go.countDown();var statuses=new ArrayList<Integer>();for(var future:futures)statuses.add(future.get(30,TimeUnit.SECONDS));
      assertEquals(List.of(200,409),statuses.stream().sorted().toList());
      int winner=statuses.indexOf(200);
      assertEquals(200,acceptOffer(tokens.get(winner),ids.get(winner),keys.get(winner)).getResponse().getStatus());
      assertEquals(409,acceptOffer(tokens.get(winner),ids.get(winner),UUID.randomUUID()).getResponse().getStatus());
      assertEquals(1,db.queryForObject("select count(*) from appointments where status='PENDING'",Integer.class));
      assertEquals(1,db.queryForObject("select count(*) from appointment_offers where status='INVALIDATED'",Integer.class));
    }finally{pool.shutdownNow();}
  }
  MvcResult acceptOffer(String token,UUID offer,UUID key) throws Exception {
    return mvc.perform(post("/api/v1/recovery/offers/"+offer+"/accept").header("Authorization","Bearer "+token)
        .contentType("application/json").content(mapper.writeValueAsString(Map.of("idempotencyKey",key)))).andReturn();
  }
  @Test void expiredOffersAndRemovedEntriesCannotBook() throws Exception {
    UUID entry=waitForSlot(otherPatientToken);UUID id=booked();assertEquals(200,action(patientToken,id,"cancel"));
    var row=outbox.findAll().stream().filter(e->e.eventType.equals("APPOINTMENT_CANCELLED")).findFirst().orElseThrow();
    recovery.process(mapper.readValue(row.payload,com.carebridge.outbox.EventService.Event.class));
    UUID offer=db.queryForObject("select id from appointment_offers",UUID.class);
    assertEquals(404,acceptOffer(patientToken,offer,UUID.randomUUID()).getResponse().getStatus());
    db.update("update waiting_entries set active=false where id=?",entry);
    assertEquals(409,acceptOffer(otherPatientToken,offer,UUID.randomUUID()).getResponse().getStatus());
    db.update("update waiting_entries set active=true where id=?",entry);
    db.update("update appointment_offers set expires_at=now()-interval '1 minute'");
    assertEquals(409,acceptOffer(otherPatientToken,offer,UUID.randomUUID()).getResponse().getStatus());
    recovery.expire();assertEquals("EXPIRED",db.queryForObject("select status from appointment_offers",String.class));
  }

  @Test void configuredCareGapsDeduplicateResolveAndRespectOwnership() throws Exception {
    String plan=carePlan();
    var activity=careRequest(doctorToken,"POST","/plans/"+plan+"/activities",Map.of("type","FOLLOW_UP","title","Synthetic review","dueAt",start),201);
    UUID entity=UUID.fromString(activity.get("id").asText());
    db.update("update care_activities set due_at=now()-interval '2 hours' where id=?",entity);
    gaps.evaluate();assertEquals(0,db.queryForObject("select count(*) from care_gap_alerts",Integer.class));
    Map<String,Object> rule=Map.of("name","Overdue follow-up review","activityType","FOLLOW_UP","triggerCondition","OVERDUE","intervalHours",1,"severity","WARNING","notificationPolicy","IN_APP","responsibleRole","DOCTOR");
    careRequest(patientToken,"POST","/gaps/rules",rule,403);
    careRequest(otherDoctorToken,"POST","/gaps/rules",rule,201);
    gaps.evaluate();assertEquals(0,db.queryForObject("select count(*) from care_gap_alerts",Integer.class));
    careRequest(doctorToken,"POST","/gaps/rules",rule,201);
    gaps.evaluate();gaps.evaluate();
    assertEquals(1,db.queryForObject("select count(*) from care_gap_alerts",Integer.class));
    assertEquals(2,db.queryForObject("select count(*) from care_notifications",Integer.class));
    assertEquals(1,careRequest(patientToken,"GET","/gaps",null,200).get("totalElements").asInt());
    assertEquals(0,careRequest(otherPatientToken,"GET","/gaps",null,200).get("totalElements").asInt());
    String path="/plans/"+plan+"/activities/"+entity+"/status";
    careRequest(doctorToken,"PATCH",path,Map.of("version",0,"status","IN_PROGRESS"),200);
    careRequest(doctorToken,"PATCH",path,Map.of("version",1,"status","COMPLETED","outcome","Synthetic review completed"),200);
    gaps.evaluate();
    assertEquals(0,careRequest(patientToken,"GET","/gaps",null,200).get("totalElements").asInt());
    assertEquals(1,careRequest(patientToken,"GET","/gaps?resolved=true",null,200).get("totalElements").asInt());
    assertEquals(1,db.queryForObject("select count(*) from care_gap_alerts",Integer.class));
  }

  @Test void fhirExportValidatesR4AndPreservesPatientIsolation() throws Exception {
    String plan=carePlan();
    var activity=careRequest(doctorToken,"POST","/plans/"+plan+"/activities",Map.of("type","INVESTIGATION","title","Synthetic investigation","dueAt",start),201);
    String path="/plans/"+plan+"/activities/"+activity.get("id").asText()+"/status";
    careRequest(doctorToken,"PATCH",path,Map.of("version",0,"status","IN_PROGRESS"),200);
    careRequest(doctorToken,"PATCH",path,Map.of("version",1,"status","COMPLETED","outcome","Synthetic recorded result"),200);
    UUID receiver=db.queryForObject("select id from doctors where id<>?",UUID.class,doctorId);
    db.update("update doctors set verified=true where id=?",receiver);
    careRequest(doctorToken,"POST","/plans/"+plan+"/referrals",Map.of("receivingProviderId",receiver,"clinicalReason","Synthetic review","priority","ROUTINE","dueAt",start),201);
    db.update("update appointments set status='COMPLETED',start_time=now()-interval '2 hours',end_time=now()-interval '1 hour' where id=(select source_appointment_id from care_plans where id=?)",UUID.fromString(plan));
    String endpoint="/api/v1/fhir/r4/care-plans/"+plan+"/bundle";
    assertEquals(403,mvc.perform(get(endpoint).header("Authorization","Bearer "+otherPatientToken)).andReturn().getResponse().getStatus());
    var response=mvc.perform(get(endpoint).header("Authorization","Bearer "+patientToken)).andReturn().getResponse();
    assertEquals(200,response.getStatus(),response.getContentAsString());
    assertTrue(response.getContentType().startsWith("application/fhir+json"));
    var bundle=mapper.readTree(response.getContentAsString());assertEquals("Bundle",bundle.get("resourceType").asText());
    Set<String> types=new HashSet<>();for(var entry:bundle.get("entry"))types.add(entry.get("resource").get("resourceType").asText());
    assertTrue(types.containsAll(Set.of("Patient","Practitioner","Appointment","Encounter","CarePlan","ServiceRequest","Observation","Task")),types.toString());
    assertThrows(IllegalStateException.class,()->fhir.validateAndEncode("{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[{\"resource\":{\"resourceType\":\"Task\",\"intent\":\"order\"}}]}"));
  }

  @Test void careEventsAreTransactionalIdempotentAndRetryAfterFailure() throws Exception {
    String plan=carePlan();
    var row=outbox.findAll().stream().filter(e->e.eventType.equals("CARE_PLAN_CREATED")).findFirst().orElseThrow();
    var event=mapper.readValue(row.payload,com.carebridge.outbox.EventService.DomainEvent.class);
    careNotifications.process(event);careNotifications.process(event);
    assertEquals(2,db.queryForObject("select count(*) from care_notifications",Integer.class));
    var invalid=new com.carebridge.outbox.EventService.DomainEvent(1,UUID.randomUUID(),UUID.randomUUID(),"CARE_PLAN","CARE_PLAN_CREATED",event.patientId(),event.providerId(),event.correlationId(),Instant.now());
    assertThrows(IllegalArgumentException.class,()->careNotifications.process(invalid));
    assertEquals(0,db.queryForObject("select count(*) from processed_events where event_id=?",Integer.class,invalid.eventId()));
    careNotifications.process(new com.carebridge.outbox.EventService.DomainEvent(1,invalid.eventId(),UUID.fromString(plan),"CARE_PLAN","CARE_PLAN_CREATED",event.patientId(),event.providerId(),event.correlationId(),Instant.now()));
    assertEquals(4,db.queryForObject("select count(*) from care_notifications",Integer.class));
    long before=outbox.count();
    careRequest(doctorToken,"PATCH","/plans/"+plan+"/status",Map.of("version",0,"status","COMPLETED"),409);
    assertEquals(before,outbox.count());
  }

  @Test void twoRetriesAcceptingTheSameOfferReturnOneBooking() throws Exception {
    waitForSlot(otherPatientToken);UUID id=booked();assertEquals(200,action(patientToken,id,"cancel"));
    var row=outbox.findAll().stream().filter(e->e.eventType.equals("APPOINTMENT_CANCELLED")).findFirst().orElseThrow();
    recovery.process(mapper.readValue(row.payload,com.carebridge.outbox.EventService.Event.class));
    UUID offer=db.queryForObject("select id from appointment_offers",UUID.class),key=UUID.randomUUID();
    ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
    try{
      var first=pool.submit(()->{go.await();return acceptOffer(otherPatientToken,offer,key);});
      var second=pool.submit(()->{go.await();return acceptOffer(otherPatientToken,offer,key);});go.countDown();
      var one=first.get(30,TimeUnit.SECONDS).getResponse();var two=second.get(30,TimeUnit.SECONDS).getResponse();
      assertEquals(200,one.getStatus());assertEquals(200,two.getStatus());
      assertEquals(mapper.readTree(one.getContentAsString()).get("id"),mapper.readTree(two.getContentAsString()).get("id"));
      assertEquals(1,db.queryForObject("select count(*) from appointments where status='PENDING'",Integer.class));
    }finally{pool.shutdownNow();}
  }

  @Test void metricsAreAdminOnlyAndAuthorizationDenialIsAudited() throws Exception {
    assertEquals(403,mvc.perform(get("/actuator/metrics").header("Authorization","Bearer "+patientToken)).andReturn().getResponse().getStatus());
    assertEquals(1,db.queryForObject("select count(*) from audit_logs where action='AUTHORIZATION_DENIED'",Integer.class));
    assertEquals(200,mvc.perform(get("/actuator/metrics").header("Authorization","Bearer "+adminToken)).andReturn().getResponse().getStatus());
    assertEquals(0,careRequest(patientToken,"GET","/dashboard",null,200).get("pendingActivities").asInt());
  }

  @Test
  void carePlanIsolationCompletionAndHistory() throws Exception {
    String id=carePlan();
    careRequest(otherPatientToken,"GET","/plans/"+id,null,403);
    careRequest(otherDoctorToken,"GET","/plans/"+id,null,403);
    careRequest(patientToken,"PUT","/plans/"+id,Map.of("version",0,"title","Changed","goals","Changed"),403);
    careRequest(doctorToken,"PATCH","/plans/"+id+"/status",Map.of("version",0,"status","COMPLETED"),409);
    var activity=careRequest(doctorToken,"POST","/plans/"+id+"/activities",Map.of("type","FOLLOW_UP","title","Provider review","dueAt",start),201);
    String path="/plans/"+id+"/activities/"+activity.get("id").asText()+"/status";
    careRequest(doctorToken,"PATCH",path,Map.of("version",0,"status","COMPLETED","outcome","Reviewed"),409);
    careRequest(doctorToken,"PATCH",path,Map.of("version",0,"status","IN_PROGRESS"),200);
    careRequest(doctorToken,"PATCH",path,Map.of("version",0,"status","CANCELLED"),409);
    careRequest(doctorToken,"PATCH",path,Map.of("version",1,"status","COMPLETED"),400);
    careRequest(doctorToken,"PATCH",path,Map.of("version",1,"status","COMPLETED","outcome","Synthetic review completed by provider"),200);
    careRequest(doctorToken,"PATCH","/plans/"+id+"/status",Map.of("version",0,"status","COMPLETED"),200);
    var plan=careRequest(patientToken,"GET","/plans/"+id,null,200);
    assertEquals(1,plan.get("completedActivities").asInt());
    String patient=plan.get("patientId").asText();
    careRequest(otherPatientToken,"GET","/journey/"+patient,null,403);
    var history=careRequest(patientToken,"GET","/journey/"+patient+"?type=FOLLOW_UP&size=2",null,200);
    assertEquals(3,history.get("totalElements").asInt());
    assertEquals(2,history.get("content").size());
    assertEquals(0,careRequest(otherDoctorToken,"GET","/journey/"+patient,null,200).get("totalElements").asInt());
    careRequest(doctorToken,"POST","/plans/"+id+"/activities",Map.of("type","FOLLOW_UP","title","Late edit","dueAt",start),409);
  }

  @Test
  void concurrentCarePlanUpdatesRejectLostWrites() throws Exception {
    String id=carePlan();
    ExecutorService pool=Executors.newFixedThreadPool(2);
    CountDownLatch ready=new CountDownLatch(2), go=new CountDownLatch(1);
    try {
      List<Future<Integer>> futures=new ArrayList<>();
      for(int i=0;i<2;i++) futures.add(pool.submit(()->{
        ready.countDown();go.await();
        return mvc.perform(put("/api/v1/care/plans/"+id).header("Authorization","Bearer "+doctorToken)
            .contentType("application/json").content("{\"version\":0,\"title\":\"Updated\",\"goals\":\"Review\"}"))
            .andReturn().getResponse().getStatus();
      }));
      assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();
      var statuses=new ArrayList<Integer>();for(var future:futures) statuses.add(future.get(30,TimeUnit.SECONDS));
      assertEquals(List.of(200,409),statuses.stream().sorted().toList());
      assertEquals(1,db.queryForObject("select count(*) from care_history where action='CARE_PLAN_UPDATED'",Integer.class));
    } finally {pool.shutdownNow();}
  }

  @Test
  void referralRequiresReceivingProviderAndActualCompletion() throws Exception {
    String plan=carePlan();
    UUID receiver=db.queryForObject("select id from doctors where id<>?",UUID.class,doctorId);
    db.update("update doctors set verified=true where id=?",receiver);
    String id=careRequest(doctorToken,"POST","/plans/"+plan+"/referrals",Map.of("receivingProviderId",receiver,"clinicalReason","Synthetic specialist review","priority","ROUTINE","dueAt",start.plusSeconds(86400)),201).get("id").asText();
    careRequest(otherDoctorToken,"GET","/plans/"+plan,null,403);
    careRequest(otherDoctorToken,"GET","/referrals/"+id,null,200);
    careRequest(otherPatientToken,"GET","/referrals/"+id,null,403);
    String path="/referrals/"+id+"/status";
    careRequest(doctorToken,"PATCH",path,Map.of("version",0,"status","COMPLETED"),409);
    careRequest(doctorToken,"PATCH",path,Map.of("version",0,"status","PENDING_ACCEPTANCE"),200);
    careRequest(doctorToken,"PATCH",path,Map.of("version",1,"status","ACCEPTED"),403);
    careRequest(otherDoctorToken,"PATCH",path,Map.of("version",1,"status","ACCEPTED"),200);
    UUID appointment=UUID.randomUUID();
    db.update("insert into appointments(id,patient_id,doctor_id,start_time,end_time,status,created_at,updated_at) select ?,patient_id,?,now()-interval '2 hours',now()-interval '1 hour','CONFIRMED',now(),now() from care_plans where id=?",appointment,receiver,UUID.fromString(plan));
    careRequest(otherDoctorToken,"PATCH",path,Map.of("version",2,"status","SCHEDULED","appointmentId",appointment),200);
    careRequest(otherDoctorToken,"PATCH",path,Map.of("version",3,"status","IN_PROGRESS"),200);
    careRequest(otherDoctorToken,"PATCH",path,Map.of("version",4,"status","COMPLETED","outcome","Reviewed"),409);
    assertEquals(200,action(otherDoctorToken,appointment,"complete"));
    careRequest(otherDoctorToken,"PATCH",path,Map.of("version",4,"status","COMPLETED","outcome","Synthetic specialist consultation completed"),200);
    careRequest(doctorToken,"PATCH","/plans/"+plan+"/status",Map.of("version",0,"status","COMPLETED"),200);
  }
}
