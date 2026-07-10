package com.ashraf.notesapi.config;

import com.ashraf.notesapi.security.CachedValidation;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * Wraps a validated token result with its own TTL so Caffeine can expire
     * each entry independently: min(60s, token's remaining lifetime).
     */
    public record Entry(CachedValidation value, long ttlNanos) {
    }

    @Bean
    public Cache<String, Entry> tokenValidationCache() {
        Expiry<String, Entry> expiry = new Expiry<>() {
            @Override
            public long expireAfterCreate(String key, Entry entry, long currentTime) {
                return entry.ttlNanos();
            }

            @Override
            public long expireAfterUpdate(String key, Entry entry, long currentTime, long currentDuration) {
                return entry.ttlNanos();
            }

            @Override
            public long expireAfterRead(String key, Entry entry, long currentTime, long currentDuration) {
                return currentDuration;
            }
        };

        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfter(expiry)
                .recordStats()
                .build();
    }

    public static long ttlNanosFor(long tokenExpiryEpochSeconds) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long remainingSeconds = tokenExpiryEpochSeconds - nowSeconds;
        long boundedSeconds = Math.max(0, Math.min(60, remainingSeconds));
        return TimeUnit.SECONDS.toNanos(boundedSeconds);
    }
}
