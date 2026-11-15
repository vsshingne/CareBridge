package com.carebridge.notification;

import com.carebridge.outbox.EventService.DomainEvent;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareNotificationService {
  private final JdbcTemplate db;
  public CareNotificationService(JdbcTemplate db){this.db=db;}
  @Transactional public void process(DomainEvent e) {
    if(e.schemaVersion()!=1||e.eventId()==null||e.aggregateId()==null||e.patientId()==null||e.timestamp()==null||e.eventType()==null||e.aggregateType()==null||e.correlationId()==null)
      throw new IllegalArgumentException("Invalid care event");
    Set<String> handled=Set.of("CARE_PLAN","REFERRAL","FOLLOW_UP","INVESTIGATION","CONSULTATION");
    if(!handled.contains(e.aggregateType())) {
      if(Set.of("APPOINTMENT_OFFER","CARE_GAP","PATIENT").contains(e.aggregateType()))return;
      throw new IllegalArgumentException("Unsupported care aggregate");
    }
    if(e.providerId()==null)throw new IllegalArgumentException("Missing provider");
    Set<String> types=Set.of("CARE_PLAN_CREATED","CARE_PLAN_UPDATED","CARE_PLAN_COMPLETED","CARE_PLAN_CANCELLED",
        "REFERRAL_CREATED","REFERRAL_PENDING_ACCEPTANCE","REFERRAL_ACCEPTED","REFERRAL_SCHEDULED","REFERRAL_IN_PROGRESS","REFERRAL_COMPLETED","REFERRAL_CANCELLED","REFERRAL_REASSIGNED",
        "CARE_ACTIVITY_CREATED","CARE_ACTIVITY_UPDATED","CARE_ACTIVITY_SCHEDULED","CARE_ACTIVITY_IN_PROGRESS","CARE_ACTIVITY_COMPLETED","CARE_ACTIVITY_CANCELLED");
    if(!types.contains(e.eventType()))throw new IllegalArgumentException("Unsupported care event type");
    int inserted=db.update("insert into processed_events(id,event_id,consumer_name,processed_at) values(?,?,?,now()) on conflict(event_id,consumer_name) do nothing",UUID.randomUUID(),e.eventId(),"care-notifications-v1");
    if(inserted==0)return;
    String entity=e.aggregateType().equals("CARE_PLAN")?"care_plans":e.aggregateType().equals("REFERRAL")?"referrals":"care_activities";
    String join=entity.equals("care_plans")?"care_plans p":"care_plans p join "+entity+" a on a.plan_id=p.id";
    String idColumn=entity.equals("care_plans")?"p.id":"a.id";
    if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from "+join+" where "+idColumn+"=? and p.patient_id=? and p.provider_id=?)",Boolean.class,e.aggregateId(),e.patientId(),e.providerId())))throw new IllegalArgumentException("Care event references do not match");
    Set<UUID> recipients=new HashSet<>();
    recipients.add(db.queryForObject("select user_id from patients where id=?",UUID.class,e.patientId()));
    recipients.add(db.queryForObject("select user_id from doctors where id=?",UUID.class,e.providerId()));
    if(entity.equals("referrals"))recipients.add(db.queryForObject("select d.user_id from referrals r join doctors d on d.id=r.receiving_provider_id where r.id=?",UUID.class,e.aggregateId()));
    for(UUID user:recipients)db.update("insert into care_notifications(id,user_id,entity_id,event_id,event_type) values(?,?,?,?,?) on conflict(user_id,event_id) do nothing",UUID.randomUUID(),user,e.aggregateId(),e.eventId(),e.eventType());
  }
}
