package com.ashraf.notesapi;

// Builds the gRPC channel to the Go auth service, lazily so the app can
// start even if the auth service isn't reachable yet — the first real
// request is what triggers the connection attempt.
import com.ashraf.notesapi.grpc.auth.AuthServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class GrpcConfig {

    @Value("${grpc.auth.host:auth-api}")
    private String authServiceHost;

    @Value("${grpc.auth.port:9090}")
    private int authServicePort;

    @Bean
    @Lazy
    public ManagedChannel authServiceChannel() {
        return ManagedChannelBuilder.forAddress(authServiceHost, authServicePort)
                .usePlaintext()
                .build();
    }

    @Bean
    @Lazy
    public AuthServiceGrpc.AuthServiceBlockingStub authServiceStub(ManagedChannel authServiceChannel) {
        return AuthServiceGrpc.newBlockingStub(authServiceChannel);
    }
}
