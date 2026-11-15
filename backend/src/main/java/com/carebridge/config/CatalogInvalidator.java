package com.carebridge.config;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component
public class CatalogInvalidator {
  private final CacheManager caches;

  public CatalogInvalidator(CacheManager caches) {
    this.caches = caches;
  }

  public void invalidateAfterCommit() {
    if (!TransactionSynchronizationManager.isSynchronizationActive())
      throw new IllegalStateException("Cache invalidation requires transaction");
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            for (String name : new String[] {"doctorProfiles", "specializations", "availability"}) {
              var cache = caches.getCache(name);
              if (cache != null)
                try {
                  cache.clear();
                } catch (RuntimeException e) {
                  org.slf4j.LoggerFactory.getLogger(CatalogInvalidator.class)
                      .warn("Cache invalidation unavailable for {}", name);
                }
            }
          }
        });
  }
}
