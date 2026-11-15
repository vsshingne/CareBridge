package com.carebridge.audit;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuditService {
  private final AuditRepository repository;

  public AuditService(AuditRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(UUID actor, String action, String type, UUID id) {
    AuditLog log = new AuditLog();
    log.actorId = actor;
    log.action = action;
    log.entityType = type;
    log.entityId = id;
    repository.save(log);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void loginFailure() {
    AuditLog log = new AuditLog();
    log.action = "LOGIN_FAILED";
    log.entityType = "AUTH";
    repository.save(log);
  }
}
