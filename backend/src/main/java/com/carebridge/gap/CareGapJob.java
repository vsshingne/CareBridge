package com.carebridge.gap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class CareGapJob {
  private final CareGapService service;

  public CareGapJob(CareGapService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${app.care-gap-scan-ms:60000}")
  public void evaluate() {
    service.evaluate();
  }
}
