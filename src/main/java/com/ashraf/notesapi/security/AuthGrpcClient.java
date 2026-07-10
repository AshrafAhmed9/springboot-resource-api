package com.ashraf.notesapi.security;

import com.ashraf.notesapi.grpc.auth.AuthServiceGrpc;
import com.ashraf.notesapi.grpc.auth.AuthProto.ValidateTokenRequest;
import com.ashraf.notesapi.grpc.auth.AuthProto.ValidateTokenResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the blocking gRPC stub, isolated in its own bean so that
 * Spring's proxy-based {@code @CircuitBreaker} AOP actually intercepts the call
 * (a self-invocation from within the same class would bypass the proxy).
 */
@Component
public class AuthGrpcClient {

    private final AuthServiceGrpc.AuthServiceBlockingStub authServiceStub;

    public AuthGrpcClient(AuthServiceGrpc.AuthServiceBlockingStub authServiceStub) {
        this.authServiceStub = authServiceStub;
    }

    @CircuitBreaker(name = "authService")
    public ValidateTokenResponse validateToken(String token) {
        return authServiceStub
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .validateToken(ValidateTokenRequest.newBuilder().setToken(token).build());
    }
}
