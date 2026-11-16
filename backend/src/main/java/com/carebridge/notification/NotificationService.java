package com.carebridge.notification;

import com.carebridge.auth.CurrentUser;
import com.carebridge.common.*;
import com.carebridge.doctor.DoctorRepository;
import com.carebridge.outbox.EventService;
import com.carebridge.outbox.EventService.Event;
import com.carebridge.patient.PatientRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  public record View(
      UUID id, UUID appointmentId, UUID entityId, String eventType, Instant createdAt) {}

  private final JdbcTemplate jdbc;
  private final NotificationRepository notifications;
  private final PatientRepository patients;
  private final DoctorRepository doctors;
  private final EventService events;
  private final CurrentUser current;

  public NotificationService(
      JdbcTemplate jdbc,
      NotificationRepository notifications,
      PatientRepository patients,
      DoctorRepository doctors,
      EventService events,
      CurrentUser current) {
    this.jdbc = jdbc;
    this.notifications = notifications;
    this.patients = patients;
    this.doctors = doctors;
    this.events = events;
    this.current = current;
  }

  @Transactional
  public void process(Event e) {
    if (e.schemaVersion() != 1
        || e.eventId() == null
        || e.appointmentId() == null
        || e.patientId() == null
        || e.doctorId() == null
        || e.timestamp() == null
        || !Set.of(
                "APPOINTMENT_BOOKED",
                "APPOINTMENT_CONFIRMED",
                "APPOINTMENT_CANCELLED",
                "APPOINTMENT_RESCHEDULED",
                "APPOINTMENT_REJECTED",
                "APPOINTMENT_COMPLETED")
            .contains(e.eventType()))
      throw new IllegalArgumentException("Unsupported appointment event");
    int inserted =
        jdbc.update(
            "insert into processed_events(id,event_id,consumer_name,processed_at)"
                + " values(?,?,?,now()) on conflict(event_id,consumer_name) do nothing",
            UUID.randomUUID(),
            e.eventId(),
            "notification-inbox-v1");
    if (inserted == 0) return;
    UUID patientUser = patients.findById(e.patientId()).orElseThrow(ApiException::notFound).userId;
    UUID doctorUser = doctors.findById(e.doctorId()).orElseThrow(ApiException::notFound).userId;
    for (UUID user : List.of(patientUser, doctorUser)) {
      Notification n = new Notification();
      n.userId = user;
      n.eventId = e.eventId();
      n.appointmentId = e.appointmentId();
      n.eventType = e.eventType();
      notifications.save(n);
    }
    events.enqueue(
        new Event(
            1,
            UUID.randomUUID(),
            e.appointmentId(),
            e.patientId(),
            e.doctorId(),
            "NOTIFICATION_PERSISTED",
            Instant.now()),
        "notification-events");
  }

  @Transactional(readOnly = true)
  public Pages.Result<View> mine(Pageable p) {
    p = Pages.checked(p, "createdAt");
    UUID user = current.get().id;
    String query =
        "select id,appointment_id,appointment_id entity_id,event_type,created_at from notifications"
            + " where user_id=? union all select id,null::uuid,entity_id,event_type,created_at from"
            + " care_notifications where user_id=?";
    long count =
        jdbc.queryForObject("select count(*) from (" + query + ") n", Long.class, user, user);
    var rows =
        jdbc.query(
            "select * from (" + query + ") n order by created_at desc,id desc limit ? offset ?",
            (r, n) ->
                new View(
                    r.getObject("id", UUID.class),
                    r.getObject("appointment_id", UUID.class),
                    r.getObject("entity_id", UUID.class),
                    r.getString("event_type"),
                    r.getTimestamp("created_at").toInstant()),
            user,
            user,
            p.getPageSize(),
            p.getOffset());
    return new Pages.Result<>(
        rows,
        count,
        (int) ((count + p.getPageSize() - 1) / p.getPageSize()),
        p.getPageNumber(),
        p.getPageSize());
  }
}
