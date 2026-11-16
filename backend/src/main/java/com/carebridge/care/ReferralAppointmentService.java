package com.carebridge.care;

import com.carebridge.audit.AuditService;
import com.carebridge.outbox.EventService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reopens scheduling when a linked visit is cancelled, rejected, or moved pending confirmation. */
@Service
public class ReferralAppointmentService {
  private final JdbcTemplate db;
  private final AuditService audit;
  private final EventService events;

  public ReferralAppointmentService(JdbcTemplate db, AuditService audit, EventService events) {
    this.db = db;
    this.audit = audit;
    this.events = events;
  }

  @Transactional
  public void process(EventService.Event event) {
    if (event.schemaVersion() != 1
        || event.eventId() == null
        || event.appointmentId() == null
        || event.patientId() == null
        || event.doctorId() == null
        || event.eventType() == null)
      throw new IllegalArgumentException("Invalid appointment event");
    if (!Set.of("APPOINTMENT_CANCELLED", "APPOINTMENT_REJECTED", "APPOINTMENT_RESCHEDULED")
        .contains(event.eventType())) return;
    if (db.update(
            "insert into processed_events(id,event_id,consumer_name,processed_at)"
                + " values(?,?,?,now()) on conflict(event_id,consumer_name) do nothing",
            UUID.randomUUID(),
            event.eventId(),
            "referral-appointments-v1")
        == 0) return;
    var candidates =
        db.queryForList(
            "select id,plan_id from referrals where appointment_id=? and status='SCHEDULED' order"
                + " by plan_id,id",
            event.appointmentId());
    for (var candidate : candidates) {
      var plans =
          db.queryForList(
              "select * from care_plans where id=? and status='ACTIVE' for update",
              candidate.get("plan_id"));
      if (plans.isEmpty()) continue;
      var plan = plans.getFirst();
      var rows =
          db.queryForList(
              "select r.* from referrals r join appointments a on a.id=r.appointment_id where"
                  + " r.id=? and r.status='SCHEDULED' and a.id=? and a.patient_id=? and"
                  + " a.doctor_id=? and a.status in ('CANCELLED','REJECTED','PENDING') for update"
                  + " of r",
              candidate.get("id"),
              event.appointmentId(),
              event.patientId(),
              event.doctorId());
      if (rows.isEmpty()) continue;
      var row = rows.getFirst();
      UUID id = (UUID) row.get("id");
      db.update(
          "update referrals set"
              + " status='ACCEPTED',appointment_id=null,version=version+1,updated_at=now() where"
              + " id=?",
          id);
      db.update(
          "insert into"
              + " care_history(id,plan_id,referral_id,entity_id,activity_type,action,status,entity_version,snapshot)"
              + " select"
              + " ?,plan_id,id,id,'REFERRAL','REFERRAL_SCHEDULING_REOPENED',status,version,to_jsonb(r)"
              + " from referrals r where id=?",
          UUID.randomUUID(),
          id);
      audit.record(null, "REFERRAL_SCHEDULING_REOPENED", "REFERRAL", id);
      events.domain(
          id,
          "REFERRAL",
          "REFERRAL_SCHEDULING_REOPENED",
          (UUID) plan.get("patient_id"),
          (UUID) plan.get("provider_id"));
    }
  }
}
