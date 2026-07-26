package com.ashraf.notesapi;

// Thin wrapper around the blocking gRPC stub, isolated in its own bean so
// that Spring's proxy-based @CircuitBreaker actually intercepts the call.
// A self-invocation from within AuthValidationService would bypass the
// proxy entirely and the circuit breaker would silently do nothing.
import com.ashraf.notesapi.grpc.auth.AuthServiceGrpc;
import com.ashraf.notesapi.grpc.auth.AuthProto.ValidateTokenRequest;
import com.ashraf.notesapi.grpc.auth.AuthProto.ValidateTokenResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

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
