package com.ashraf.notesapi.support;

import com.ashraf.notesapi.grpc.auth.AuthServiceGrpc;
import com.ashraf.notesapi.grpc.auth.AuthProto.ValidateTokenRequest;
import com.ashraf.notesapi.grpc.auth.AuthProto.ValidateTokenResponse;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.Map;

/**
 * In-process stand-in for the Go auth service's gRPC ValidateToken endpoint,
 * so integration tests don't need the real Go binary running.
 */
public class FakeAuthService extends AuthServiceGrpc.AuthServiceImplBase {

    private final Map<String, ValidateTokenResponse> responses = new HashMap<>();
    private boolean simulateOutage = false;

    public void registerValidToken(String token, long userId, String email, String role) {
        responses.put(token, ValidateTokenResponse.newBuilder()
                .setValid(true)
                .setUserId(userId)
                .setEmail(email)
                .setRole(role)
                .build());
    }

    public void registerInvalidToken(String token, String error) {
        responses.put(token, ValidateTokenResponse.newBuilder()
                .setValid(false)
                .setError(error)
                .build());
    }

    public void simulateOutage(boolean outage) {
        this.simulateOutage = outage;
    }

    @Override
    public void validateToken(ValidateTokenRequest request, StreamObserver<ValidateTokenResponse> responseObserver) {
        if (simulateOutage) {
            responseObserver.onError(io.grpc.Status.UNAVAILABLE.withDescription("auth service down").asRuntimeException());
            return;
        }

        ValidateTokenResponse response = responses.getOrDefault(
                request.getToken(),
                ValidateTokenResponse.newBuilder().setValid(false).setError("unknown token").build()
        );
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
