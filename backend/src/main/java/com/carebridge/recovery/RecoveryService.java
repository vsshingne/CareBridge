package com.carebridge.recovery;

import com.carebridge.appointment.*;
import com.carebridge.audit.AuditService;
import com.carebridge.auth.CurrentUser;
import com.carebridge.common.*;
import com.carebridge.doctor.DoctorRepository;
import com.carebridge.outbox.EventService;
import com.carebridge.user.Role;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecoveryService {
  public record Join(UUID doctorId,@Size(max=120) String specialty,@NotNull @Future Instant availableFrom,
      @NotNull @Future Instant availableTo,boolean notifyInApp) {}
  public record Claim(@NotNull UUID idempotencyKey) {}
  public record Priority(@Min(0) @Max(10) int priority) {}
  public record Entry(UUID id,UUID doctorId,String specialty,Instant availableFrom,Instant availableTo,
      int priority,boolean notifyInApp,boolean active) {}
  public record Offer(UUID id,UUID doctorId,String providerName,Instant startTime,Instant endTime,
      Instant expiresAt,String status,UUID acceptedAppointmentId) {}
  private final JdbcTemplate db; private final CurrentUser current; private final DoctorRepository doctors;
  private final AppointmentService appointments; private final AuditService audit; private final EventService events;
  public RecoveryService(JdbcTemplate db,CurrentUser current,DoctorRepository doctors,AppointmentService appointments,AuditService audit,EventService events) {
    this.db=db;this.current=current;this.doctors=doctors;this.appointments=appointments;this.audit=audit;this.events=events;
  }
  private UUID patient() {return db.queryForObject("select id from patients where user_id=?",UUID.class,current.require(Role.PATIENT).id);}
  public UUID join(Join r) {
    UUID patient=patient(); boolean specialty=r.specialty()!=null&&!r.specialty().isBlank();
    if((r.doctorId()!=null)==specialty) throw ApiException.badRequest("Choose either a provider or specialty");
    if(!r.availableTo().isAfter(r.availableFrom()) || r.availableTo().isAfter(Instant.now().plus(Duration.ofDays(90)))) throw ApiException.badRequest("Choose availability within 90 days");
    boolean eligible=r.doctorId()!=null ? Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from doctors d join users u on u.id=d.user_id where d.id=? and d.verified and u.enabled)",Boolean.class,r.doctorId())) : Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from doctors d join users u on u.id=d.user_id where lower(d.specialization)=lower(?) and d.verified and u.enabled)",Boolean.class,r.specialty().trim()));
    if(!eligible) throw ApiException.badRequest("No eligible provider matches this preference");
    UUID id=UUID.randomUUID();
    db.update("insert into waiting_entries(id,patient_id,doctor_id,specialty,available_from,available_to,notify_in_app) values(?,?,?,?,?,?,?)",id,patient,r.doctorId(),specialty?r.specialty().trim():null,Timestamp.from(r.availableFrom()),Timestamp.from(r.availableTo()),r.notifyInApp());
    audit.record(current.get().id,"WAITING_LIST_JOINED","WAITING_ENTRY",id);return id;
  }
  public List<Entry> entries() {
    return db.query("select * from waiting_entries where patient_id=? order by created_at desc limit 100",(r,n)->new Entry(r.getObject("id",UUID.class),r.getObject("doctor_id",UUID.class),r.getString("specialty"),r.getTimestamp("available_from").toInstant(),r.getTimestamp("available_to").toInstant(),r.getInt("priority"),r.getBoolean("notify_in_app"),r.getBoolean("active")),patient());
  }
  public void remove(UUID id) {
    if(db.update("update waiting_entries set active=false where id=? and patient_id=?",id,patient())==0) throw ApiException.notFound();
    audit.record(current.get().id,"WAITING_LIST_REMOVED","WAITING_ENTRY",id);
  }
  public void prioritize(UUID id,int priority) {
    var actor=current.require(Role.ADMIN);
    if(db.update("update waiting_entries set priority=? where id=? and active",priority,id)==0) throw ApiException.notFound();
    audit.record(actor.id,"WAITING_PRIORITY_UPDATED","WAITING_ENTRY",id);
  }
  public void process(EventService.Event e) {
    if(e.schemaVersion()!=1 || e.eventId()==null || e.appointmentId()==null || e.doctorId()==null || e.patientId()==null || e.eventType()==null) throw new IllegalArgumentException("Invalid recovery event");
    if(!e.eventType().equals("APPOINTMENT_CANCELLED")) return;
    doctors.lockById(e.doctorId()).orElseThrow(ApiException::notFound);
    int inserted=db.update("insert into processed_events(id,event_id,consumer_name,processed_at) values(?,?,?,now()) on conflict(event_id,consumer_name) do nothing",UUID.randomUUID(),e.eventId(),"appointment-recovery-v1");
    if(inserted==0) return;
    var slots=db.queryForList("select a.*,d.specialization from appointments a join doctors d on d.id=a.doctor_id join users u on u.id=d.user_id where a.id=? and a.doctor_id=? and a.patient_id=? and a.status='CANCELLED' and a.start_time>now() and d.verified and u.enabled",e.appointmentId(),e.doctorId(),e.patientId());
    if(slots.isEmpty()) return;
    var slot=slots.getFirst(); var start=(Timestamp)slot.get("start_time"); var end=(Timestamp)slot.get("end_time");
    if(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from appointments where doctor_id=? and status in ('PENDING','CONFIRMED') and start_time<? and end_time>?)",Boolean.class,e.doctorId(),end,start))) return;
    var candidates=db.queryForList("""
        select w.*,p.user_id from waiting_entries w join patients p on p.id=w.patient_id join users u on u.id=p.user_id
        where w.active and u.enabled and w.patient_id<>? and w.available_from<=? and w.available_to>=?
        and (w.doctor_id=? or lower(w.specialty)=lower(?))
        and not exists(select 1 from appointments a where a.patient_id=w.patient_id and a.status in ('PENDING','CONFIRMED') and a.start_time<? and a.end_time>?)
        and not exists(select 1 from appointment_offers o where o.cancelled_appointment_id=? and o.entry_id=w.id)
        order by w.priority desc,w.created_at,w.id limit 5
        """,e.patientId(),start,end,e.doctorId(),slot.get("specialization"),end,start,e.appointmentId());
    Instant expiry=Instant.now().plus(Duration.ofMinutes(15));if(expiry.isAfter(start.toInstant())) expiry=start.toInstant();
    Set<UUID> offered=new HashSet<>();
    for(var candidate:candidates) {
      UUID pid=(UUID)candidate.get("patient_id");if(!offered.add(pid)) continue;
      UUID id=UUID.randomUUID();
      db.update("insert into appointment_offers(id,cancelled_appointment_id,entry_id,patient_id,doctor_id,start_time,end_time,status,expires_at) values(?,?,?,?,?,?,?,'OPEN',?)",id,e.appointmentId(),candidate.get("id"),pid,e.doctorId(),start,end,Timestamp.from(expiry));
      UUID event=events.domain(id,"APPOINTMENT_OFFER","APPOINTMENT_OFFER_CREATED",pid,e.doctorId());
      if((boolean)candidate.get("notify_in_app")) db.update("insert into care_notifications(id,user_id,entity_id,event_id,event_type) values(?,?,?,?,'APPOINTMENT_OFFER_CREATED')",UUID.randomUUID(),candidate.get("user_id"),id,event);
      audit.record(null,"APPOINTMENT_OFFER_CREATED","APPOINTMENT_OFFER",id);
    }
  }
  public List<Offer> offers() {
    return db.query("""
        select o.*,u.full_name,w.active entry_active from appointment_offers o join waiting_entries w on w.id=o.entry_id join doctors d on d.id=o.doctor_id join users u on u.id=d.user_id
        where o.patient_id=? order by o.created_at desc limit 100
        """,(r,n)->new Offer(r.getObject("id",UUID.class),r.getObject("doctor_id",UUID.class),r.getString("full_name"),r.getTimestamp("start_time").toInstant(),r.getTimestamp("end_time").toInstant(),r.getTimestamp("expires_at").toInstant(),r.getString("status").equals("OPEN")?(!r.getBoolean("entry_active")?"INVALIDATED":r.getTimestamp("expires_at").toInstant().isBefore(Instant.now())?"EXPIRED":"OPEN"):r.getString("status"),r.getObject("accepted_appointment_id",UUID.class)),patient());
  }
  public AppointmentDtos.View accept(UUID id,Claim request) {
    UUID pid=patient();
    var initial=db.queryForList("select doctor_id from appointment_offers where id=? and patient_id=?",id,pid);
    if(initial.isEmpty()) throw ApiException.notFound();
    doctors.lockById((UUID)initial.getFirst().get("doctor_id")).orElseThrow(ApiException::notFound);
    var row=db.queryForMap("select * from appointment_offers where id=? for update",id);
    if(row.get("status").equals("ACCEPTED") && request.idempotencyKey().equals(row.get("idempotency_key"))) return appointments.get((UUID)row.get("accepted_appointment_id"));
    if(!row.get("status").equals("OPEN") || !((Timestamp)row.get("expires_at")).toInstant().isAfter(Instant.now())) throw ApiException.conflict("Offer is no longer available");
    // Serialize removal and acceptance on the waiting entry. All booking paths still use the doctor lock.
    var waiting=db.queryForList("select id from waiting_entries where id=? and active for update",row.get("entry_id"));
    if(waiting.isEmpty()) throw ApiException.conflict("Waiting entry is inactive");
    var start=(Timestamp)row.get("start_time");var end=(Timestamp)row.get("end_time");
    if(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from appointments where patient_id=? and status in ('PENDING','CONFIRMED') and start_time<? and end_time>?)",Boolean.class,pid,end,start))) throw ApiException.conflict("You already have an appointment at this time");
    var booking=appointments.book(new AppointmentDtos.Book((UUID)row.get("doctor_id"),start.toInstant(),end.toInstant(),"Waiting-list offer accepted"));
    db.update("update appointment_offers set status='ACCEPTED',accepted_appointment_id=?,idempotency_key=? where id=?",booking.id(),request.idempotencyKey(),id);
    db.update("update appointment_offers set status='INVALIDATED' where doctor_id=? and start_time<? and end_time>? and status='OPEN'",row.get("doctor_id"),end,start);
    db.update("update waiting_entries set active=false where id=?",row.get("entry_id"));
    events.domain(id,"APPOINTMENT_OFFER","APPOINTMENT_OFFER_ACCEPTED",pid,(UUID)row.get("doctor_id"));
    audit.record(current.get().id,"APPOINTMENT_OFFER_ACCEPTED","APPOINTMENT_OFFER",id);return booking;
  }
  public void expire() {
    db.update("update appointment_offers set status='EXPIRED' where status='OPEN' and expires_at<=now()");
    db.update("update waiting_entries set active=false where active and available_to<=now()");
  }
}
