package com.ashraf.notesapi.security;

import com.ashraf.notesapi.config.CacheConfig;
import com.ashraf.notesapi.grpc.auth.AuthProto.ValidateTokenResponse;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class AuthValidationService {

    private final AuthGrpcClient authGrpcClient;
    private final Cache<String, CacheConfig.Entry> tokenValidationCache;
    private final JwtExpiryReader jwtExpiryReader;

    public AuthValidationService(
            AuthGrpcClient authGrpcClient,
            Cache<String, CacheConfig.Entry> tokenValidationCache,
            JwtExpiryReader jwtExpiryReader
    ) {
        this.authGrpcClient = authGrpcClient;
        this.tokenValidationCache = tokenValidationCache;
        this.jwtExpiryReader = jwtExpiryReader;
    }

    public sealed interface Result permits Result.Valid, Result.Invalid, Result.ServiceUnavailable {
        record Valid(long userId, String email, String role) implements Result {}
        record Invalid(String reason) implements Result {}
        record ServiceUnavailable() implements Result {}
    }

    public Result validate(String token) {
        String cacheKey = sha256(token);

        CacheConfig.Entry cached = tokenValidationCache.getIfPresent(cacheKey);
        if (cached != null) {
            CachedValidation v = cached.value();
            return new Result.Valid(v.userId(), v.email(), v.role());
        }

        try {
            ValidateTokenResponse response = authGrpcClient.validateToken(token);

            if (!response.getValid()) {
                return new Result.Invalid(response.getError());
            }

            long expiry = jwtExpiryReader.expiryEpochSeconds(token);
            long ttlNanos = CacheConfig.ttlNanosFor(expiry);
            if (ttlNanos > 0) {
                CachedValidation cachedValidation = new CachedValidation(response.getUserId(), response.getEmail(), response.getRole());
                tokenValidationCache.put(cacheKey, new CacheConfig.Entry(cachedValidation, ttlNanos));
            }

            return new Result.Valid(response.getUserId(), response.getEmail(), response.getRole());
        } catch (CallNotPermittedException | StatusRuntimeException e) {
            // Circuit open, or the gRPC call itself failed/timed out, with no cache entry: fail closed.
            return new Result.ServiceUnavailable();
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
