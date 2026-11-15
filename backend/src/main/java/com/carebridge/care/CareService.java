package com.carebridge.care;

import static com.carebridge.care.CareDtos.*;
import static com.carebridge.care.CareStates.*;
import com.carebridge.auth.CurrentUser;
import com.carebridge.audit.AuditService;
import com.carebridge.common.*;
import com.carebridge.user.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CareService {
  private final JdbcTemplate db;
  private final CurrentUser current;
  private final AuditService audit;
  private final com.carebridge.outbox.EventService events;
  public CareService(JdbcTemplate db, CurrentUser current, AuditService audit,com.carebridge.outbox.EventService events) {
    this.db = db; this.current = current; this.audit = audit;this.events=events;
  }
  private static final String PLAN_SELECT = """
      select p.*, pu.full_name patient_name, du.full_name provider_name,
      (select count(*) from care_activities a where a.plan_id=p.id) total_activities,
      (select count(*) from care_activities a where a.plan_id=p.id and a.status='COMPLETED') completed_activities,
      (select count(*) from referrals r where r.plan_id=p.id) total_referrals,
      (select count(*) from referrals r where r.plan_id=p.id and r.status='COMPLETED') completed_referrals
      from care_plans p join patients pt on pt.id=p.patient_id join users pu on pu.id=pt.user_id
      join doctors d on d.id=p.provider_id join users du on du.id=d.user_id
      """;
  private static final String REFERRAL_SELECT = """
      select r.*, p.patient_id, p.provider_id, pu.full_name patient_name,
      du.full_name referring_name, ru.full_name receiving_name
      from referrals r join care_plans p on p.id=r.plan_id
      join patients pt on pt.id=p.patient_id join users pu on pu.id=pt.user_id
      join doctors d on d.id=p.provider_id join users du on du.id=d.user_id
      join doctors rd on rd.id=r.receiving_provider_id join users ru on ru.id=rd.user_id
      """;
  private static UUID uuid(ResultSet r, String field) throws SQLException { return r.getObject(field, UUID.class); }
  private static Instant instant(ResultSet r, String field) throws SQLException { return r.getTimestamp(field).toInstant(); }
  private static final RowMapper<Plan> PLAN = (r,n) -> new Plan(uuid(r,"id"),uuid(r,"patient_id"),uuid(r,"provider_id"),
      r.getString("patient_name"),r.getString("provider_name"),r.getString("title"),r.getString("goals"),
      PlanStatus.valueOf(r.getString("status")),r.getLong("version"),instant(r,"created_at"),
      r.getLong("total_activities"),r.getLong("completed_activities"),r.getLong("total_referrals"),r.getLong("completed_referrals"));
  private static final RowMapper<Activity> ACTIVITY = (r,n) -> new Activity(uuid(r,"id"),uuid(r,"plan_id"),
      ActivityType.valueOf(r.getString("type")),r.getString("title"),instant(r,"due_at"),
      ActivityStatus.valueOf(r.getString("status")),r.getString("outcome"),r.getLong("version"),
      instant(r,"due_at").isBefore(Instant.now()) && !Set.of("COMPLETED","CANCELLED").contains(r.getString("status")));
  private static final RowMapper<Referral> REFERRAL = (r,n) -> new Referral(uuid(r,"id"),uuid(r,"plan_id"),uuid(r,"patient_id"),
      uuid(r,"provider_id"),uuid(r,"receiving_provider_id"),r.getString("patient_name"),r.getString("referring_name"),
      r.getString("receiving_name"),r.getString("clinical_reason"),Priority.valueOf(r.getString("priority")),
      instant(r,"due_at"),uuid(r,"appointment_id"),ReferralStatus.valueOf(r.getString("status")),r.getString("outcome"),
      r.getLong("version"),instant(r,"created_at"));
  private <T> T one(List<T> rows) { if(rows.isEmpty()) throw ApiException.notFound(); return rows.getFirst(); }
  private UUID doctor(UserAccount u) {
    if(u.role != Role.DOCTOR) throw ApiException.forbidden();
    return one(db.query("select id from doctors where user_id=?", (r,n)->uuid(r,"id"),u.id));
  }
  private void providerAvailable(UUID id) {
    if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from doctors d join users u on u.id=d.user_id where d.id=? and d.verified and u.enabled)", Boolean.class,id)))
      throw ApiException.badRequest("Choose an enabled, verified receiving provider");
  }
  private Plan plan(UUID id, boolean lock) {
    // Read aggregate counts in a fresh statement after a contended lock is acquired.
    // PostgreSQL READ COMMITTED otherwise permits subqueries to use the pre-wait snapshot.
    if(lock)one(db.query("select id from care_plans where id=? for update",(r,n)->uuid(r,"id"),id));
    return one(db.query(PLAN_SELECT+" where p.id=?",PLAN,id));
  }
  private Referral referral(UUID id, boolean lock) { return one(db.query(REFERRAL_SELECT+" where r.id=?"+(lock?" for update of r":""),REFERRAL,id)); }
  private void readPlan(Plan p, UserAccount u) {
    if(u.role==Role.ADMIN) return;
    boolean allowed = u.role==Role.DOCTOR ? p.providerId().equals(doctor(u)) : Boolean.TRUE.equals(db.queryForObject(
        "select exists(select 1 from patients where id=? and user_id=?)",Boolean.class,p.patientId(),u.id));
    if(!allowed) throw ApiException.forbidden();
  }
  private void writePlan(Plan p, UserAccount u) {
    if(!p.providerId().equals(doctor(u))) throw ApiException.forbidden();
    if(p.status()!=PlanStatus.ACTIVE) throw ApiException.conflict("The care plan is closed");
  }
  private void readReferral(Referral r, UserAccount u) {
    if(u.role==Role.DOCTOR && r.receivingProviderId().equals(doctor(u))) return;
    readPlan(plan(r.planId(),false),u);
  }
  private void version(long actual,long expected) { if(actual!=expected) throw ApiException.conflict("Record changed. Refresh before saving."); }
  private void history(UUID planId, UUID referralId, UUID entityId, String type, String action, String status, long version) {
    UUID actor=current.get().id;
    db.update("insert into care_history(id,plan_id,referral_id,entity_id,activity_type,action,status,actor_id,entity_version) values(?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),planId,referralId,entityId,type,action,status,actor,version);
    String table=type.equals("CARE_PLAN")?"care_plans":type.equals("REFERRAL")?"referrals":"care_activities";
    db.update("update care_history set snapshot=(select to_jsonb(t) from "+table+" t where t.id=?) where entity_id=? and entity_version=?",entityId,entityId,version);
    if(Set.of("COMPLETED","CANCELLED").contains(status))db.update("update care_gap_alerts set resolved_at=now() where entity_id=? and resolved_at is null",entityId);
    if(type.equals("REFERRAL") && Set.of("SCHEDULED","IN_PROGRESS").contains(status))
      db.update("update care_gap_alerts g set resolved_at=now() from care_gap_rules r where r.id=g.rule_id and r.trigger_condition='UNSCHEDULED' and g.entity_id=? and g.resolved_at is null",entityId);
    audit.record(actor,action,type,entityId);
    Plan p=plan(planId,false);
    events.domain(entityId,type,action,p.patientId(),p.providerId());
  }
  public Plan create(CreatePlan request) {
    UserAccount actor=current.require(Role.DOCTOR); UUID provider=doctor(actor);
    UUID patient=one(db.query("select patient_id from appointments where id=? and doctor_id=? and status in ('CONFIRMED','COMPLETED')",
        (r,n)->uuid(r,"patient_id"),request.appointmentId(),provider));
    providerAvailable(provider);
    UUID id=UUID.randomUUID();
    db.update("insert into care_plans(id,patient_id,provider_id,source_appointment_id,title,goals,status) values(?,?,?,?,?,?,'ACTIVE')",
        id,patient,provider,request.appointmentId(),request.title(),request.goals());
    history(id,null,id,"CARE_PLAN","CARE_PLAN_CREATED","ACTIVE",0);
    return plan(id,false);
  }
  public Plan get(UUID id) {
    Plan p=plan(id,false); readPlan(p,current.get()); audit.record(current.get().id,"CARE_PLAN_READ","CARE_PLAN",id); return p;
  }
  public Plan edit(UUID id, EditPlan r) {
    Plan p=plan(id,true); writePlan(p,current.get()); version(p.version(),r.version());
    db.update("update care_plans set title=?,goals=?,version=version+1,updated_at=now() where id=?",r.title(),r.goals(),id);
    history(id,null,id,"CARE_PLAN","CARE_PLAN_UPDATED",p.status().name(),p.version()+1); return plan(id,false);
  }
  public Plan finish(UUID id, FinishPlan r) {
    Plan p=plan(id,true); writePlan(p,current.get()); version(p.version(),r.version());
    if(r.status()==PlanStatus.ACTIVE) throw ApiException.conflict("Plan is already active");
    long open=db.queryForObject("select (select count(*) from care_activities where plan_id=? and status not in ('COMPLETED','CANCELLED')) + (select count(*) from referrals where plan_id=? and status not in ('COMPLETED','CANCELLED'))",Long.class,id,id);
    if(open>0) throw ApiException.conflict("Complete or cancel every activity and referral before closing the plan");
    if(r.status()==PlanStatus.COMPLETED && p.completedActivities()+p.completedReferrals()==0)
      throw ApiException.conflict("A completed plan must contain completed care");
    db.update("update care_plans set status=?,version=version+1,updated_at=now() where id=?",r.status().name(),id);
    history(id,null,id,"CARE_PLAN","CARE_PLAN_"+r.status(),r.status().name(),p.version()+1); return plan(id,false);
  }
  private String planScope(UserAccount u,List<Object> args) {
    if(u.role==Role.ADMIN) return "true";
    args.add(u.id); return u.role==Role.PATIENT ? "pt.user_id=?" : "d.user_id=?";
  }
  private <T> Pages.Result<T> page(String query, List<Object> args,RowMapper<T> mapper,int page,int size) {
    if(page<0 || size<1 || size>100 || page>1000000) throw ApiException.badRequest("Invalid page");
    long count=db.queryForObject("select count(*) from ("+query+") counted",Long.class,args.toArray());
    var values=new ArrayList<>(args); values.add(size); values.add((long)page*size);
    return new Pages.Result<>(db.query(query+" order by created_at desc,id desc limit ? offset ?",mapper,values.toArray()),count,(int)((count+size-1)/size),page,size);
  }
  public Pages.Result<Plan> plans(int page,int size) {
    var args=new ArrayList<Object>(); String scope=planScope(current.get(),args);
    return page(PLAN_SELECT+" where "+scope,args,PLAN,page,size);
  }
  public Map<String,Long> dashboard() {
    UserAccount actor=current.get();var args=new ArrayList<Object>();String scope=planScope(actor,args);
    String base=" from care_plans p join patients pt on pt.id=p.patient_id join doctors d on d.id=p.provider_id where "+scope;
    Map<String,Long> result=new LinkedHashMap<>();
    result.put("completedPlans",db.queryForObject("select count(*)"+base+" and p.status='COMPLETED'",Long.class,args.toArray()));
    for(var filter:Map.of("pendingActivities","a.status not in ('COMPLETED','CANCELLED')","overdueActivities","a.status not in ('COMPLETED','CANCELLED') and a.due_at<now()","upcomingFollowUps","a.type='FOLLOW_UP' and a.status not in ('COMPLETED','CANCELLED') and a.due_at>=now() and a.due_at<now()+interval '7 days'").entrySet())
      result.put(filter.getKey(),db.queryForObject("select count(*) from care_activities a where "+filter.getValue()+" and a.plan_id in (select p.id"+base+" and p.status='ACTIVE')",Long.class,args.toArray()));
    for(var filter:Map.of("pendingReferrals","r.status not in ('COMPLETED','CANCELLED')","unscheduledReferrals","r.status='ACCEPTED'").entrySet())
      result.put(filter.getKey(),db.queryForObject("select count(*) from referrals r where "+filter.getValue()+" and r.plan_id in (select p.id"+base+" and p.status='ACTIVE')",Long.class,args.toArray()));
    return result;
  }
  public Pages.Result<Activity> activities(UUID id,int page,int size) {
    readPlan(plan(id,false),current.get());
    return page("select * from care_activities where plan_id=?",new ArrayList<>(List.of(id)),ACTIVITY,page,size);
  }
  public Activity addActivity(UUID id,AddActivity r) {
    Plan p=plan(id,true); writePlan(p,current.get()); UUID activity=UUID.randomUUID();
    db.update("insert into care_activities(id,plan_id,type,title,due_at,status) values(?,?,?,?,?,'NOT_STARTED')",
        activity,id,r.type().name(),r.title(),Timestamp.from(r.dueAt()));
    history(id,null,activity,r.type().name(),"CARE_ACTIVITY_CREATED","NOT_STARTED",0);
    return one(db.query("select * from care_activities where id=?",ACTIVITY,activity));
  }
  private Activity activity(UUID planId,UUID id) {
    return one(db.query("select * from care_activities where id=? and plan_id=? for update",ACTIVITY,id,planId));
  }
  public Activity editActivity(UUID planId,UUID id,EditActivity r) {
    writePlan(plan(planId,true),current.get()); Activity a=activity(planId,id); version(a.version(),r.version());
    if(Set.of(ActivityStatus.COMPLETED,ActivityStatus.CANCELLED).contains(a.status())) throw ApiException.conflict("Activity is closed");
    db.update("update care_activities set title=?,due_at=?,version=version+1,updated_at=now() where id=?",r.title(),Timestamp.from(r.dueAt()),id);
    db.update("update care_gap_alerts set resolved_at=now() where entity_id=? and resolved_at is null",id);
    history(planId,null,id,a.type().name(),"CARE_ACTIVITY_UPDATED",a.status().name(),a.version()+1);
    return activity(planId,id);
  }
  public Activity changeActivity(UUID planId,UUID id,ActivityChange r) {
    writePlan(plan(planId,true),current.get()); Activity a=activity(planId,id); version(a.version(),r.version()); a.status().requireTransition(r.status());
    if(r.status()==ActivityStatus.COMPLETED && (r.outcome()==null || r.outcome().isBlank())) throw ApiException.badRequest("Record an outcome before completing care");
    db.update("update care_activities set status=?,outcome=?,version=version+1,updated_at=now() where id=?",r.status().name(),r.outcome(),id);
    history(planId,null,id,a.type().name(),"CARE_ACTIVITY_"+r.status(),r.status().name(),a.version()+1); return activity(planId,id);
  }
  public Referral addReferral(UUID planId,CreateReferral r) {
    Plan p=plan(planId,true); writePlan(p,current.get()); providerAvailable(r.receivingProviderId());
    if(p.providerId().equals(r.receivingProviderId())) throw ApiException.badRequest("Choose another provider for the referral");
    UUID id=UUID.randomUUID();
    db.update("insert into referrals(id,plan_id,receiving_provider_id,clinical_reason,priority,due_at,status) values(?,?,?,?,?,?,'CREATED')",
        id,planId,r.receivingProviderId(),r.clinicalReason(),r.priority().name(),Timestamp.from(r.dueAt()));
    history(planId,id,id,"REFERRAL","REFERRAL_CREATED","CREATED",0); return referral(id,false);
  }
  public Referral getReferral(UUID id) {
    Referral r=referral(id,false); readReferral(r,current.get()); audit.record(current.get().id,"REFERRAL_READ","REFERRAL",id); return r;
  }
  public Pages.Result<Referral> referrals(int page,int size) {
    UserAccount u=current.get(); var args=new ArrayList<Object>(); String scope=planScope(u,args);
    if(u.role==Role.DOCTOR) { scope="("+scope+" or rd.user_id=?)"; args.add(u.id); }
    return page(REFERRAL_SELECT+" where "+scope,args,REFERRAL,page,size);
  }
  public Referral changeReferral(UUID id,ReferralChange r) {
    Referral initial=referral(id,false); Plan p=plan(initial.planId(),true); Referral item=referral(id,true);
    UUID actor=doctor(current.get()); boolean referring=actor.equals(p.providerId()), receiving=actor.equals(item.receivingProviderId());
    if(!referring && !receiving) throw ApiException.forbidden();
    if(p.status()!=PlanStatus.ACTIVE) throw ApiException.conflict("Plan is closed");
    version(item.version(),r.version()); item.status().requireTransition(r.status());
    if(r.status()==ReferralStatus.PENDING_ACCEPTANCE && !referring) throw ApiException.forbidden();
    if(r.status()!=ReferralStatus.PENDING_ACCEPTANCE && r.status()!=ReferralStatus.CANCELLED && !receiving) throw ApiException.forbidden();
    UUID appointment=item.appointmentId();
    if(r.status()==ReferralStatus.SCHEDULED) {
      if(r.appointmentId()==null) throw ApiException.badRequest("An appointment is required");
      appointment=r.appointmentId();
    } else if(r.appointmentId()!=null && !r.appointmentId().equals(appointment)) throw ApiException.badRequest("Appointment can only be linked when scheduling");
    if(Set.of(ReferralStatus.SCHEDULED,ReferralStatus.IN_PROGRESS,ReferralStatus.COMPLETED).contains(r.status())) {
      var matches=db.queryForList("select status,start_time,end_time from appointments where id=? and patient_id=? and doctor_id=? for share",appointment,p.patientId(),item.receivingProviderId());
      if(matches.isEmpty()) throw ApiException.badRequest("Appointment must match the referral patient and provider");
      String status=(String)matches.getFirst().get("status");
      if(r.status()==ReferralStatus.COMPLETED && !status.equals("COMPLETED")) throw ApiException.conflict("Complete the appointment before completing the referral");
      if(r.status()!=ReferralStatus.COMPLETED && !Set.of("CONFIRMED","COMPLETED").contains(status)) throw ApiException.conflict("Referral requires a confirmed appointment");
      if(r.status()==ReferralStatus.IN_PROGRESS && ((Timestamp)matches.getFirst().get("start_time")).toInstant().isAfter(Instant.now())) throw ApiException.conflict("The appointment has not started");
    }
    if(r.status()==ReferralStatus.COMPLETED && (r.outcome()==null || r.outcome().isBlank())) throw ApiException.badRequest("Record a referral outcome");
    db.update("update referrals set status=?,appointment_id=?,outcome=?,version=version+1,updated_at=now() where id=?",r.status().name(),appointment,r.outcome(),id);
    history(p.id(),id,id,"REFERRAL","REFERRAL_"+r.status(),r.status().name(),item.version()+1); return referral(id,false);
  }
  public Referral reassign(UUID id,Reassign r) {
    Referral initial=referral(id,false); Plan p=plan(initial.planId(),true); writePlan(p,current.get()); Referral item=referral(id,true);
    version(item.version(),r.version());
    if(Set.of(ReferralStatus.IN_PROGRESS,ReferralStatus.COMPLETED,ReferralStatus.CANCELLED).contains(item.status())) throw ApiException.conflict("Referral can no longer be reassigned");
    if(item.appointmentId()!=null) {
      String status=db.queryForObject("select status from appointments where id=? for share",String.class,item.appointmentId());
      if(!Set.of("CANCELLED","REJECTED").contains(status)) throw ApiException.conflict("Cancel the linked appointment before reassignment");
    }
    providerAvailable(r.receivingProviderId());
    if(p.providerId().equals(r.receivingProviderId()) || item.receivingProviderId().equals(r.receivingProviderId())) throw ApiException.badRequest("Choose a different receiving provider");
    db.update("update referrals set receiving_provider_id=?,appointment_id=null,status='PENDING_ACCEPTANCE',version=version+1,updated_at=now() where id=?",r.receivingProviderId(),id);
    history(p.id(),id,id,"REFERRAL","REFERRAL_REASSIGNED","PENDING_ACCEPTANCE",item.version()+1); return referral(id,false);
  }
  public Pages.Result<Journey> journey(UUID patientId,String type,String status,Instant from,Instant to,int page,int size) {
    UserAccount u=current.get(); if(from!=null && to!=null && !from.isBefore(to)) throw ApiException.badRequest("Invalid date range");
    if(u.role==Role.PATIENT && !Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from patients where id=? and user_id=?)",Boolean.class,patientId,u.id))) throw ApiException.forbidden();
    UUID doctorId=u.role==Role.DOCTOR?doctor(u):null;
    var args=new ArrayList<Object>(); args.add(patientId);
    String careScope="";
    if(doctorId!=null) {careScope=" and (p.provider_id=? or (h.referral_id is not null and r.receiving_provider_id=?))";args.add(doctorId);args.add(doctorId);}
    String query="select h.id,h.entity_id,h.activity_type,h.action,h.status,h.occurred_at created_at,h.entity_version version from care_history h join care_plans p on p.id=h.plan_id left join referrals r on r.id=h.referral_id where p.patient_id=?"+careScope;
    args.add(patientId);
    query+=" union all select e.id,a.id,'APPOINTMENT',e.event_type,case when e.event_type in ('APPOINTMENT_BOOKED','APPOINTMENT_RESCHEDULED') then 'PENDING' else replace(e.event_type,'APPOINTMENT_','') end,e.created_at,null::bigint from appointments a join outbox_events e on e.aggregate_id=a.id and e.topic='appointment-events' where a.patient_id=?";
    if(doctorId!=null){query+=" and a.doctor_id=?";args.add(doctorId);}
    query+=" union all select n.id,n.appointment_id,'NOTIFICATION',n.event_type,'DELIVERED',n.created_at,null::bigint from notifications n join appointments a on a.id=n.appointment_id where a.patient_id=? and n.user_id=?";
    args.add(patientId);args.add(u.id);
    query+=" union all select n.id,n.entity_id,'NOTIFICATION',n.event_type,'DELIVERED',n.created_at,null::bigint from care_notifications n join outbox_events e on e.id=n.event_id where e.payload::jsonb->>'patientId'=? and n.user_id=?";
    args.add(patientId.toString());args.add(u.id);
    query="select * from ("+query+") journey where true";
    if(type!=null){query+=" and activity_type=?";args.add(type);}
    if(status!=null){query+=" and status=?";args.add(status);}
    if(from!=null){query+=" and created_at>=?";args.add(Timestamp.from(from));}
    if(to!=null){query+=" and created_at<?";args.add(Timestamp.from(to));}
    audit.record(u.id,"CARE_JOURNEY_READ","PATIENT",patientId);
    return page(query,args,(rs,n)->new Journey(uuid(rs,"id"),uuid(rs,"entity_id"),rs.getString("activity_type"),rs.getString("action"),rs.getString("status"),instant(rs,"created_at"),rs.getLong("version")),page,size);
  }
}
