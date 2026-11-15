package com.carebridge.gap;

import com.carebridge.audit.AuditService;
import com.carebridge.auth.CurrentUser;
import com.carebridge.common.*;
import com.carebridge.outbox.EventService;
import com.carebridge.user.Role;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class CareGapService {
  public enum Type {FOLLOW_UP,INVESTIGATION,CONSULTATION,REFERRAL}
  public enum Trigger {OVERDUE,UNSCHEDULED}
  public enum Severity {INFO,WARNING,HIGH}
  public enum Policy {IN_APP,NONE}
  public enum Responsible {DOCTOR,ADMIN}
  public record RuleInput(@NotBlank @Size(max=160) String name,@NotNull Type activityType,
      @NotNull Trigger triggerCondition,@Min(0) @Max(8760) int intervalHours,
      @NotNull Severity severity,@NotNull Policy notificationPolicy,@NotNull Responsible responsibleRole) {}
  public record Rule(UUID id,String name,String activityType,String triggerCondition,int intervalHours,
      String severity,String notificationPolicy,String responsibleRole,boolean enabled,long version) {}
  public record Enable(@Min(0) long version,boolean enabled) {}
  public record Alert(UUID id,UUID planId,UUID entityId,String patientName,String ruleName,String activityType,
      String severity,Instant createdAt,Instant resolvedAt) {}
  private final JdbcTemplate db;private final CurrentUser current;private final AuditService audit;private final EventService events;
  public CareGapService(JdbcTemplate db,CurrentUser current,AuditService audit,EventService events){this.db=db;this.current=current;this.audit=audit;this.events=events;}
  private UUID provider() {
    var u=current.get();if(u.role==Role.ADMIN)return null;if(u.role!=Role.DOCTOR)throw ApiException.forbidden();
    return db.queryForObject("select id from doctors where user_id=?",UUID.class,u.id);
  }
  public UUID create(RuleInput r) {
    UUID owner=provider();
    if(r.triggerCondition()==Trigger.UNSCHEDULED && r.activityType()!=Type.REFERRAL) throw ApiException.badRequest("Unscheduled rules apply to referrals");
    if(owner!=null && r.responsibleRole()==Responsible.ADMIN) throw ApiException.forbidden();
    UUID id=UUID.randomUUID();
    db.update("insert into care_gap_rules(id,owner_provider_id,name,activity_type,trigger_condition,interval_hours,severity,notification_policy,responsible_role,created_by) values(?,?,?,?,?,?,?,?,?,?)",id,owner,r.name(),r.activityType().name(),r.triggerCondition().name(),r.intervalHours(),r.severity().name(),r.notificationPolicy().name(),r.responsibleRole().name(),current.get().id);
    audit.record(current.get().id,"CARE_GAP_RULE_CREATED","CARE_GAP_RULE",id);return id;
  }
  public List<Rule> rules() {
    UUID owner=provider();
    return db.query("select * from care_gap_rules"+(owner==null?"":" where owner_provider_id=?")+" order by created_at desc limit 100",
        (r,n)->new Rule(r.getObject("id",UUID.class),r.getString("name"),r.getString("activity_type"),r.getString("trigger_condition"),r.getInt("interval_hours"),r.getString("severity"),r.getString("notification_policy"),r.getString("responsible_role"),r.getBoolean("enabled"),r.getLong("version")),owner==null?new Object[]{}:new Object[]{owner});
  }
  public void enable(UUID id,Enable request) {
    UUID owner=provider();var rows=db.queryForList("select * from care_gap_rules where id=? for update",id);
    if(rows.isEmpty())throw ApiException.notFound();var row=rows.getFirst();
    if(owner!=null&&!owner.equals(row.get("owner_provider_id")))throw ApiException.forbidden();
    if(((Number)row.get("version")).longValue()!=request.version())throw ApiException.conflict("Rule changed. Refresh before saving.");
    db.update("update care_gap_rules set enabled=?,version=version+1 where id=?",request.enabled(),id);
    db.update("update care_gap_alerts set resolved_at=now() where rule_id=? and resolved_at is null",id);
    audit.record(current.get().id,"CARE_GAP_RULE_UPDATED","CARE_GAP_RULE",id);
  }
  public void runByAdmin(){current.require(Role.ADMIN);evaluate();}
  public void evaluate() {
    // One bounded scan per cluster; a crashed transaction releases this lock and rolls back all effects.
    if(!Boolean.TRUE.equals(db.queryForObject("select pg_try_advisory_xact_lock(7529031401)",Boolean.class)))return;
    db.update("""
        update care_gap_alerts g set resolved_at=now() where g.resolved_at is null and (
        exists(select 1 from care_plans p where p.id=g.plan_id and p.status<>'ACTIVE') or
        exists(select 1 from care_activities a where a.id=g.entity_id and a.status in ('COMPLETED','CANCELLED')) or
        exists(select 1 from referrals r where r.id=g.entity_id and r.status in ('COMPLETED','CANCELLED')) or
        exists(select 1 from care_gap_rules r where r.id=g.rule_id and (not r.enabled or r.version<>g.rule_version)))
        """);
    for(var rule:db.queryForList("select * from care_gap_rules where enabled order by created_at,id")) {
      boolean referral=rule.get("activity_type").equals("REFERRAL");
      boolean unscheduled=rule.get("trigger_condition").equals("UNSCHEDULED");
      String table=referral?"referrals":"care_activities";
      String condition=unscheduled?"a.status='ACCEPTED' and a.updated_at+make_interval(hours=>?)<=now()":"a.status not in ('COMPLETED','CANCELLED') and a.due_at+make_interval(hours=>?)<=now()";
      var args=new ArrayList<Object>();args.add(rule.get("interval_hours"));
      if(!referral){condition+=" and a.type=?";args.add(rule.get("activity_type"));}
      if(rule.get("owner_provider_id")!=null){condition+=" and p.provider_id=?";args.add(rule.get("owner_provider_id"));}
      args.add(rule.get("id"));args.add(rule.get("version"));
      var candidates=db.queryForList("select a.id,p.id plan_id,p.patient_id,p.provider_id,pt.user_id patient_user,d.user_id provider_user from "+table+" a join care_plans p on p.id=a.plan_id join patients pt on pt.id=p.patient_id join doctors d on d.id=p.provider_id where p.status='ACTIVE' and "+condition+" and not exists(select 1 from care_gap_alerts g where g.rule_id=? and g.rule_version=? and g.entity_id=a.id) order by a.due_at,a.id limit 100 for update of a skip locked",args.toArray());
      for(var item:candidates) {
        UUID id=UUID.randomUUID();
        int inserted=db.update("insert into care_gap_alerts(id,rule_id,rule_version,plan_id,entity_id,activity_type,severity) values(?,?,?,?,?,?,?) on conflict(rule_id,rule_version,entity_id) do nothing",id,rule.get("id"),rule.get("version"),item.get("plan_id"),item.get("id"),rule.get("activity_type"),rule.get("severity"));
        if(inserted==0)continue;
        UUID event=events.domain(id,"CARE_GAP","CARE_GAP_DETECTED",(UUID)item.get("patient_id"),(UUID)item.get("provider_id"));
        audit.record(null,"CARE_GAP_DETECTED","CARE_GAP",id);
        if(rule.get("notification_policy").equals("IN_APP")) {
          Set<UUID> users=new HashSet<>();users.add((UUID)item.get("patient_user"));
          if(rule.get("responsible_role").equals("ADMIN"))users.addAll(db.queryForList("select id from users where role='ADMIN' and enabled",UUID.class));
          else users.add((UUID)item.get("provider_user"));
          for(UUID user:users)db.update("insert into care_notifications(id,user_id,entity_id,event_id,event_type) values(?,?,?,?,'CARE_GAP_DETECTED') on conflict(user_id,event_id) do nothing",UUID.randomUUID(),user,id,event);
        }
      }
    }
  }
  public Pages.Result<Alert> alerts(boolean resolved,int page,int size) {
    if(page<0||page>1000000||size<1||size>100)throw ApiException.badRequest("Invalid page");
    var actor=current.get();var args=new ArrayList<Object>();
    String query=" from care_gap_alerts g join care_gap_rules r on r.id=g.rule_id join care_plans p on p.id=g.plan_id join patients pt on pt.id=p.patient_id join users u on u.id=pt.user_id join doctors d on d.id=p.provider_id where g.resolved_at is "+(resolved?"not null":"null");
    if(actor.role!=Role.ADMIN){query+=actor.role==Role.PATIENT?" and pt.user_id=?":" and d.user_id=?";args.add(actor.id);}
    long count=db.queryForObject("select count(*)"+query,Long.class,args.toArray());args.add(size);args.add((long)page*size);
    var rows=db.query("select g.*,r.name,u.full_name"+query+" order by g.created_at desc,g.id desc limit ? offset ?",(rs,n)->new Alert(rs.getObject("id",UUID.class),rs.getObject("plan_id",UUID.class),rs.getObject("entity_id",UUID.class),rs.getString("full_name"),rs.getString("name"),rs.getString("activity_type"),rs.getString("severity"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("resolved_at")==null?null:rs.getTimestamp("resolved_at").toInstant()),args.toArray());
    return new Pages.Result<>(rows,count,(int)((count+size-1)/size),page,size);
  }
}
