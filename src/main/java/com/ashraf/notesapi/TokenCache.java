package com.ashraf.notesapi;

// Everything to do with "how long do we trust a validated token without
// re-checking gRPC": the Caffeine cache itself, and reading a token's exp
// claim to bound that TTL. Reading exp here does NOT verify the JWT
// signature — signature verification is the Go service's job, this only
// needs to know when the token naturally expires.
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Configuration
public class TokenCache {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.cache.max-ttl-seconds:60}")
    private long maxTtlSeconds = 60;

    public record CachedValidation(long userId, String email, String role) {
    }

    /** Pairs a cached result with its own TTL so each entry can expire on its own schedule. */
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

        return Caffeine.newBuilder().maximumSize(10_000).expireAfter(expiry).recordStats().build();
    }

    /** exp claim of a JWT, read locally without checking the signature. Returns 0 if unreadable. */
    public long expiryEpochSeconds(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return 0;
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            return payload.has("exp") ? payload.get("exp").asLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public long ttlNanosFor(long tokenExpiryEpochSeconds) {
        return ttlNanosFor(tokenExpiryEpochSeconds, maxTtlSeconds);
    }

    public static long ttlNanosFor(long tokenExpiryEpochSeconds, long maxTtlSeconds) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long remainingSeconds = tokenExpiryEpochSeconds - nowSeconds;
        long boundedSeconds = Math.max(0, Math.min(maxTtlSeconds, remainingSeconds));
        return TimeUnit.SECONDS.toNanos(boundedSeconds);
    }
}
