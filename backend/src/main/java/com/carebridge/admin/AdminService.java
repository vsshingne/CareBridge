package com.carebridge.admin;

import com.carebridge.appointment.*;
import com.carebridge.audit.*;
import com.carebridge.auth.*;
import com.carebridge.common.*;
import com.carebridge.config.CatalogInvalidator;
import com.carebridge.doctor.*;
import com.carebridge.outbox.*;
import com.carebridge.user.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
  public record AuditView(
      UUID id, UUID actorId, String action, String entityType, UUID entityId, Instant timestamp) {}

  public record OutboxView(
      UUID id, String eventType, String status, int retryCount, Instant createdAt) {}

  private final UserRepository users;
  private final DoctorRepository doctors;
  private final DoctorService profiles;
  private final CurrentUser current;
  private final AuditService audit;
  private final AuditRepository logs;
  private final RefreshTokenRepository tokens;
  private final CatalogInvalidator cache;
  private final JdbcTemplate db;
  private final OutboxRepository outbox;

  public AdminService(
      UserRepository users,
      DoctorRepository doctors,
      DoctorService profiles,
      CurrentUser current,
      AuditService audit,
      AuditRepository logs,
      RefreshTokenRepository tokens,
      CatalogInvalidator cache,
      JdbcTemplate db,
      OutboxRepository outbox) {
    this.users = users;
    this.doctors = doctors;
    this.profiles = profiles;
    this.current = current;
    this.audit = audit;
    this.logs = logs;
    this.tokens = tokens;
    this.cache = cache;
    this.db = db;
    this.outbox = outbox;
  }

  @Transactional(readOnly = true)
  public Pages.Result<AuthDtos.UserView> users(Role role, Pageable p) {
    current.require(Role.ADMIN);
    p = Pages.checked(p, "createdAt", "fullName", "email", "role");
    return Pages.Result.from(
        (role == null ? users.findAll(p) : users.findByRole(role, p)).map(AuthDtos.UserView::from));
  }

  @Transactional(readOnly = true)
  public Pages.Result<DoctorDtos.View> doctors(Pageable p) {
    current.require(Role.ADMIN);
    return Pages.Result.from(
        doctors
            .findAll(Pages.checked(p, "createdAt", "specialization", "verified"))
            .map(profiles::view));
  }

  @Transactional
  public DoctorDtos.View verify(UUID id, boolean verified) {
    UserAccount actor = current.require(Role.ADMIN);
    Doctor d = doctors.lockById(id).orElseThrow(ApiException::notFound);
    d.verified = verified;
    doctors.save(d);
    audit.record(actor.id, verified ? "DOCTOR_VERIFIED" : "DOCTOR_UNVERIFIED", "DOCTOR", id);
    cache.invalidateAfterCommit();
    return profiles.view(d);
  }

  @Transactional
  public AuthDtos.UserView enable(UUID id, boolean enabled) {
    UserAccount actor = current.require(Role.ADMIN);
    UserAccount user = users.findById(id).orElseThrow(ApiException::notFound);
    if (user.role == Role.ADMIN)
      throw ApiException.conflict("Administrator accounts cannot be disabled through this API");
    if (user.role == Role.DOCTOR) doctors.findByUserId(id).ifPresent(d -> doctors.lockById(d.id));
    user.enabled = enabled;
    user.updatedAt = Instant.now();
    users.save(user);
    if (!enabled) tokens.revokeUser(id);
    audit.record(actor.id, enabled ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED", "USER", id);
    cache.invalidateAfterCommit();
    return AuthDtos.UserView.from(user);
  }

  @Transactional(readOnly = true)
  public Pages.Result<AuditView> logs(Pageable p) {
    current.require(Role.ADMIN);
    return Pages.Result.from(
        logs.findAll(Pages.checked(p, "timestamp", "action"))
            .map(
                a ->
                    new AuditView(
                        a.id, a.actorId, a.action, a.entityType, a.entityId, a.timestamp)));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> analytics() {
    current.require(Role.ADMIN);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("patients", db.queryForObject("select count(*) from patients", Long.class));
    result.put("doctors", doctors.count());
    result.put(
        "unverifiedDoctors",
        db.queryForObject("select count(*) from doctors where not verified", Long.class));
    result.put("appointments", db.queryForObject("select count(*) from appointments", Long.class));
    result.put(
        "appointmentsByStatus",
        db.queryForList(
            "select status, count(*) as count from appointments group by status order by status"));
    result.put(
        "appointmentsByDay",
        db.queryForList(
            "select (start_time at time zone 'UTC')::date as date, count(*) as count from"
                + " appointments where start_time>=now()-interval '30 days' group by 1 order by"
                + " 1"));
    result.put(
        "failedOutboxEvents",
        db.queryForObject("select count(*) from outbox_events where status='FAILED'", Long.class));
    return result;
  }

  @Transactional(readOnly = true)
  public Pages.Result<OutboxView> outbox(Pageable p) {
    current.require(Role.ADMIN);
    return Pages.Result.from(
        outbox
            .findAll(Pages.checked(p, "createdAt", "status"))
            .map(e -> new OutboxView(e.id, e.eventType, e.status, e.retryCount, e.createdAt)));
  }

  @Transactional
  public void retry(UUID id) {
    UserAccount actor = current.require(Role.ADMIN);
    OutboxEvent e = outbox.findById(id).orElseThrow(ApiException::notFound);
    if (!e.status.equals("FAILED"))
      throw ApiException.conflict("Only failed outbox events can be retried");
    e.status = "PENDING";
    e.retryCount = 0;
    e.nextAttemptAt = Instant.now();
    outbox.save(e);
    audit.record(actor.id, "OUTBOX_RETRIED", "OUTBOX", id);
  }
}
