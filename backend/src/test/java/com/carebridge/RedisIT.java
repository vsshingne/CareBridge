package com.carebridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.carebridge.availability.AvailabilityService;
import com.carebridge.config.CatalogInvalidator;
import com.carebridge.config.RedisConfig;
import com.carebridge.doctor.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.transaction.support.*;
import org.testcontainers.containers.GenericContainer;

class RedisIT {
  @Test
  void realRedisCachesPublicDtosAndEvictsAfterCommit() {
    GenericContainer<?> redis = null;
    String host = System.getenv("REDIS_TEST_HOST");
    int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379"));
    if (host == null) {
      redis = new GenericContainer<>("redis:7.4.5-alpine").withExposedPorts(6379);
      redis.start();
      host = redis.getHost();
      port = redis.getMappedPort(6379);
    }
    final String redisHost = host;
    final int redisPort = port;
    try {
      try (var context = new AnnotationConfigApplicationContext()) {
        DoctorService service = mock(DoctorService.class);
        DoctorRepository repository = mock(DoctorRepository.class);
        AvailabilityService availability = mock(AvailabilityService.class);
        var id = UUID.randomUUID();
        var view =
            new DoctorDtos.View(
                id, "Doctor", "General", "MD", 3, BigDecimal.TEN, "Clinic", "Bio", "UTC", true);
        when(service.publicProfile(id)).thenReturn(view);
        context.registerBean(
            LettuceConnectionFactory.class,
            () -> new LettuceConnectionFactory(redisHost, redisPort));
        context.registerBean(DoctorService.class, () -> service);
        context.registerBean(DoctorRepository.class, () -> repository);
        context.registerBean(AvailabilityService.class, () -> availability);
        context.register(RedisConfig.class, PublicCatalog.class, CatalogInvalidator.class);
        context.refresh();
        var catalog = context.getBean(PublicCatalog.class);
        var caches = context.getBean(CacheManager.class);
        assertNull(caches.getCache("doctorProfiles").get(id));
        assertEquals(view, catalog.profile(id));
        assertEquals(view, catalog.profile(id));
        verify(service, times(1)).publicProfile(id);
        assertNotNull(caches.getCache("doctorProfiles").get(id));
        TransactionSynchronizationManager.initSynchronization();
        try {
          context.getBean(CatalogInvalidator.class).invalidateAfterCommit();
          assertNotNull(caches.getCache("doctorProfiles").get(id));
          TransactionSynchronizationManager.getSynchronizations()
              .forEach(TransactionSynchronization::afterCommit);
        } finally {
          TransactionSynchronizationManager.clearSynchronization();
        }
        assertNull(caches.getCache("doctorProfiles").get(id));
        catalog.profile(id);
        verify(service, times(2)).publicProfile(id);
      }
    } finally {
      if (redis != null) redis.stop();
    }
  }
}
