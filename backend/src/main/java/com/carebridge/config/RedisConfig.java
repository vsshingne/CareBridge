package com.carebridge.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.cache.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {
  @Override
  public CacheErrorHandler errorHandler() {
    return new CacheErrorHandler() {
      private void unavailable(Cache cache) {
        org.slf4j.LoggerFactory.getLogger(RedisConfig.class)
            .warn("Public catalog cache unavailable: {}", cache.getName());
      }

      public void handleCacheGetError(RuntimeException e, Cache c, Object key) {
        unavailable(c);
      }

      public void handleCachePutError(RuntimeException e, Cache c, Object key, Object value) {
        unavailable(c);
      }

      public void handleCacheEvictError(RuntimeException e, Cache c, Object key) {
        unavailable(c);
      }

      public void handleCacheClearError(RuntimeException e, Cache c) {
        unavailable(c);
      }
    };
  }

  @Bean
  RedisCacheManager cacheManager(RedisConnectionFactory connection) {
    var defaults =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(2))
            .disableCachingNullValues();
    return RedisCacheManager.builder(connection)
        .cacheDefaults(defaults)
        .withInitialCacheConfigurations(
            Map.of(
                "availability",
                defaults.entryTtl(Duration.ofSeconds(30)),
                "specializations",
                defaults.entryTtl(Duration.ofMinutes(5))))
        .build();
  }
}
