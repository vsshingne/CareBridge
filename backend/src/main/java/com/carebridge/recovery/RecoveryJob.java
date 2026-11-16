package com.carebridge.recovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class RecoveryJob {
  private final RecoveryService service;

  public RecoveryJob(RecoveryService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${app.recovery-expiry-ms:30000}")
  public void expire() {
    service.expire();
  }
}
