package com.ashraf.notesapi.unit;

import com.ashraf.notesapi.config.CacheConfig;
import com.ashraf.notesapi.grpc.auth.AuthProto.ValidateTokenResponse;
import com.ashraf.notesapi.security.AuthGrpcClient;
import com.ashraf.notesapi.security.AuthValidationService;
import com.ashraf.notesapi.security.JwtExpiryReader;
import com.ashraf.notesapi.support.FakeJwt;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthValidationServiceTest {

    @Mock
    private AuthGrpcClient authGrpcClient;

    private Cache<String, CacheConfig.Entry> cache;
    private AuthValidationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cache = Caffeine.newBuilder().build();
        service = new AuthValidationService(authGrpcClient, cache, new JwtExpiryReader(), new CacheConfig());
    }

    @Test
    void validTokenReturnsValidResultAndCachesIt() {
        String token = FakeJwt.withExpiryInSeconds(900);
        when(authGrpcClient.validateToken(token)).thenReturn(
                ValidateTokenResponse.newBuilder().setValid(true).setUserId(1L).setEmail("a@b.com").setRole("user").build()
        );

        AuthValidationService.Result result = service.validate(token);

        assertThat(result).isInstanceOf(AuthValidationService.Result.Valid.class);
        AuthValidationService.Result.Valid valid = (AuthValidationService.Result.Valid) result;
        assertThat(valid.userId()).isEqualTo(1L);
        assertThat(valid.role()).isEqualTo("user");
        assertThat(cache.estimatedSize()).isEqualTo(1);
    }

    @Test
    void secondCallForSameTokenHitsCacheNotGrpc() {
        String token = FakeJwt.withExpiryInSeconds(900);
        when(authGrpcClient.validateToken(token)).thenReturn(
                ValidateTokenResponse.newBuilder().setValid(true).setUserId(1L).setRole("user").build()
        );

        service.validate(token);
        service.validate(token);

        verify(authGrpcClient, times(1)).validateToken(token);
    }

    @Test
    void invalidTokenIsNeverCached() {
        String token = FakeJwt.withExpiryInSeconds(900);
        when(authGrpcClient.validateToken(token)).thenReturn(
                ValidateTokenResponse.newBuilder().setValid(false).setError("revoked").build()
        );

        AuthValidationService.Result result = service.validate(token);

        assertThat(result).isInstanceOf(AuthValidationService.Result.Invalid.class);
        assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    void grpcFailureWithNoCacheEntryFailsClosed() {
        String token = FakeJwt.withExpiryInSeconds(900);
        when(authGrpcClient.validateToken(token)).thenThrow(
                new StatusRuntimeException(Status.UNAVAILABLE)
        );

        AuthValidationService.Result result = service.validate(token);

        assertThat(result).isInstanceOf(AuthValidationService.Result.ServiceUnavailable.class);
    }

    @Test
    void circuitOpenWithNoCacheEntryFailsClosed() {
        String token = FakeJwt.withExpiryInSeconds(900);
        CircuitBreaker circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
        when(authGrpcClient.validateToken(token)).thenThrow(
                CallNotPermittedException.createCallNotPermittedException(circuitBreaker)
        );

        AuthValidationService.Result result = service.validate(token);

        assertThat(result).isInstanceOf(AuthValidationService.Result.ServiceUnavailable.class);
    }

    @Test
    void alreadyExpiredTokenIsNotCachedEvenIfMarkedValid() {
        String token = FakeJwt.withExpiryInSeconds(-10);
        when(authGrpcClient.validateToken(token)).thenReturn(
                ValidateTokenResponse.newBuilder().setValid(true).setUserId(1L).setRole("user").build()
        );

        service.validate(token);

        assertThat(cache.estimatedSize()).isZero();
    }
}
